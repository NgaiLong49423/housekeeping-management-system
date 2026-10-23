package com.housekeeping.management.service;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.TreeMap;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

final class PayosSignature {
    private static final ObjectMapper JSON = new ObjectMapper();

    private PayosSignature() { }

    static String createPaymentRequest(long orderCode, long amount, String description, String cancelUrl,
            String returnUrl, String checksumKey) {
        String data = "amount=%d&cancelUrl=%s&description=%s&orderCode=%d&returnUrl=%s"
                .formatted(amount, cancelUrl, description, orderCode, returnUrl);
        return hmac(data, checksumKey);
    }

    static String createDataSignature(Map<String, Object> data, String checksumKey) {
        String canonical = new TreeMap<>(data).entrySet().stream()
                .map(entry -> entry.getKey() + "=" + canonicalValue(entry.getValue()))
                .collect(java.util.stream.Collectors.joining("&"));
        return hmac(canonical, checksumKey);
    }

    static boolean isValid(String actual, String expected) {
        if (actual == null || expected == null) return false;
        return MessageDigest.isEqual(actual.toLowerCase(java.util.Locale.ROOT).getBytes(StandardCharsets.US_ASCII),
                expected.toLowerCase(java.util.Locale.ROOT).getBytes(StandardCharsets.US_ASCII));
    }

    private static String canonicalValue(Object value) {
        if (value == null || "null".equals(value) || "undefined".equals(value)) return "";
        if (value instanceof Map<?, ?> map) {
            TreeMap<String, Object> sorted = new TreeMap<>();
            map.forEach((key, nested) -> sorted.put(String.valueOf(key), nested));
            return json(sorted);
        }
        if (value instanceof Collection<?> collection) {
            ArrayList<Object> ordered = new ArrayList<>(collection.size());
            for (Object item : collection) {
                if (item instanceof Map<?, ?> map) {
                    TreeMap<String, Object> sorted = new TreeMap<>();
                    map.forEach((key, nested) -> sorted.put(String.valueOf(key), nested));
                    ordered.add(sorted);
                } else {
                    ordered.add(item);
                }
            }
            return json(ordered);
        }
        return String.valueOf(value);
    }

    private static String json(Object value) {
        try {
            return JSON.writeValueAsString(value);
        } catch (JacksonException exception) {
            throw new IllegalArgumentException("Could not serialize PayOS signature data", exception);
        }
    }

    private static String hmac(String data, String checksumKey) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(checksumKey.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return java.util.HexFormat.of().formatHex(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.GeneralSecurityException exception) {
            throw new IllegalStateException("Unable to create PayOS signature", exception);
        }
    }
}
