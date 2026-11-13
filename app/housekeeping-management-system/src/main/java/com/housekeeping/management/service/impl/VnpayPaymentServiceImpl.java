package com.housekeeping.management.service.impl;

import com.housekeeping.management.enums.ReturnStatus;
import com.housekeeping.management.integration.vnpay.VnpayCodes;
import com.housekeeping.management.integration.vnpay.VnpayGatewayClient;
import com.housekeeping.management.integration.vnpay.VnpayGatewayClient.QueryResult;
import com.housekeeping.management.service.OnlinePaymentService;
import com.housekeeping.management.service.PaymentService;
import com.housekeeping.management.service.PaymentService.OnlineCheckout;
import com.housekeeping.management.service.PaymentService.OnlinePayment;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;

/**
 * Thanh toán online qua VNPay sandbox.
 * <p>
 * Kết quả thanh toán được xác nhận từ chính VNPay: qua lời gọi IPN có chữ ký, hoặc qua QueryDR khi khách quay về
 * trang của mình (cách này chạy được cả trên máy không có địa chỉ public). Nếu QueryDR không trả lời được thì mới
 * dùng dữ liệu trả về có chữ ký của VNPay.
 */
@Service
public class VnpayPaymentServiceImpl implements OnlinePaymentService {
    private static final Logger log = LoggerFactory.getLogger(VnpayPaymentServiceImpl.class);
    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm");
    /** Trạng thái của dòng Payment đã thanh toán xong. */
    private static final String PAID_STATUS = "SUCCESS";

    private final PaymentService payments;
    private final VnpayGatewayClient gateway;

    public VnpayPaymentServiceImpl(PaymentService payments, VnpayGatewayClient gateway) {
        this.payments = payments;
        this.gateway = gateway;
    }

    @Override
    public Checkout checkout(Long bookingId, Long customerId, String clientIp) {
        if (!gateway.isConfigured()) {
            throw new IllegalStateException("Hãy cấu hình VNPAY_TMN_CODE và VNPAY_HASH_SECRET trong file .env "
                    + "rồi khởi động lại ứng dụng trước khi thanh toán qua VNPay.");
        }
        OnlineCheckout attempt = payments.prepareOnlineCheckout(bookingId, customerId);
        if (attempt.recoverExisting()) {
            // Khách có thể đã trả tiền lần trước nhưng chưa quay về trang; phải hỏi VNPay trước để không thu hai lần.
            QueryResult result = query(attempt.orderCode(), attempt.createdAt());
            if (result.paid()) {
                payments.confirmOnlinePayment(attempt.orderCode(), result.amount());
                return new Checkout(null, true);
            }
            // VNPay không cho mở lại đơn đã bắt đầu, nên chỉ tạo giao dịch mới khi link cũ đã hết hạn.
            if (!result.knownUnpaid() && gateway.checkoutOpen(attempt.createdAt())) {
                String retryTime = CLOCK.format(attempt.createdAt().plusMinutes(VnpayGatewayClient.CHECKOUT_MINUTES));
                throw new IllegalStateException("Hãy hoàn tất lần thanh toán VNPay đang mở hoặc thử lại sau " + retryTime
                        + ": VNPay chưa xác nhận lần thanh toán trước, nên hệ thống chưa tạo giao dịch mới để tránh thu tiền hai lần.");
            }
            payments.failOnlinePayment(attempt.orderCode(), BigDecimal.valueOf(attempt.amount()));
            attempt = payments.prepareOnlineCheckout(bookingId, customerId);
        }
        String paymentUrl = gateway.paymentUrl(attempt.orderCode(), attempt.amount(), attempt.createdAt(), clientIp);
        return new Checkout(paymentUrl, false);
    }

