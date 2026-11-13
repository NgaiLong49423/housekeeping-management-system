package com.housekeeping.management.integration.vnpay;

import com.housekeeping.management.config.VnpayPaymentProperties;
import java.math.BigDecimal;
import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/** Làm việc với VNPay: tạo link thanh toán có chữ ký, kiểm tra chữ ký dữ liệu trả về và tra cứu giao dịch (QueryDR). */
@Component
public class VnpayGatewayClient {
    private static final Logger log = LoggerFactory.getLogger(VnpayGatewayClient.class);
    private static final ZoneOffset VIETNAM = ZoneOffset.ofHours(7);
    private static final DateTimeFormatter VNPAY_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final String VERSION = "2.1.0";
    /** Thời gian link thanh toán còn hiệu lực ({@code vnp_ExpireDate}); VNPay không cho mở lại đơn đã bắt đầu. */
    public static final int CHECKOUT_MINUTES = 15;
    /** VNPay yêu cầu IP của máy chủ gọi QueryDR; môi trường sandbox không giới hạn IP. */
    private static final String SERVER_IP = "127.0.0.1";

    private final VnpayPaymentProperties properties;
    private final RestClient restClient;
    private final Clock clock;
    private final Supplier<String> requestIds;

    /** Constructor Spring dùng khi chạy thật. */
    @Autowired
    public VnpayGatewayClient(VnpayPaymentProperties properties) {
        this(properties, RestClient.builder().requestFactory(timeoutRequestFactory()), Clock.system(VIETNAM),
                () -> UUID.randomUUID().toString().replace("-", ""));
    }

    /** Constructor cho test: truyền đồng hồ và mã request cố định để kết quả lặp lại được. */
    VnpayGatewayClient(VnpayPaymentProperties properties, RestClient.Builder restClient, Clock clock,
            Supplier<String> requestIds) {
        this.properties = properties;
        this.restClient = restClient.build();
        this.clock = clock;
        this.requestIds = requestIds;
    }

    public boolean isConfigured() { return properties.isConfigured(); }

    /** Link thanh toán VNPay có chữ ký; {@code createdAt} phải là thời điểm đã lưu để sau này tra cứu QueryDR. */
    public String paymentUrl(long txnRef, long amount, LocalDateTime createdAt, String clientIp) {
        Map<String, String> params = new TreeMap<>();
        params.put("vnp_Version", VERSION);
        params.put("vnp_Command", "pay");
        params.put("vnp_TmnCode", properties.getTmnCode());
        params.put("vnp_Amount", VnpayCodes.fromVnd(amount));
        params.put("vnp_CurrCode", "VND");
        params.put("vnp_TxnRef", Long.toString(txnRef));
        params.put("vnp_OrderInfo", "Thanh toan don dat lich " + txnRef);
        params.put("vnp_OrderType", "other");
        params.put("vnp_Locale", "vn");
        params.put("vnp_ReturnUrl", properties.getReturnUrl());
        params.put("vnp_IpAddr", clientIp);
        params.put("vnp_CreateDate", VNPAY_TIME.format(createdAt));
        params.put("vnp_ExpireDate", VNPAY_TIME.format(createdAt.plusMinutes(CHECKOUT_MINUTES)));
        String query = params.entrySet().stream()
                .map(entry -> VnpaySignature.encode(entry.getKey()) + "=" + VnpaySignature.encode(entry.getValue()))
                .collect(Collectors.joining("&"));
        return properties.getPayUrl() + "?" + query + "&" + VnpaySignature.SECURE_HASH + "="
                + VnpaySignature.sign(params, properties.getHashSecret());
    }

    /** Link thanh toán còn mở, tức là chưa quá {@code vnp_ExpireDate}. */
    public boolean checkoutOpen(LocalDateTime createdAt) {
        return LocalDateTime.now(clock).isBefore(createdAt.plusMinutes(CHECKOUT_MINUTES));
    }

    /** Kiểm tra chữ ký của dữ liệu VNPay gửi về (qua trình duyệt hoặc qua IPN). */
    public boolean hasValidSignature(Map<String, String> params) {
        return VnpaySignature.hasValidSignature(params, properties.getHashSecret());
    }

