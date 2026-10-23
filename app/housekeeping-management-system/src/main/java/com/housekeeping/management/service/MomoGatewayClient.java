package com.housekeeping.management.service;

import com.housekeeping.management.config.MomoPaymentProperties;
import java.net.URI;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Component
public class MomoGatewayClient {
    private final RestClient restClient;
    private final MomoPaymentProperties properties;

    public MomoGatewayClient(MomoPaymentProperties properties) {
        HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(30)).build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofSeconds(30));
        this.restClient = RestClient.builder().baseUrl(properties.getBaseUrl()).requestFactory(requestFactory).build();
        this.properties = properties;
    }

    public String createCheckout(String requestId, String orderId, long amount) {
        if (!properties.isConfigured()) {
            throw new IllegalStateException("MoMo is not configured. Set MOMO_PARTNER_CODE, MOMO_ACCESS_KEY, MOMO_SECRET_KEY, MOMO_REDIRECT_URL and MOMO_IPN_URL.");
        }
        MomoCreatePaymentRequest unsigned = new MomoCreatePaymentRequest(
                properties.getPartnerCode(), "Gon Nha", "Gon Nha", requestId, amount, orderId,
                "Thanh toan booking " + orderId, properties.getRedirectUrl(), properties.getIpnUrl(),
                "captureWallet", "", true, "vi", "");
        MomoCreatePaymentRequest signed = new MomoCreatePaymentRequest(
                unsigned.partnerCode(), unsigned.partnerName(), unsigned.storeId(), unsigned.requestId(),
                unsigned.amount(), unsigned.orderId(), unsigned.orderInfo(), unsigned.redirectUrl(),
                unsigned.ipnUrl(), unsigned.requestType(), unsigned.extraData(), unsigned.autoCapture(),
                unsigned.lang(), MomoSignature.createPaymentRequest(unsigned, properties.getAccessKey(), properties.getSecretKey()));

        MomoCreatePaymentResponse response = restClient.post()
                .uri("/v2/gateway/api/create")
                .contentType(MediaType.APPLICATION_JSON)
                .body(signed)
                .retrieve()
                .body(MomoCreatePaymentResponse.class);
        if (response == null || response.resultCode() == null || response.resultCode() != 0
                || !properties.getPartnerCode().equals(response.partnerCode())
                || !requestId.equals(response.requestId()) || !orderId.equals(response.orderId())
                || response.amount() == null || response.amount() != amount
                || !isProviderCheckoutUrl(response.payUrl())) {
            throw new IllegalStateException("MoMo did not return a valid checkout response.");
        }
        return response.payUrl();
    }

    public boolean verifyIpn(MomoIpnRequest ipn) {
        if (!properties.isConfigured() || ipn == null || ipn.signature() == null) return false;
        return MomoSignature.isValid(ipn.signature(),
                MomoSignature.createWebhook(ipn, properties.getAccessKey(), properties.getSecretKey()));
    }

    public boolean isConfigured() { return properties.isConfigured(); }

    public String expectedPartnerCode() { return properties.getPartnerCode(); }

    private boolean isProviderCheckoutUrl(String value) {
        if (value == null) return false;
        try {
            URI checkout = URI.create(value);
            URI provider = URI.create(properties.getBaseUrl());
            return "https".equalsIgnoreCase(checkout.getScheme())
                    && checkout.getHost() != null && checkout.getHost().equalsIgnoreCase(provider.getHost());
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }
}
