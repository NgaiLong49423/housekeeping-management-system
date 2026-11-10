package com.housekeeping.management.service.impl;

import com.housekeeping.management.service.VnpayGatewayClient;
import com.housekeeping.management.service.PaymentService;
import com.housekeeping.management.service.OnlinePaymentService;

import com.housekeeping.management.service.PaymentService.OnlinePayment;
import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;

/**
 * Online payment through the VNPay sandbox. A payment is confirmed from VNPay itself: the signed IPN call, or a
 * server-to-server QueryDR lookup made when the customer returns, so it works on machines without a public URL.
 * When QueryDR is unavailable, the return data VNPay signed with the merchant secret is accepted instead.
 */
@Service
public class VnpayPaymentServiceImpl implements OnlinePaymentService {
    private static final Logger log = LoggerFactory.getLogger(VnpayPaymentServiceImpl.class);
    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm");

    private final PaymentService payments;
    private final VnpayGatewayClient gateway;

    public VnpayPaymentServiceImpl(PaymentService payments, VnpayGatewayClient gateway) {
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
            VnpayGatewayClient.QueryResult result = query(attempt.orderCode(), attempt.createdAt());
            if (result.paid()) {
                payments.confirmOnlinePayment(attempt.orderCode(), result.amount());
                return new Checkout(null, true);
            }
            // VNPay refuses to reopen a started order, so an unconfirmed one is only replaced once it has expired.
            if (!result.knownUnpaid() && gateway.checkoutOpen(attempt.createdAt())) {
                throw new IllegalStateException("Hãy hoàn tất lần thanh toán VNPay đang mở hoặc thử lại sau "
                        + CLOCK.format(attempt.createdAt().plusMinutes(VnpayGatewayClient.CHECKOUT_MINUTES))
                        + ": VNPay chưa xác nhận lần thanh toán trước, nên hệ thống chưa tạo giao dịch mới để tránh thu tiền hai lần.");
            }
            payments.failOnlinePayment(attempt.orderCode(), BigDecimal.valueOf(attempt.amount()));
            attempt = payments.prepareOnlineCheckout(bookingId, customerId);
        }
        return new Checkout(gateway.paymentUrl(attempt.orderCode(), attempt.amount(), attempt.createdAt(), clientIp), false);
    }

    /** Handles the browser return: QueryDR decides when it answers, otherwise the signed return data does. */
    public ReturnOutcome handleReturn(Map<String, String> params) {
        if (!gateway.hasValidSignature(params)) {
            throw new IllegalArgumentException("VNPay return data has an invalid signature.");
        }
        long orderCode = orderCode(params);
        OnlinePayment attempt = payments.findOnlinePayment(orderCode)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy giao dịch thanh toán trực tuyến."));
        if ("SUCCESS".equals(attempt.status())) return new ReturnOutcome(attempt.bookingId(), ReturnStatus.PAID);

        VnpayGatewayClient.QueryResult result = query(orderCode, attempt.createdAt());
        if (result.paid()) {
            payments.confirmOnlinePayment(orderCode, result.amount());
            return new ReturnOutcome(attempt.bookingId(), ReturnStatus.PAID);
        }
        boolean signedSuccess = "00".equals(params.get("vnp_ResponseCode"))
                && "00".equals(params.get("vnp_TransactionStatus"));
        if (!result.answered() && signedSuccess) {
            // QueryDR is unavailable, so rely on the return data VNPay signed with the merchant's secret;
            // the stored amount is checked again inside confirmOnlinePayment.
            log.warn("VNPay QueryDR unavailable for order {} (code {}); confirming from the signed return data.",
                    orderCode, result.responseCode());
            payments.confirmOnlinePayment(orderCode, vnpayAmount(params.get("vnp_Amount")));
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
            Optional<OnlinePayment> found = payments.findOnlinePayment(orderCode);
            if (found.isEmpty()) return new IpnResponse("01", "Order not found");
            OnlinePayment attempt = found.get();
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

    private VnpayGatewayClient.QueryResult query(long orderCode, java.time.LocalDateTime createdAt) {
        try {
            return gateway.query(orderCode, createdAt);
        } catch (RestClientException unreachable) {
            log.warn("VNPay QueryDR for order {} could not be reached.", orderCode, unreachable);
            return new VnpayGatewayClient.QueryResult("UNREACHABLE", "", null);
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
}
