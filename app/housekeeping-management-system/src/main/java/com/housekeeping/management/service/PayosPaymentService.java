package com.housekeeping.management.service;

import java.math.BigDecimal;
import org.springframework.stereotype.Service;

@Service
public class PayosPaymentService {
    private final PaymentService payments;
    private final PayosGatewayClient gateway;

    public PayosPaymentService(PaymentService payments, PayosGatewayClient gateway) {
        this.payments = payments;
        this.gateway = gateway;
    }

    public String checkout(Long bookingId, Long customerId) {
        if (!gateway.isConfigured()) {
            throw new IllegalStateException("Configure PayOS credentials and public HTTPS callback URLs before starting checkout.");
        }
        PaymentService.OnlineCheckout attempt = payments.prepareOnlineCheckout(bookingId, customerId);
        PayosGatewayClient.Checkout checkout = attempt.recoverExisting()
                ? gateway.recoverCheckout(attempt.orderCode(), attempt.amount()) : null;
        if (checkout == null) checkout = gateway.createCheckout(attempt.orderCode(), attempt.amount());
        return checkout.checkoutUrl();
    }

    public void receiveWebhook(PayosWebhookRequest webhook) {
        if (!gateway.verifyWebhook(webhook)) {
            throw new IllegalArgumentException("PayOS webhook signature is invalid.");
        }
        if (webhook == null || webhook.data() == null
                || !(webhook.data().get("orderCode") instanceof Number orderCode)
                || !(webhook.data().get("amount") instanceof Number amount)
                || !"VND".equals(webhook.data().get("currency"))) {
            throw new IllegalArgumentException("PayOS webhook is missing required transaction data.");
        }
        long exactOrderCode = exactLong(orderCode, "orderCode");
        BigDecimal exactAmount = exactAmount(amount);
        if (Boolean.TRUE.equals(webhook.success()) && "00".equals(webhook.code())
                && "00".equals(webhook.data().get("code"))) {
            payments.confirmOnlinePayment(exactOrderCode, exactAmount);
        } else {
            payments.failOnlinePayment(exactOrderCode, exactAmount);
        }
    }

    private static long exactLong(Number value, String fieldName) {
        try {
            return new BigDecimal(value.toString()).longValueExact();
        } catch (ArithmeticException | NumberFormatException invalidValue) {
            throw new IllegalArgumentException("PayOS webhook contains an invalid " + fieldName + ".", invalidValue);
        }
    }

    private static BigDecimal exactAmount(Number value) {
        try {
            return new BigDecimal(value.toString());
        } catch (NumberFormatException invalidAmount) {
            throw new IllegalArgumentException("PayOS webhook contains an invalid amount.", invalidAmount);
        }
    }
}