    /**
     * Khách quay về từ VNPay. Thứ tự xử lý:
     * <ol>
     *   <li>Giao dịch đã được ghi nhận thành công trước đó: báo thành công.</li>
     *   <li>QueryDR xác nhận đã trả tiền: ghi nhận thanh toán.</li>
     *   <li>QueryDR không trả lời được nhưng dữ liệu có chữ ký báo thành công: tin dữ liệu đó.</li>
     *   <li>VNPay báo lỗi: đánh dấu thất bại. Còn lại: chờ VNPay xác nhận.</li>
     * </ol>
     */
    @Override
    public ReturnOutcome handleReturn(Map<String, String> params) {
        if (!gateway.hasValidSignature(params)) {
            throw new IllegalArgumentException("Chữ ký dữ liệu VNPay trả về không hợp lệ.");
        }
        long orderCode = orderCode(params);
        OnlinePayment attempt = payments.findOnlinePayment(orderCode)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy giao dịch thanh toán trực tuyến."));
        if (PAID_STATUS.equals(attempt.status())) {
            return new ReturnOutcome(attempt.bookingId(), ReturnStatus.PAID);
        }

        QueryResult result = query(orderCode, attempt.createdAt());
        if (result.paid()) {
            payments.confirmOnlinePayment(orderCode, result.amount());
            return new ReturnOutcome(attempt.bookingId(), ReturnStatus.PAID);
        }
        if (!result.answered() && isSuccessful(params)) {
            // Số tiền đã lưu vẫn được kiểm tra lại bên trong confirmOnlinePayment.
            log.warn("VNPay QueryDR unavailable for order {} (code {}); confirming from the signed return data.",
                    orderCode, result.responseCode());
            payments.confirmOnlinePayment(orderCode, vnpayAmount(params));
            return new ReturnOutcome(attempt.bookingId(), ReturnStatus.PAID);
        }
        if (!VnpayCodes.SUCCESS.equals(params.get("vnp_ResponseCode"))) {
            payments.failOnlinePayment(orderCode, attempt.amount());
            return new ReturnOutcome(attempt.bookingId(), ReturnStatus.FAILED);
        }
        log.warn("VNPay has not confirmed order {} yet (query response {}, transaction status {}).",
                orderCode, result.responseCode(), result.transactionStatus());
        return new ReturnOutcome(attempt.bookingId(), ReturnStatus.PENDING);
    }

    /** VNPay gọi thẳng vào máy chủ (IPN); mã RspCode trả về theo đúng quy định IPN của VNPay. */
    @Override
    public IpnResponse handleIpn(Map<String, String> params) {
        if (!gateway.hasValidSignature(params)) return new IpnResponse("97", "Invalid Checksum");
        try {
            long orderCode = orderCode(params);
            Optional<OnlinePayment> found = payments.findOnlinePayment(orderCode);
            if (found.isEmpty()) return new IpnResponse("01", "Order not found");

            OnlinePayment attempt = found.get();
            if (attempt.amount().compareTo(vnpayAmount(params)) != 0) return new IpnResponse("04", "Invalid amount");
            if (PAID_STATUS.equals(attempt.status())) return new IpnResponse("02", "Order already confirmed");

            if (isSuccessful(params)) {
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

    /** Gọi QueryDR; nếu không kết nối được VNPay thì trả về kết quả "chưa tra cứu được" thay vì báo lỗi. */
    private QueryResult query(long orderCode, LocalDateTime createdAt) {
        try {
            return gateway.query(orderCode, createdAt);
        } catch (RestClientException unreachable) {
            log.warn("VNPay QueryDR for order {} could not be reached.", orderCode, unreachable);
            return new QueryResult("UNREACHABLE", "", null);
        }
    }

    /** Dữ liệu VNPay gửi về báo giao dịch thành công. */
    private static boolean isSuccessful(Map<String, String> params) {
        return VnpayCodes.SUCCESS.equals(params.get("vnp_ResponseCode"))
                && VnpayCodes.SUCCESS.equals(params.get("vnp_TransactionStatus"));
    }

    private static long orderCode(Map<String, String> params) {
        try {
            return Long.parseLong(params.get("vnp_TxnRef"));
        } catch (NumberFormatException invalid) {
            throw new IllegalArgumentException("Mã đơn hàng VNPay gửi về không hợp lệ.", invalid);
        }
    }

    private static BigDecimal vnpayAmount(Map<String, String> params) {
        try {
            return VnpayCodes.toVnd(params.get("vnp_Amount"));
        } catch (NullPointerException | NumberFormatException invalid) {
            throw new IllegalArgumentException("Số tiền VNPay gửi về không hợp lệ.", invalid);
        }
    }
}
