package com.housekeeping.management.integration.vnpay;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** Tạo và kiểm tra chữ ký HMAC-SHA512 của VNPay, theo code mẫu Java của VNPay. */
final class VnpaySignature {
    static final String SECURE_HASH = "vnp_SecureHash";
    static final String SECURE_HASH_TYPE = "vnp_SecureHashType";

    private VnpaySignature() { }

    /**
     * Chuỗi dùng để ký: các cặp {@code tên=giá trị đã mã hóa URL}, sắp xếp theo tên và nối bằng {@code &};
     * bỏ các giá trị rỗng và chính hai trường chữ ký.
     */
    static String hashData(Map<String, String> params) {
        return new TreeMap<>(params).entrySet().stream()
                .filter(entry -> !SECURE_HASH.equals(entry.getKey()) && !SECURE_HASH_TYPE.equals(entry.getKey()))
                .filter(entry -> entry.getValue() != null && !entry.getValue().isEmpty())
                .map(entry -> entry.getKey() + "=" + encode(entry.getValue()))
                .collect(Collectors.joining("&"));
    }

    static String sign(Map<String, String> params, String secret) {
        return hmacSha512(secret, hashData(params));
    }

    static boolean hasValidSignature(Map<String, String> params, String secret) {
        return params != null && matches(params.get(SECURE_HASH), sign(params, secret));
    }

    static String hmacSha512(String secret, String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA512");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA512"));
            return HexFormat.of().formatHex(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException unavailable) {
            throw new IllegalStateException("Máy chủ không hỗ trợ thuật toán HMAC-SHA512.", unavailable);
        }
    }

    /** So sánh hai chữ ký với thời gian không đổi, để không bị dò chữ ký qua thời gian phản hồi. */
    static boolean matches(String actual, String expected) {
        if (actual == null || expected == null) return false;
        return MessageDigest.isEqual(actual.toLowerCase(Locale.ROOT).getBytes(StandardCharsets.US_ASCII),
                expected.toLowerCase(Locale.ROOT).getBytes(StandardCharsets.US_ASCII));
    }

    static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.US_ASCII);
    }
}
