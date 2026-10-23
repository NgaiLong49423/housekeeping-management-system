package com.housekeeping.management.service;

import com.housekeeping.management.config.PayosPaymentProperties;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

@Component
public class PayosGatewayClient {
    private static final String CHECKOUT_HOST = "pay.payos.vn";
    private final RestClient restClient;
    private final PayosPaymentProperties properties;

    public PayosGatewayClient(PayosPaymentProperties properties) {
        HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20)).build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofSeconds(20));
        this.restClient = RestClient.builder().baseUrl(properties.getBaseUrl()).requestFactory(requestFactory).build();
        this.properties = properties;
    }

    public Checkout createCheckout(long orderCode, long amount) {
        requireConfigured();
        String description = "GN" + String.format(java.util.Locale.ROOT, "%07d", orderCode % 10_000_000);
        String signature = PayosSignature.createPaymentRequest(orderCode, amount, description,
                properties.getCancelUrl(), properties.getReturnUrl(), properties.getChecksumKey());
        PayosCreatePaymentRequest request = new PayosCreatePaymentRequest(orderCode, amount, description,
                properties.getCancelUrl(), properties.getReturnUrl(), signature);
        PayosApiResponse response = restClient.post()
                .uri("/v2/payment-requests")
                .header("x-client-id", properties.getClientId())
                .header("x-api-key", properties.getApiKey())
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(PayosApiResponse.class);
        return validateCreateResponse(response, orderCode, amount);
    }

    /** Recover the checkout after a timed-out create call using PayOS's order lookup API. */
    public Checkout recoverCheckout(long orderCode, long amount) {
        requireConfigured();
        PayosApiResponse response;
        try {
            response = restClient.get()
                    .uri("/v2/payment-requests/{orderCode}", orderCode)
                    .header("x-client-id", properties.getClientId())
                    .header("x-api-key", properties.getApiKey())
                    .retrieve()
                    .body(PayosApiResponse.class);
        } catch (HttpClientErrorException.NotFound notFound) {
            return null;
        }
        Map<String, Object> data = response == null ? null : response.data();
        if (!isSignedSuccess(response) || data == null || number(data.get("orderCode")) != orderCode
                || number(data.get("amount")) != amount || !"PENDING".equals(data.get("status"))) {
            throw new IllegalStateException("PayOS found an existing order that cannot be safely reopened.");
        }
        String linkId = string(data.get("id"));
        if (!present(linkId)) throw new IllegalStateException("PayOS response did not include a payment-link ID.");
        return new Checkout("https://pay.payos.vn/web/" + linkId, linkId);
    }

    public boolean verifyWebhook(PayosWebhookRequest webhook) {
        return isConfigured() && webhook != null && webhook.data() != null
                && PayosSignature.isValid(webhook.signature(),
                        PayosSignature.createDataSignature(webhook.data(), properties.getChecksumKey()));
    }

    public boolean isConfigured() { return properties.isConfigured(); }

    private Checkout validateCreateResponse(PayosApiResponse response, long expectedOrderCode, long expectedAmount) {
        Map<String, Object> data = response == null ? null : response.data();
        if (!isSignedSuccess(response) || data == null || number(data.get("orderCode")) != expectedOrderCode
                || number(data.get("amount")) != expectedAmount || !"VND".equals(data.get("currency"))
                || !"PENDING".equals(data.get("status"))) {
            throw new IllegalStateException("PayOS did not return a valid pending checkout response.");
        }
        String checkoutUrl = string(data.get("checkoutUrl"));
        String linkId = string(data.get("paymentLinkId"));
        if (!isProviderCheckoutUrl(checkoutUrl) || !present(linkId)) {
            throw new IllegalStateException("PayOS did not return a valid checkout URL and payment-link ID.");
        }
        return new Checkout(checkoutUrl, linkId);
    }

    private boolean isSignedSuccess(PayosApiResponse response) {
        return response != null && "00".equals(response.code()) && response.data() != null
                && PayosSignature.isValid(response.signature(),
                        PayosSignature.createDataSignature(response.data(), properties.getChecksumKey()));
    }

    private boolean isProviderCheckoutUrl(String value) {
        if (!present(value)) return false;
        try {
            URI uri = URI.create(value);
            return "https".equalsIgnoreCase(uri.getScheme()) && CHECKOUT_HOST.equalsIgnoreCase(uri.getHost());
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    private void requireConfigured() {
        if (!isConfigured()) {
            throw new IllegalStateException("Configure PAYOS_CLIENT_ID, PAYOS_API_KEY, PAYOS_CHECKSUM_KEY and public HTTPS return, cancel and webhook URLs before checkout.");
        }
    }

    private static long number(Object value) {
        if (value instanceof Number number) return number.longValue();
        if (value instanceof String string) return Long.parseLong(string);
        return Long.MIN_VALUE;
    }

    private static String string(Object value) { return value == null ? null : String.valueOf(value); }
    private static boolean present(String value) { return value != null && !value.isBlank(); }

    public record Checkout(String checkoutUrl, String paymentLinkId) { }
}
