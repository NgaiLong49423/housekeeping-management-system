package com.housekeeping.management.service;

import org.springframework.stereotype.Service;

@Service
public class MomoPaymentService {
    private final PaymentAttemptService attempts;
    private final MomoGatewayClient gateway;

    public MomoPaymentService(PaymentAttemptService attempts, MomoGatewayClient gateway) {
        this.attempts = attempts;
        this.gateway = gateway;
    }

    public String checkout(Long bookingId, Long customerId) {
        if (!gateway.isConfigured()) {
            throw new IllegalStateException("Configure MoMo Test credentials and public HTTPS callback URLs before starting checkout.");
        }
        PaymentAttemptService.AttemptCheckout attempt = attempts.prepare(bookingId, customerId);
        if (attempt.checkoutUrl() != null) return attempt.checkoutUrl();
        try {
            String checkoutUrl = gateway.createCheckout(attempt.requestId(), attempt.orderId(), attempt.amount());
            attempts.storeCheckoutUrl(attempt.attemptId(), checkoutUrl);
            return checkoutUrl;
        } catch (RuntimeException failure) {
            // Keep the same request/order IDs so a retry is idempotent even if MoMo accepted a timed-out request.
            throw new IllegalStateException("Could not start MoMo checkout. Please retry the same payment shortly.", failure);
        }
    }

    public void receiveIpn(MomoIpnRequest ipn) {
        attempts.processIpn(ipn, gateway.verifyIpn(ipn), gateway.expectedPartnerCode());
    }

    public Long bookingIdForOrder(String orderId) {
        return attempts.bookingIdForOrder(orderId);
    }
}
