package com.housekeeping.management.service;

public record PayosCreatePaymentRequest(long orderCode, long amount, String description,
        String cancelUrl, String returnUrl, String signature) { }
