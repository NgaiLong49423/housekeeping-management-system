package com.housekeeping.management.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PayosPaymentServiceTest {
    private PaymentAttemptService attempts;
    private PayosGatewayClient gateway;
    private PayosPaymentService service;

    @BeforeEach
    void setUp() {
        attempts = mock(PaymentAttemptService.class);
        gateway = mock(PayosGatewayClient.class);
        service = new PayosPaymentService(attempts, gateway);
    }

    @Test
    void missingCredentialsOrPublicHttpsCallbacksDoNotCreatePaymentAttempt() {
        when(gateway.isConfigured()).thenReturn(false);

        assertThatThrownBy(() -> service.checkout(42L, 7L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Configure PayOS credentials and public HTTPS callback URLs before starting checkout.");

        verifyNoInteractions(attempts);
        verify(gateway).isConfigured();
        verify(gateway, never()).createCheckout(anyLong(), anyLong());
    }
}
