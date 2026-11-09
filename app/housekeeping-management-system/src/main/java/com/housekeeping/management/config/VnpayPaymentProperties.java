package com.housekeeping.management.config;

import java.net.URI;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.payment.vnpay")
public class VnpayPaymentProperties {
    private String tmnCode = "";
    private String hashSecret = "";
    private String payUrl = "https://sandbox.vnpayment.vn/paymentv2/vpcpay.html";
    private String apiUrl = "https://sandbox.vnpayment.vn/merchant_webapi/api/transaction";
    private String returnUrl = "http://localhost:8080/payments/vnpay/return";

    public String getTmnCode() { return tmnCode; }
    public void setTmnCode(String tmnCode) { this.tmnCode = tmnCode; }
    public String getHashSecret() { return hashSecret; }
    public void setHashSecret(String hashSecret) { this.hashSecret = hashSecret; }
    public String getPayUrl() { return payUrl; }
    public void setPayUrl(String payUrl) { this.payUrl = payUrl; }
    public String getApiUrl() { return apiUrl; }
    public void setApiUrl(String apiUrl) { this.apiUrl = apiUrl; }
    public String getReturnUrl() { return returnUrl; }
    public void setReturnUrl(String returnUrl) { this.returnUrl = returnUrl; }

    public boolean isConfigured() {
        return present(tmnCode) && present(hashSecret) && https(payUrl) && https(apiUrl) && absoluteWebUrl(returnUrl);
    }

    private static boolean present(String value) { return value != null && !value.isBlank(); }

    private static boolean https(String value) {
        URI uri = parse(value);
        return uri != null && "https".equalsIgnoreCase(uri.getScheme()) && present(uri.getHost());
    }

    private static boolean absoluteWebUrl(String value) {
        URI uri = parse(value);
        return uri != null && ("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme()))
                && present(uri.getHost());
    }

    private static URI parse(String value) {
        if (!present(value)) return null;
        try {
            return URI.create(value.trim());
        } catch (IllegalArgumentException invalid) {
            return null;
        }
    }
}
