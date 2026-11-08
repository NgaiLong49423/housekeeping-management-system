package com.housekeeping.management.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.maps.vietmap")
public record VietmapProperties(String apiKey, String baseUrl) {
    public VietmapProperties {
        if (baseUrl == null || baseUrl.isBlank()) baseUrl = "https://maps.vietmap.vn";
    }
}
