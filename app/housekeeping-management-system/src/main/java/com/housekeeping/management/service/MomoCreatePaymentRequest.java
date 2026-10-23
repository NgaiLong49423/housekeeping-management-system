package com.housekeeping.management.service;

public record MomoCreatePaymentRequest(
        String partnerCode,
        String partnerName,
        String storeId,
        String requestId,
        long amount,
        String orderId,
        String orderInfo,
        String redirectUrl,
        String ipnUrl,
        String requestType,
        String extraData,
        boolean autoCapture,
        String lang,
        String signature) { }
