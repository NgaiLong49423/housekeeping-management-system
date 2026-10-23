package com.housekeeping.management.service;

import java.util.Map;

public record PayosWebhookRequest(String code, String desc, Boolean success,
        Map<String, Object> data, String signature) { }