    /** Hỏi trực tiếp VNPay (QueryDR) về kết quả giao dịch, để không phụ thuộc dữ liệu trên trình duyệt. */
    public QueryResult query(long txnRef, LocalDateTime transactionDate) {
        Map<String, String> request = new LinkedHashMap<>();
        request.put("vnp_RequestId", requestIds.get());
        request.put("vnp_Version", VERSION);
        request.put("vnp_Command", "querydr");
        request.put("vnp_TmnCode", properties.getTmnCode());
        request.put("vnp_TxnRef", Long.toString(txnRef));
        request.put("vnp_OrderInfo", "Kiem tra giao dich " + txnRef);
        request.put("vnp_TransactionDate", VNPAY_TIME.format(transactionDate));
        request.put("vnp_CreateDate", VNPAY_TIME.format(LocalDateTime.now(clock)));
        request.put("vnp_IpAddr", SERVER_IP);
        // Thứ tự các trường nối bằng "|" do tài liệu QueryDR của VNPay quy định.
        request.put(VnpaySignature.SECURE_HASH, VnpaySignature.hmacSha512(properties.getHashSecret(), String.join("|",
                request.get("vnp_RequestId"), request.get("vnp_Version"), request.get("vnp_Command"),
                request.get("vnp_TmnCode"), request.get("vnp_TxnRef"), request.get("vnp_TransactionDate"),
                request.get("vnp_CreateDate"), request.get("vnp_IpAddr"), request.get("vnp_OrderInfo"))));

        Map<String, Object> response = restClient.post()
                .uri(properties.getApiUrl())
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(new ParameterizedTypeReference<Map<String, Object>>() { });
        if (response == null) throw new IllegalStateException("VNPay không trả dữ liệu khi tra cứu giao dịch.");
        String responseCode = text(response, "vnp_ResponseCode");
        if (!VnpayCodes.SUCCESS.equals(responseCode)) {
            // VNPay không ký phản hồi lỗi; phản hồi lỗi chỉ có nghĩa là "chưa xác nhận đã thanh toán".
            log.warn("VNPay QueryDR for order {} answered {} ({}).", txnRef, responseCode, text(response, "vnp_Message"));
            return new QueryResult(responseCode, text(response, "vnp_TransactionStatus"), null);
        }
        String expectedSignature = VnpaySignature.hmacSha512(properties.getHashSecret(), String.join("|",
                text(response, "vnp_ResponseId"), text(response, "vnp_Command"), text(response, "vnp_ResponseCode"),
                text(response, "vnp_Message"), text(response, "vnp_TmnCode"), text(response, "vnp_TxnRef"),
                text(response, "vnp_Amount"), text(response, "vnp_BankCode"), text(response, "vnp_PayDate"),
                text(response, "vnp_TransactionNo"), text(response, "vnp_TransactionType"),
                text(response, "vnp_TransactionStatus"), text(response, "vnp_OrderInfo"),
                text(response, "vnp_PromotionCode"), text(response, "vnp_PromotionAmount")));
        if (!VnpaySignature.matches(text(response, VnpaySignature.SECURE_HASH), expectedSignature)) {
            throw new IllegalStateException("Chữ ký phản hồi QueryDR của VNPay không hợp lệ.");
        }
        if (!Long.toString(txnRef).equals(text(response, "vnp_TxnRef"))
                || !properties.getTmnCode().equals(text(response, "vnp_TmnCode"))) {
            throw new IllegalStateException("Phản hồi QueryDR của VNPay không khớp với đơn hàng đã hỏi.");
        }
        return new QueryResult(responseCode, text(response, "vnp_TransactionStatus"), vnpayAmount(text(response, "vnp_Amount")));
    }

    private static BigDecimal vnpayAmount(String value) {
        if (value.isEmpty()) return null;
        try {
            return VnpayCodes.toVnd(value);
        } catch (NumberFormatException invalid) {
            throw new IllegalStateException("VNPay trả về số tiền không hợp lệ.", invalid);
        }
    }

    private static String text(Map<String, Object> response, String field) {
        Object value = response.get(field);
        return value == null ? "" : String.valueOf(value);
    }

    private static JdkClientHttpRequestFactory timeoutRequestFactory() {
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20)).build());
        factory.setReadTimeout(Duration.ofSeconds(20));
        return factory;
    }

    /** Kết quả QueryDR; {@code amount} đã đổi về VND. */
    public record QueryResult(String responseCode, String transactionStatus, BigDecimal amount) {
        /** VNPay xác nhận khách đã thanh toán thành công. */
        public boolean paid() {
            return VnpayCodes.SUCCESS.equals(responseCode) && VnpayCodes.SUCCESS.equals(transactionStatus)
                    && amount != null;
        }

        /** VNPay có giao dịch nhưng khách chưa thanh toán xong. */
        public boolean unfinished() {
            return VnpayCodes.SUCCESS.equals(responseCode) && VnpayCodes.UNFINISHED.equals(transactionStatus);
        }

        /** VNPay khẳng định chưa trừ tiền: không có đơn này, hoặc thanh toán đã thất bại. */
        public boolean knownUnpaid() {
            return VnpayCodes.ORDER_NOT_FOUND.equals(responseCode)
                    || (VnpayCodes.SUCCESS.equals(responseCode) && VnpayCodes.FAILED.equals(transactionStatus));
        }

        /** QueryDR đã trả lời về đơn hàng; mã khác nghĩa là chưa tra cứu được (VNPay lỗi hoặc mất kết nối). */
        public boolean answered() {
            return VnpayCodes.SUCCESS.equals(responseCode) || VnpayCodes.ORDER_NOT_FOUND.equals(responseCode);
        }
    }
}
