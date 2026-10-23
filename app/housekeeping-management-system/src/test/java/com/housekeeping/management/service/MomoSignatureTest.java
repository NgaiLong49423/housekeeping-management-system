package com.housekeeping.management.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class MomoSignatureTest {
    @Test
    void webhookSignatureAcceptsExactPayloadAndRejectsTampering() {
        MomoIpnRequest ipn = new MomoIpnRequest("PARTNER", "request-1", "order-1", 150000L,
                "Payment test", "momo_wallet", "", 123456789L, 0, "Successful.", "qr",
                1792752000000L, "", null);
        String signature = MomoSignature.createWebhook(ipn, "access-key", "secret-key");

        assertThat(MomoSignature.isValid(signature, MomoSignature.createWebhook(ipn, "access-key", "secret-key"))).isTrue();
        MomoIpnRequest tampered = new MomoIpnRequest("PARTNER", "request-1", "order-1", 150001L,
                "Payment test", "momo_wallet", "", 123456789L, 0, "Successful.", "qr",
                1792752000000L, "", signature);
        assertThat(MomoSignature.isValid(signature, MomoSignature.createWebhook(tampered, "access-key", "secret-key"))).isFalse();
    }
}
