package com.housekeeping.management.service;

import org.springframework.stereotype.Service;

@Service
public class PayosPaymentService {
    private final PaymentAttemptService attempts;
    private final PayosGatewayClient gateway;

    public PayosPaymentService(PaymentAttemptService attempts, PayosGatewayClient gateway) {
        this.attempts = attempts;
        this.gateway = gateway;
    }

    public String checkout(Long bookingId, Long customerId) {
        if (!gateway.isConfigured()) {
            throw new IllegalStateException("Configure PayOS credentials and public HTTPS callback URLs before starting checkout.");
        }
        PaymentAttemptService.AttemptCheckout attempt = attempts.prepare(bookingId, customerId);
        if (attempt.checkoutUrl() != null) return attempt.checkoutUrl();

        PayosGatewayClient.Checkout checkout = attempt.needsRecovery()
                ? gateway.recoverCheckout(attempt.orderCode(), attempt.amount()) : null;
        if (checkout == null) checkout = gateway.createCheckout(attempt.orderCode(), attempt.amount());
        attempts.storeCheckout(attempt.attemptId(), checkout.checkoutUrl(), checkout.paymentLinkId());
        return checkout.checkoutUrl();
    }

    public void receiveWebhook(PayosWebhookRequest webhook) {
        attempts.processWebhook(webhook, gateway.verifyWebhook(webhook));
    }
}
