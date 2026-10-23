package com.housekeeping.management.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MomoPaymentServiceTest {
    private PaymentAttemptService attempts;
    private MomoGatewayClient gateway;
    private MomoPaymentService service;

    @BeforeEach
    void setUp() {
        attempts = mock(PaymentAttemptService.class);
        gateway = mock(MomoGatewayClient.class);
        service = new MomoPaymentService(attempts, gateway);
    }

    @Test
    void missingCredentialsOrPublicHttpsCallbacksDoNotCreatePaymentAttempt() {
        when(gateway.isConfigured()).thenReturn(false);

        assertThatThrownBy(() -> service.checkout(42L, 7L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Configure MoMo Test credentials and public HTTPS callback URLs before starting checkout.");

        verifyNoInteractions(attempts);
        verify(gateway).isConfigured();
        verify(gateway, never()).createCheckout(anyString(), anyString(), anyLong());
    }
}
