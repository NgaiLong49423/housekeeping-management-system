package com.housekeeping.management.service;

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

/** VNPay HMAC-SHA512 checksums, following VNPay's Java integration sample. */
final class VnpaySignature {
    static final String SECURE_HASH = "vnp_SecureHash";
    static final String SECURE_HASH_TYPE = "vnp_SecureHashType";

    private VnpaySignature() { }

    /** Sorted {@code name=urlEncodedValue} pairs joined by {@code &}, without empty values or hash fields. */
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
            throw new IllegalStateException("HMAC-SHA512 is unavailable.", unavailable);
        }
    }

    static boolean matches(String actual, String expected) {
        if (actual == null || expected == null) return false;
        return MessageDigest.isEqual(actual.toLowerCase(Locale.ROOT).getBytes(StandardCharsets.US_ASCII),
                expected.toLowerCase(Locale.ROOT).getBytes(StandardCharsets.US_ASCII));
    }

    static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.US_ASCII);
    }
}
