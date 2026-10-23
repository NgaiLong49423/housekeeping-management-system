package com.housekeeping.management.service;

public record MomoIpnRequest(
        String partnerCode,
        String requestId,
        String orderId,
        Long amount,
        String orderInfo,
        String orderType,
        String partnerClientId,
        Long transId,
        Integer resultCode,
        String message,
        String payType,
        Long responseTime,
        String extraData,
        String signature) { }
