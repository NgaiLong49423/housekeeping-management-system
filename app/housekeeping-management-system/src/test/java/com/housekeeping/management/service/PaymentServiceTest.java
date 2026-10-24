package com.housekeeping.management.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import com.housekeeping.management.repository.BookingPersistenceRepository;
import com.housekeeping.management.repository.PaymentPersistenceRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

class PaymentServiceTest {
    private BookingPersistenceRepository bookings;
    private PaymentPersistenceRepository payments;
    private PaymentService service;

    @BeforeEach
    void setUp() {
        bookings = mock(BookingPersistenceRepository.class);
        payments = mock(PaymentPersistenceRepository.class);
        service = new PaymentService(bookings, payments);
    }

    @Test
    void onlineCheckoutCreatesPaymentRowAndReusesPendingOrderAfterRetry() {
        when(bookings.lockBooking(42L)).thenReturn(Optional.of(booking(7L, 11L, "AWAITING_PAYMENT")));
        when(bookings.bookingAmount(42L)).thenReturn(new BigDecimal("150000.00"));
        when(payments.findForBookingUpdate(42L)).thenReturn(List.of()).thenReturn(List.of(
                payment(19L, 42L, "150000.00", "ONLINE", "PENDING", "1001")));
        when(payments.nextPayosOrderCode()).thenReturn(1001L);

        assertThat(service.prepareOnlineCheckout(42L, 7L)).isEqualTo(new PaymentService.OnlineCheckout(1001L, 150000L, false));
        verify(payments).insertPendingOnline(eq(42L), eq(new BigDecimal("150000.00")), eq(1001L), any(LocalDateTime.class));
        assertThat(service.prepareOnlineCheckout(42L, 7L)).isEqualTo(new PaymentService.OnlineCheckout(1001L, 150000L, true));
        verify(payments, times(1)).nextPayosOrderCode();
    }

    @Test
    void onlineCheckoutRequiresOwnerAndAwaitingPaymentState() {
        when(bookings.lockBooking(42L)).thenReturn(Optional.of(booking(7L, 11L, "AWAITING_PAYMENT")))
                .thenReturn(Optional.of(booking(7L, 11L, "CONFIRMED")));
        assertThatThrownBy(() -> service.prepareOnlineCheckout(42L, 8L)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.prepareOnlineCheckout(42L, 7L)).isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(payments);
    }

    @Test
    void assignedStaffCashConfirmationCreatesSuccessAndCompletesBookingAtomically() {
        when(bookings.lockBooking(42L)).thenReturn(Optional.of(booking(7L, 11L, "AWAITING_PAYMENT")));
        when(bookings.bookingAmount(42L)).thenReturn(new BigDecimal("150000.00"));
        when(payments.findForBookingUpdate(42L)).thenReturn(List.of(
                payment(19L, 42L, "150000.00", "ONLINE", "PENDING", "1001")));
        when(payments.completeBooking(42L)).thenReturn(1);
        service.confirmCashReceived(42L, 11L);
        verify(payments).failPendingOnlineAttempts(42L);
        verify(payments).insertCashSuccess(eq(42L), eq(new BigDecimal("150000.00")), any(LocalDateTime.class));
        verify(payments).completeBooking(42L);
    }

    @Test
    void cashConfirmationRequiresAssignedStaffAndAwaitingPayment() {
        when(bookings.lockBooking(42L)).thenReturn(Optional.of(booking(7L, 11L, "AWAITING_PAYMENT")))
                .thenReturn(Optional.of(booking(7L, 11L, "COMPLETED")));
        assertThatThrownBy(() -> service.confirmCashReceived(42L, 12L)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.confirmCashReceived(42L, 11L)).isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(payments);
    }

    @Test
    void verifiedOnlinePaymentCompletesBooking() {
        var pending = payment(19L, 42L, "150000.00", "ONLINE", "PENDING", "1001");
        when(payments.findByOrderCode(1001L)).thenReturn(Optional.of(pending));
        when(payments.findByOrderCodeForUpdate(1001L)).thenReturn(Optional.of(pending));
        when(bookings.lockBooking(42L)).thenReturn(Optional.of(booking(7L, 11L, "AWAITING_PAYMENT")));
        when(payments.hasSuccessfulPayment(42L)).thenReturn(false);
        when(payments.markSuccess(eq(19L), any(LocalDateTime.class))).thenReturn(1);
        when(payments.completeBooking(42L)).thenReturn(1);
        service.confirmOnlinePayment(1001L, new BigDecimal("150000"));
        verify(payments).markSuccess(eq(19L), any(LocalDateTime.class));
        verify(payments).completeBooking(42L);
    }

    @Test
    void amountMismatchDoesNotCompleteBooking() {
        var pending = payment(19L, 42L, "150000.00", "ONLINE", "PENDING", "1001");
        when(payments.findByOrderCode(1001L)).thenReturn(Optional.of(pending));
        when(bookings.lockBooking(42L)).thenReturn(Optional.of(booking(7L, 11L, "AWAITING_PAYMENT")));
        when(payments.findByOrderCodeForUpdate(1001L)).thenReturn(Optional.of(pending));
        assertThatThrownBy(() -> service.confirmOnlinePayment(1001L, new BigDecimal("150001")))
                .isInstanceOf(IllegalArgumentException.class);
        verify(payments, never()).completeBooking(anyLong());
    }

    private BookingPersistenceRepository.BookingState booking(Long customerId, Long staffId, String status) {
        return new BookingPersistenceRepository.BookingState(42L, customerId, staffId, 9L,
                LocalDateTime.now(), LocalDateTime.now().plusHours(1), status);
    }

    private PaymentPersistenceRepository.PaymentState payment(Long paymentId, Long bookingId, String amount,
            String method, String status, String transactionCode) {
        return new PaymentPersistenceRepository.PaymentState(paymentId, bookingId, new BigDecimal(amount),
                method, status, transactionCode);
    }
}
