package com.housekeeping.management.service;

import com.housekeeping.management.repository.PaymentPersistenceRepository.PaymentState;
import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Online payment through the VNPay sandbox. A payment is confirmed only from VNPay itself: the signed IPN call,
 * or a server-to-server QueryDR lookup made when the customer returns, so it works on machines without a
 * public URL.
 */
@Service
public class VnpayPaymentService {
    private static final Logger log = LoggerFactory.getLogger(VnpayPaymentService.class);

    private final PaymentService payments;
    private final VnpayGatewayClient gateway;

    public VnpayPaymentService(PaymentService payments, VnpayGatewayClient gateway) {
        this.payments = payments;
        this.gateway = gateway;
    }

    public Checkout checkout(Long bookingId, Long customerId, String clientIp) {
        if (!gateway.isConfigured()) {
            throw new IllegalStateException("Hãy cấu hình VNPAY_TMN_CODE và VNPAY_HASH_SECRET trong file .env "
                    + "rồi khởi động lại ứng dụng trước khi thanh toán qua VNPay.");
        }
        PaymentService.OnlineCheckout attempt = payments.prepareOnlineCheckout(bookingId, customerId);
        if (attempt.recoverExisting()) {
            // The customer may have paid the earlier attempt without returning; never charge twice.
            VnpayGatewayClient.QueryResult result = gateway.query(attempt.orderCode(), attempt.createdAt());
            if (result.paid()) {
                payments.confirmOnlinePayment(attempt.orderCode(), result.amount());
                return new Checkout(null, true);
            }
            if (result.unfinished() && gateway.checkoutOpen(attempt.createdAt())) {
                return new Checkout(gateway.paymentUrl(attempt.orderCode(), attempt.amount(), attempt.createdAt(), clientIp), false);
            }
            if (!result.knownUnpaid() && !result.unfinished()) {
                throw new IllegalStateException("Hãy thử lại sau ít phút: VNPay chưa trả lời được trạng thái lần thanh toán trước (mã "
                        + result.responseCode() + "), nên hệ thống chưa tạo giao dịch mới để tránh thu tiền hai lần.");
            }
            payments.failOnlinePayment(attempt.orderCode(), BigDecimal.valueOf(attempt.amount()));
            attempt = payments.prepareOnlineCheckout(bookingId, customerId);
        }
        return new Checkout(gateway.paymentUrl(attempt.orderCode(), attempt.amount(), attempt.createdAt(), clientIp), false);
    }

    /** Handles the browser return; the returned data is only used to find the order, never to mark it paid. */
    public ReturnOutcome handleReturn(Map<String, String> params) {
        if (!gateway.hasValidSignature(params)) {
            throw new IllegalArgumentException("VNPay return data has an invalid signature.");
        }
        long orderCode = orderCode(params);
        PaymentState attempt = payments.findOnlinePayment(orderCode)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy giao dịch thanh toán trực tuyến."));
        if ("SUCCESS".equals(attempt.status())) return new ReturnOutcome(attempt.bookingId(), ReturnStatus.PAID);

        VnpayGatewayClient.QueryResult result = gateway.query(orderCode, attempt.createdAt());
        if (result.paid()) {
            payments.confirmOnlinePayment(orderCode, result.amount());
            return new ReturnOutcome(attempt.bookingId(), ReturnStatus.PAID);
        }
        if (!"00".equals(params.get("vnp_ResponseCode"))) {
            payments.failOnlinePayment(orderCode, attempt.amount());
            return new ReturnOutcome(attempt.bookingId(), ReturnStatus.FAILED);
        }
        log.warn("VNPay has not confirmed order {} yet (query response {}, transaction status {}).",
                orderCode, result.responseCode(), result.transactionStatus());
        return new ReturnOutcome(attempt.bookingId(), ReturnStatus.PENDING);
    }

    /** VNPay's server-to-server notification; the response codes follow VNPay's IPN contract. */
    public IpnResponse handleIpn(Map<String, String> params) {
        if (!gateway.hasValidSignature(params)) return new IpnResponse("97", "Invalid Checksum");
        try {
            long orderCode = orderCode(params);
            Optional<PaymentState> found = payments.findOnlinePayment(orderCode);
            if (found.isEmpty()) return new IpnResponse("01", "Order not found");
            PaymentState attempt = found.get();
            if (attempt.amount().compareTo(vnpayAmount(params.get("vnp_Amount"))) != 0) {
                return new IpnResponse("04", "Invalid amount");
            }
            if ("SUCCESS".equals(attempt.status())) return new IpnResponse("02", "Order already confirmed");
            if ("00".equals(params.get("vnp_ResponseCode")) && "00".equals(params.get("vnp_TransactionStatus"))) {
                payments.confirmOnlinePayment(orderCode, attempt.amount());
            } else {
                payments.failOnlinePayment(orderCode, attempt.amount());
            }
            return new IpnResponse("00", "Confirm Success");
        } catch (RuntimeException failure) {
            log.warn("Could not record VNPay IPN for order {}.", params.get("vnp_TxnRef"), failure);
            return new IpnResponse("99", "Unknown error");
        }
    }

    private static long orderCode(Map<String, String> params) {
        try {
            return Long.parseLong(params.get("vnp_TxnRef"));
        } catch (NumberFormatException invalid) {
            throw new IllegalArgumentException("VNPay data contains an invalid order code.", invalid);
        }
    }

    private static BigDecimal vnpayAmount(String value) {
        try {
            return new BigDecimal(value).movePointLeft(2);
        } catch (NullPointerException | NumberFormatException invalid) {
            throw new IllegalArgumentException("VNPay data contains an invalid amount.", invalid);
        }
    }

    /** Either a VNPay checkout URL, or {@code alreadyPaid} when an earlier attempt turned out to be paid. */
    public record Checkout(String redirectUrl, boolean alreadyPaid) { }

    public enum ReturnStatus { PAID, FAILED, PENDING }

    public record ReturnOutcome(Long bookingId, ReturnStatus status) { }

    public record IpnResponse(String rspCode, String message) { }
}
