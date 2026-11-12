package com.housekeeping.management.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.housekeeping.management.entity.Booking;
import com.housekeeping.management.enums.BookingStatus;
import com.housekeeping.management.repository.BookingPersistenceRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class CustomerDelayReportServiceTest {
    @Test
    void adminVerifiedNoShowLetsCustomerChooseWaitAndPersistsChoice() {
        BookingPersistenceRepository bookings = mock(BookingPersistenceRepository.class);
        BookingCancellationService cancellation = mock(BookingCancellationService.class);
        LocalDateTime now = LocalDateTime.now(ZoneOffset.ofHours(7)).withNano(0);
        Booking booking = disputedBooking(now);
        booking.setAdminNoShowVerifiedAt(now.minusMinutes(1));
        when(bookings.lockEntity(42L)).thenReturn(Optional.of(booking));
        when(bookings.recordCustomerWaitAfterDelayReview(eq(42L), eq(3L), any())).thenReturn(1);

        new com.housekeeping.management.service.impl.CustomerDelayReportServiceImpl(bookings, cancellation)
                .respondAfterAdminReview(42L, 3L, "WAIT");

        verify(bookings).recordCustomerWaitAfterDelayReview(eq(42L), eq(3L), any());
        verify(cancellation, never()).cancelByCustomer(any(), any());
    }

    @Test
    void adminVerifiedLateArrivalLetsCustomerCancelImmediately() {
        BookingPersistenceRepository bookings = mock(BookingPersistenceRepository.class);
        BookingCancellationService cancellation = mock(BookingCancellationService.class);
        LocalDateTime now = LocalDateTime.now(ZoneOffset.ofHours(7)).withNano(0);
        Booking booking = disputedBooking(now);
        booking.setArrivalVerifiedAt(booking.getStartTime().plusMinutes(5));
        booking.setArrivalVerificationSource("ADMIN");
        when(bookings.lockEntity(42L)).thenReturn(Optional.of(booking));

        new com.housekeeping.management.service.impl.CustomerDelayReportServiceImpl(bookings, cancellation)
                .respondAfterAdminReview(42L, 3L, "CANCEL");

        verify(cancellation).cancelByCustomer(42L, 3L);
        verify(bookings, never()).recordCustomerWaitAfterDelayReview(any(), any(), any());
    }

    @Test
    void noShowReportAloneDoesNotAllowWaitOrCancellation() {
        BookingPersistenceRepository bookings = mock(BookingPersistenceRepository.class);
        LocalDateTime now = LocalDateTime.now(ZoneOffset.ofHours(7)).withNano(0);
        when(bookings.lockEntity(42L)).thenReturn(Optional.of(disputedBooking(now)));
        var service = new com.housekeeping.management.service.impl.CustomerDelayReportServiceImpl(
                bookings, mock(BookingCancellationService.class));

        assertThatThrownBy(() -> service.respondAfterAdminReview(42L, 3L, "WAIT"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("Admin cần xác minh");
        verify(bookings, never()).recordCustomerWaitAfterDelayReview(any(), any(), any());
    }

    @Test
    void customerCanReportAfterStaffClaimsArrivalBeforeWorkStarts() {
        BookingPersistenceRepository bookings = mock(BookingPersistenceRepository.class);
        LocalDateTime start = LocalDateTime.now(ZoneOffset.ofHours(7)).minusMinutes(30).withNano(0);
        var booking = new BookingPersistenceRepository.BookingWorkflowState(42L, 3L, 7L, start,
                start.plusHours(1), "CONFIRMED", new BigDecimal("150000"), start.plusMinutes(10),
                null, null, null, null, null, null, null, BigDecimal.ZERO, BigDecimal.ZERO);
        when(bookings.lockBookingWorkflow(42L)).thenReturn(Optional.of(booking));
        when(bookings.reportCustomerDelay(eq(42L), eq(3L), any())).thenReturn(1);

        new com.housekeeping.management.service.impl.CustomerDelayReportServiceImpl(bookings, mock(BookingCancellationService.class))
                .reportStaffNotArrived(42L, 3L);

        verify(bookings).reportCustomerDelay(eq(42L), eq(3L), any());
    }

    @Test
    void customerCanReportThatStaffClaimedArrivalTimeWasIncorrect() {
        BookingPersistenceRepository bookings = mock(BookingPersistenceRepository.class);
        LocalDateTime start = LocalDateTime.now(ZoneOffset.ofHours(7)).minusMinutes(30).withNano(0);
        var booking = new BookingPersistenceRepository.BookingWorkflowState(42L, 3L, 7L, start,
                start.plusHours(1), "CONFIRMED", new BigDecimal("150000"), start.plusMinutes(5),
                null, null, null, null, null, null, null, BigDecimal.ZERO, BigDecimal.ZERO,
                null, start.plusMinutes(5), 3L, "CUSTOMER", null, start.plusMinutes(6), null);
        when(bookings.lockBookingWorkflow(42L)).thenReturn(Optional.of(booking));
        when(bookings.reportCustomerDelay(eq(42L), eq(3L), any())).thenReturn(1);

        new com.housekeeping.management.service.impl.CustomerDelayReportServiceImpl(bookings, mock(BookingCancellationService.class))
                .reportStaffNotArrived(42L, 3L);

        verify(bookings).reportCustomerDelay(eq(42L), eq(3L), any());
    }

    @Test
    void rejectsWrongCustomerAndCompletedWork() {
        BookingPersistenceRepository bookings = mock(BookingPersistenceRepository.class);
        LocalDateTime start = LocalDateTime.now(ZoneOffset.ofHours(7)).minusMinutes(30).withNano(0);
        var wrongOwner = new BookingPersistenceRepository.BookingWorkflowState(42L, 4L, 7L, start,
                start.plusHours(1), "CONFIRMED", new BigDecimal("150000"), null, null, null, null, null, null,
                null, null, BigDecimal.ZERO, BigDecimal.ZERO);
        when(bookings.lockBookingWorkflow(42L)).thenReturn(Optional.of(wrongOwner));
        var service = new com.housekeeping.management.service.impl.CustomerDelayReportServiceImpl(bookings, mock(BookingCancellationService.class));
        assertThatThrownBy(() -> service.reportStaffNotArrived(42L, 3L))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);

        var completed = new BookingPersistenceRepository.BookingWorkflowState(42L, 3L, 7L, start,
                start.plusHours(1), "CONFIRMED", new BigDecimal("150000"), null, start.plusMinutes(1), null,
                null, null, null, null, null, BigDecimal.ZERO, BigDecimal.ZERO,
                null, null, null, null, null, null, start.plusMinutes(20));
        when(bookings.lockBookingWorkflow(42L)).thenReturn(Optional.of(completed));
        assertThatThrownBy(() -> service.reportStaffNotArrived(42L, 3L))
                .isInstanceOf(IllegalStateException.class);
    }

    private static Booking disputedBooking(LocalDateTime now) {
        Booking booking = new Booking();
        booking.setBookingId(42L);
        booking.setCustomerId(3L);
        booking.setStaffId(7L);
        booking.setStatus(BookingStatus.CONFIRMED);
        booking.setStartTime(now.minusMinutes(5));
        booking.setEndTime(now.plusHours(1));
        booking.setTotalAmount(new BigDecimal("150000"));
        booking.setCustomerDelayReportedAt(now.minusMinutes(2));
        return booking;
    }
}
