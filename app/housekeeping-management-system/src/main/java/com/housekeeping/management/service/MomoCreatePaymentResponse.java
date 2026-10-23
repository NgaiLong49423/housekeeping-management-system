package com.housekeeping.management.service;

public record MomoCreatePaymentResponse(
        String partnerCode,
        String requestId,
        String orderId,
        Long amount,
        Long responseTime,
        String message,
        Integer resultCode,
        String payUrl,
        String signature) { }
