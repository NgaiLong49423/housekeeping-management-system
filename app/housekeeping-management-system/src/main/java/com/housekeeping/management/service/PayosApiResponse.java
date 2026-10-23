package com.housekeeping.management.service;

import java.util.Map;

public record PayosApiResponse(String code, String desc, Map<String, Object> data, String signature) { }
