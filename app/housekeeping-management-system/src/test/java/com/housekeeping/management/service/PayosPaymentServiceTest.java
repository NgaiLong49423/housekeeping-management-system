package com.housekeeping.management.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PayosPaymentServiceTest {
    private PaymentService payments;
    private PayosGatewayClient gateway;
    private PayosPaymentService service;

    @BeforeEach
    void setUp() {
        payments = mock(PaymentService.class);
        gateway = mock(PayosGatewayClient.class);
        service = new PayosPaymentService(payments, gateway);
    }

    @Test
    void missingCredentialsOrPublicHttpsCallbacksDoNotCreatePaymentAttempt() {
        when(gateway.isConfigured()).thenReturn(false);

        assertThatThrownBy(() -> service.checkout(42L, 7L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Configure PayOS credentials and public HTTPS callback URLs before starting checkout.");

        verifyNoInteractions(payments);
        verify(gateway).isConfigured();
        verify(gateway, never()).createCheckout(anyLong(), anyLong());
    }

    @Test
    void createsOrRecoversCheckoutForPersistedPaymentAttempt() {
        when(gateway.isConfigured()).thenReturn(true);
        when(payments.prepareOnlineCheckout(42L, 7L)).thenReturn(new PaymentService.OnlineCheckout(1001L, 150000L, false));
        when(gateway.createCheckout(1001L, 150000L))
                .thenReturn(new PayosGatewayClient.Checkout("https://pay.payos.vn/web/link", "link"));
        org.assertj.core.api.Assertions.assertThat(service.checkout(42L, 7L)).isEqualTo("https://pay.payos.vn/web/link");

        when(payments.prepareOnlineCheckout(42L, 7L)).thenReturn(new PaymentService.OnlineCheckout(1001L, 150000L, true));
        when(gateway.recoverCheckout(1001L, 150000L))
                .thenReturn(new PayosGatewayClient.Checkout("https://pay.payos.vn/web/recovered", "link"));
        org.assertj.core.api.Assertions.assertThat(service.checkout(42L, 7L)).isEqualTo("https://pay.payos.vn/web/recovered");
        verify(gateway).createCheckout(1001L, 150000L);
        verify(gateway).recoverCheckout(1001L, 150000L);
    }

    @Test
    void verifiedWebhookConfirmsSuccessAndFailureOnPersistedPaymentRows() {
        when(gateway.verifyWebhook(any())).thenReturn(true);
        service.receiveWebhook(webhook(true, "00"));
        verify(payments).confirmOnlinePayment(1001L, java.math.BigDecimal.valueOf(150000));

        service.receiveWebhook(webhook(false, "01"));
        verify(payments).failOnlinePayment(1001L, java.math.BigDecimal.valueOf(150000));
    }

    @Test
    void webhookPreservesFractionalAmountInsteadOfTruncatingIt() {
        when(gateway.verifyWebhook(any())).thenReturn(true);
        service.receiveWebhook(webhook(true, "00", 1001L, new BigDecimal("150000.50")));

        verify(payments).confirmOnlinePayment(1001L, new BigDecimal("150000.50"));
    }

    @Test
    void webhookRejectsOrderCodeOutsideLongRange() {
        when(gateway.verifyWebhook(any())).thenReturn(true);

        assertThatThrownBy(() -> service.receiveWebhook(webhook(true, "00",
                new BigInteger("9223372036854775808"), 150000L)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("orderCode");

        verifyNoInteractions(payments);
    }

    private PayosWebhookRequest webhook(boolean success, String code) {
        return webhook(success, code, 1001L, 150000L);
    }

    private PayosWebhookRequest webhook(boolean success, String code, Number orderCode, Number amount) {
        return new PayosWebhookRequest(code, "result", success,
                Map.of("orderCode", orderCode, "amount", amount, "currency", "VND", "code", code), "signature");
    }
}
