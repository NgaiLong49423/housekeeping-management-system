package com.housekeeping.management.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import com.housekeeping.management.domain.PaymentMethod;
import com.housekeeping.management.domain.PaymentStatus;
import com.housekeeping.management.entity.Payment;
import com.housekeeping.management.repository.PaymentPersistenceRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

class PaymentServiceTest {
    private BookingReservationService bookings;
    private PaymentPersistenceRepository payments;
    private PaymentService service;

    @BeforeEach
    void setUp() {
        bookings = mock(BookingReservationService.class);
        payments = mock(PaymentPersistenceRepository.class);
        service = new com.housekeeping.management.service.impl.PaymentServiceImpl(bookings, payments);
    }

    @Test
    void createsJpaPaymentThenUsesIdentityAsOrderCodeSuffixAndReusesPending() {
        LocalDateTime createdAt = LocalDateTime.of(2026, 11, 8, 10, 15, 30);
        when(bookings.lockBookingForPayment(42L)).thenReturn(Optional.of(booking(7L, 11L, "AWAITING_PAYMENT")));
        when(bookings.amountForPayment(42L)).thenReturn(new BigDecimal("150000.00"));
        when(payments.findByBookingIdOrderByCreatedAtDescPaymentIdDesc(42L)).thenReturn(List.of()).thenAnswer(invocation -> {
            Payment payment = new Payment();
            payment.setPaymentId(19L);
            payment.setBookingId(42L);
            payment.setAmount(new BigDecimal("150000.00"));
            payment.setPaymentMethod(PaymentMethod.ONLINE);
            payment.setStatus(PaymentStatus.PENDING);
            payment.setTransactionCode("26110810153000019");
            payment.setCreatedAt(createdAt);
            return List.of(payment);
        });
        when(payments.saveAndFlush(any(Payment.class))).thenAnswer(invocation -> {
            Payment payment = invocation.getArgument(0);
            payment.setPaymentId(19L);
            return payment;
        });

        var created = service.prepareOnlineCheckout(42L, 7L);
        String timestampPrefix = created.createdAt().format(java.time.format.DateTimeFormatter.ofPattern("yyMMddHHmmss"));
        assertThat(created.orderCode()).isEqualTo(Long.parseLong(timestampPrefix + "00019"));
        assertThat(created.amount()).isEqualTo(150000L);
        assertThat(created.recoverExisting()).isFalse();
        verify(payments).saveAndFlush(argThat(payment -> payment.isSimulated()
                && payment.getStatus() == PaymentStatus.PENDING
                && payment.getPaymentMethod() == PaymentMethod.ONLINE));
        verify(payments).save(argThat(payment -> payment.getTransactionCode().endsWith("00019")));

        var retry = service.prepareOnlineCheckout(42L, 7L);
        assertThat(retry.recoverExisting()).isTrue();
        assertThat(retry.orderCode()).isEqualTo(26110810153000019L);
    }

    @Test
    void checkoutRequiresOwnerAndAwaitingPaymentState() {
        when(bookings.lockBookingForPayment(42L)).thenReturn(Optional.of(booking(7L, 11L, "AWAITING_PAYMENT")))
                .thenReturn(Optional.of(booking(7L, 11L, "CONFIRMED")));
        assertThatThrownBy(() -> service.prepareOnlineCheckout(42L, 8L)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.prepareOnlineCheckout(42L, 7L)).isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(payments);
    }

    @Test
    void cashConfirmationRequiresAssignedStaffAndCompletesBooking() {
        when(bookings.lockBookingForPayment(42L)).thenReturn(Optional.of(booking(7L, 11L, "AWAITING_PAYMENT")));
        when(payments.findByBookingIdOrderByCreatedAtDescPaymentIdDesc(42L)).thenReturn(List.of());
        when(bookings.amountForPayment(42L)).thenReturn(new BigDecimal("150000"));
        when(bookings.completeBookingAfterPayment(42L)).thenReturn(1);

        assertThatThrownBy(() -> service.confirmCashReceived(42L, 12L)).isInstanceOf(AccessDeniedException.class);
        service.confirmCashReceived(42L, 11L);
        verify(payments).failPendingOnlineAttempts(42L);
        verify(payments).save(argThat(payment -> payment.getPaymentMethod() == PaymentMethod.CASH
                && payment.getStatus() == PaymentStatus.SUCCESS));
        verify(bookings).completeBookingAfterPayment(42L);
    }

    private BookingReservationService.BookingPaymentState booking(Long customerId, Long staffId, String status) {
        return new BookingReservationService.BookingPaymentState(customerId, staffId, status);
    }
}
