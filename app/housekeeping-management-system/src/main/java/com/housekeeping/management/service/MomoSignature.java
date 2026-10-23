package com.housekeeping.management.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

final class MomoSignature {
    private MomoSignature() { }

    static String createPaymentRequest(MomoCreatePaymentRequest request, String accessKey, String secretKey) {
        String data = "accessKey=%s&amount=%d&extraData=%s&ipnUrl=%s&orderId=%s&orderInfo=%s&partnerCode=%s&redirectUrl=%s&requestId=%s&requestType=%s"
                .formatted(accessKey, request.amount(), request.extraData(), request.ipnUrl(),
                        request.orderId(), request.orderInfo(), request.partnerCode(), request.redirectUrl(),
                        request.requestId(), request.requestType());
        return hmac(data, secretKey);
    }

    static String createWebhook(MomoIpnRequest ipn, String accessKey, String secretKey) {
        String data = "accessKey=%s&amount=%s&extraData=%s&message=%s&orderId=%s&orderInfo=%s&orderType=%s&partnerClientId=%s&partnerCode=%s&payType=%s&requestId=%s&responseTime=%s&resultCode=%s&transId=%s"
                .formatted(value(accessKey), value(ipn.amount()), value(ipn.extraData()), value(ipn.message()),
                        value(ipn.orderId()), value(ipn.orderInfo()), value(ipn.orderType()),
                        value(ipn.partnerClientId()), value(ipn.partnerCode()), value(ipn.payType()),
                        value(ipn.requestId()), value(ipn.responseTime()), value(ipn.resultCode()), value(ipn.transId()));
        return hmac(data, secretKey);
    }

    static boolean isValid(String actual, String expected) {
        return actual != null && MessageDigest.isEqual(actual.toLowerCase().getBytes(StandardCharsets.US_ASCII),
                expected.toLowerCase().getBytes(StandardCharsets.US_ASCII));
    }

    private static String hmac(String data, String secretKey) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secretKey.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return java.util.HexFormat.of().formatHex(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.GeneralSecurityException exception) {
            throw new IllegalStateException("Unable to create MoMo request signature", exception);
        }
    }

    private static String value(Object value) { return value == null ? "" : value.toString(); }
}
