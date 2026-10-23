package com.housekeeping.management.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.payment.momo")
public class MomoPaymentProperties {
    private String baseUrl = "https://test-payment.momo.vn";
    private String partnerCode = "";
    private String accessKey = "";
    private String secretKey = "";
    private String redirectUrl = "";
    private String ipnUrl = "";

    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
    public String getPartnerCode() { return partnerCode; }
    public void setPartnerCode(String partnerCode) { this.partnerCode = partnerCode; }
    public String getAccessKey() { return accessKey; }
    public void setAccessKey(String accessKey) { this.accessKey = accessKey; }
    public String getSecretKey() { return secretKey; }
    public void setSecretKey(String secretKey) { this.secretKey = secretKey; }
    public String getRedirectUrl() { return redirectUrl; }
    public void setRedirectUrl(String redirectUrl) { this.redirectUrl = redirectUrl; }
    public String getIpnUrl() { return ipnUrl; }
    public void setIpnUrl(String ipnUrl) { this.ipnUrl = ipnUrl; }

    public boolean isConfigured() {
        return present(partnerCode) && present(accessKey) && present(secretKey)
                && publicHttps(redirectUrl) && publicHttps(ipnUrl);
    }

    private static boolean present(String value) { return value != null && !value.isBlank(); }
    private static boolean publicHttps(String value) {
        if (!present(value)) return false;
        try {
            java.net.URI uri = java.net.URI.create(value);
            String host = uri.getHost();
            return "https".equalsIgnoreCase(uri.getScheme()) && host != null
                    && !host.equalsIgnoreCase("localhost") && !host.endsWith(".local")
                    && !host.equals("127.0.0.1") && !host.equals("::1");
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }
}
