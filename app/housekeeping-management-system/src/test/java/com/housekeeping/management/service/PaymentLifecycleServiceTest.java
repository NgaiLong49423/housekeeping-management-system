package com.housekeeping.management.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import com.housekeeping.management.domain.PaymentMethod;
import com.housekeeping.management.domain.PaymentStatus;
import com.housekeeping.management.domain.entity.Payment;
import com.housekeeping.management.repository.PaymentAttemptRepository;
import com.housekeeping.management.repository.PaymentRepository;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PaymentLifecycleServiceTest {
    private PaymentRepository payments;
    private PaymentAttemptRepository attempts;
    private PaymentLifecycleService service;

    @BeforeEach
    void setUp() {
        payments = mock(PaymentRepository.class);
        attempts = mock(PaymentAttemptRepository.class);
        service = new PaymentLifecycleService(payments, attempts);
    }

    @Test
    void adminCannotManuallyMarkMomoPaymentPaid() {
        Payment payment = unpaid(PaymentMethod.MOMO);
        when(payments.findByBookingIdForUpdate(42L)).thenReturn(Optional.of(payment));

        assertThatThrownBy(() -> service.confirmReceivedByAdmin(42L, true, OffsetDateTime.now()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("MoMo payments are confirmed automatically after a verified provider notification");
        assertThat(payment.getPaymentStatus()).isEqualTo(PaymentStatus.UNPAID);
        verify(payments, never()).saveAndFlush(any());
    }

    @Test
    void adminCanConfirmManualPaymentAfterReceiptVerification() {
        Payment payment = unpaid(PaymentMethod.BANK_TRANSFER);
        when(payments.findByBookingIdForUpdate(42L)).thenReturn(Optional.of(payment));
        when(payments.saveAndFlush(payment)).thenReturn(payment);
        OffsetDateTime verifiedAt = OffsetDateTime.parse("2026-10-23T12:00:00Z");

        Payment updated = service.confirmReceivedByAdmin(42L, true, verifiedAt);

        assertThat(updated.getPaymentStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(updated.getVerifiedAt()).isEqualTo(verifiedAt.withOffsetSameInstant(java.time.ZoneOffset.ofHours(7)));
    }

    @Test
    void momoMustUseGatewayCheckoutRatherThanManualMethodSelection() {
        assertThatThrownBy(() -> service.chooseMethod(42L, PaymentMethod.MOMO))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Use the MoMo checkout flow for online payments");
        verifyNoInteractions(payments, attempts);
    }

    private Payment unpaid(PaymentMethod method) {
        Payment payment = new Payment();
        payment.setPaymentStatus(PaymentStatus.UNPAID);
        payment.setPaymentMethod(method);
        return payment;
    }
}
