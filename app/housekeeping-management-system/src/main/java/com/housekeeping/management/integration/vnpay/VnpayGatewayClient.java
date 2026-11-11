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

/** Talks to VNPay: builds signed checkout URLs, verifies returned data and queries transaction results. */
@Component
public class VnpayGatewayClient {
    private static final Logger log = LoggerFactory.getLogger(VnpayGatewayClient.class);
    private static final ZoneOffset VIETNAM = ZoneOffset.ofHours(7);
    private static final DateTimeFormatter VNPAY_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final String VERSION = "2.1.0";
    /** Lifetime of a VNPay checkout ({@code vnp_ExpireDate}); VNPay refuses to reopen an order it has started. */
    public static final int CHECKOUT_MINUTES = 15;
    /** VNPay asks for the calling server's IP; the sandbox does not restrict it. */
    private static final String SERVER_IP = "127.0.0.1";

    private final VnpayPaymentProperties properties;
    private final RestClient restClient;
    private final Clock clock;
    private final Supplier<String> requestIds;

    @Autowired
    public VnpayGatewayClient(VnpayPaymentProperties properties) {
        this(properties, RestClient.builder().requestFactory(timeoutRequestFactory()), Clock.system(VIETNAM),
                () -> UUID.randomUUID().toString().replace("-", ""));
    }

    VnpayGatewayClient(VnpayPaymentProperties properties, RestClient.Builder restClient, Clock clock,
            Supplier<String> requestIds) {
        this.properties = properties;
        this.restClient = restClient.build();
        this.clock = clock;
        this.requestIds = requestIds;
    }

    public boolean isConfigured() { return properties.isConfigured(); }

    /** Signed VNPay checkout URL; {@code createdAt} must be the stored attempt time so it can be queried later. */
    public String paymentUrl(long txnRef, long amount, LocalDateTime createdAt, String clientIp) {
        Map<String, String> params = new TreeMap<>();
        params.put("vnp_Version", VERSION);
        params.put("vnp_Command", "pay");
        params.put("vnp_TmnCode", properties.getTmnCode());
        params.put("vnp_Amount", Long.toString(Math.multiplyExact(amount, 100L)));
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

    /** VNPay stops accepting a checkout at its {@code vnp_ExpireDate}, set from the attempt's creation time. */
    public boolean checkoutOpen(LocalDateTime createdAt) {
        return LocalDateTime.now(clock).isBefore(createdAt.plusMinutes(CHECKOUT_MINUTES));
    }

    /** Verifies the checksum of data VNPay sent back through the browser return or the IPN call. */
    public boolean hasValidSignature(Map<String, String> params) {
        return VnpaySignature.hasValidSignature(params, properties.getHashSecret());
    }

    /** Server-to-server QueryDR lookup of a payment attempt, so the result never depends on browser data. */
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
        if (response == null) throw new IllegalStateException("VNPay QueryDR returned an empty response.");
        String responseCode = text(response, "vnp_ResponseCode");
        if (!"00".equals(responseCode)) {
            // VNPay does not sign error responses; they can only ever mean "not confirmed as paid".
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
            throw new IllegalStateException("VNPay QueryDR response checksum is invalid.");
        }
        if (!Long.toString(txnRef).equals(text(response, "vnp_TxnRef"))
                || !properties.getTmnCode().equals(text(response, "vnp_TmnCode"))) {
            throw new IllegalStateException("VNPay QueryDR response does not match the requested order.");
        }
        return new QueryResult(responseCode, text(response, "vnp_TransactionStatus"), vnpayAmount(text(response, "vnp_Amount")));
    }

    private static BigDecimal vnpayAmount(String value) {
        if (value.isEmpty()) return null;
        try {
            return new BigDecimal(value).movePointLeft(2);
        } catch (NumberFormatException invalid) {
            throw new IllegalStateException("VNPay returned an invalid amount.", invalid);
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

    /** {@code amount} is in VND (VNPay sends it multiplied by 100). */
    public record QueryResult(String responseCode, String transactionStatus, BigDecimal amount) {
        public boolean paid() {
            return "00".equals(responseCode) && "00".equals(transactionStatus) && amount != null;
        }

        /** VNPay has the transaction but the customer has not finished paying it. */
        public boolean unfinished() {
            return "00".equals(responseCode) && "01".equals(transactionStatus);
        }

        /** VNPay positively reports that no money was taken: the order is unknown (91) or the payment failed (02). */
        public boolean knownUnpaid() {
            return "91".equals(responseCode) || ("00".equals(responseCode) && "02".equals(transactionStatus));
        }

        /** QueryDR actually answered about the order; any other code means the lookup itself is unavailable. */
        public boolean answered() {
            return "00".equals(responseCode) || "91".equals(responseCode);
        }
    }
}
