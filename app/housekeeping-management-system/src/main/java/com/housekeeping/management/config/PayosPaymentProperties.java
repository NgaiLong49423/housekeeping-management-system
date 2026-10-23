package com.housekeeping.management.config;

import java.net.URI;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.payment.payos")
public class PayosPaymentProperties {
    private String baseUrl = "https://api-merchant.payos.vn";
    private String clientId = "";
    private String apiKey = "";
    private String checksumKey = "";
    private String returnUrl = "";
    private String cancelUrl = "";
    private String webhookUrl = "";

    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
    public String getClientId() { return clientId; }
    public void setClientId(String clientId) { this.clientId = clientId; }
    public String getApiKey() { return apiKey; }
    public void setApiKey(String apiKey) { this.apiKey = apiKey; }
    public String getChecksumKey() { return checksumKey; }
    public void setChecksumKey(String checksumKey) { this.checksumKey = checksumKey; }
    public String getReturnUrl() { return returnUrl; }
    public void setReturnUrl(String returnUrl) { this.returnUrl = returnUrl; }
    public String getCancelUrl() { return cancelUrl; }
    public void setCancelUrl(String cancelUrl) { this.cancelUrl = cancelUrl; }
    public String getWebhookUrl() { return webhookUrl; }
    public void setWebhookUrl(String webhookUrl) { this.webhookUrl = webhookUrl; }

    public boolean isConfigured() {
        return present(clientId) && present(apiKey) && present(checksumKey)
                && officialApiBaseUrl(baseUrl) && publicHttps(returnUrl) && publicHttps(cancelUrl) && publicHttps(webhookUrl);
    }

    private static boolean present(String value) { return value != null && !value.isBlank(); }

    private static boolean publicHttps(String value) {
        if (!present(value)) return false;
        try {
            URI uri = URI.create(value);
            String host = uri.getHost();
            if (!"https".equalsIgnoreCase(uri.getScheme()) || host == null || host.isBlank()) return false;
            String normalizedHost = host.toLowerCase(java.util.Locale.ROOT);
            return !normalizedHost.equals("localhost") && !normalizedHost.endsWith(".localhost")
                    && !normalizedHost.endsWith(".local") && !normalizedHost.equals("127.0.0.1")
                    && !normalizedHost.equals("::1") && !normalizedHost.startsWith("192.168.")
                    && !normalizedHost.startsWith("10.");
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    private static boolean officialApiBaseUrl(String value) {
        try {
            URI uri = URI.create(value);
            return "https".equalsIgnoreCase(uri.getScheme())
                    && "api-merchant.payos.vn".equalsIgnoreCase(uri.getHost())
                    && (uri.getPort() == -1 || uri.getPort() == 443)
                    && (uri.getPath().isEmpty() || "/".equals(uri.getPath()));
        } catch (IllegalArgumentException | NullPointerException ignored) {
            return false;
        }
    }
}
