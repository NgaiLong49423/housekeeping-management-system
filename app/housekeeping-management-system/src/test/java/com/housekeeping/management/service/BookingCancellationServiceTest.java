package com.housekeeping.management.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.housekeeping.management.repository.BookingPersistenceRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class BookingCancellationServiceTest {
    private static final LocalDateTime START = LocalDateTime.parse("2026-11-01T12:00:00");

    @Test
    void acceptsCancellationAtExactTwoHourBoundary() {
        assertThat(BookingCancellationService.canCustomerCancel("CONFIRMED", START, START.minusHours(2), null)).isTrue();
    }

    @Test
    void rejectsCancellationInsideTwoHourWindowAndNonConfirmedBooking() {
        assertThat(BookingCancellationService.canCustomerCancel("CONFIRMED", START, START.minusMinutes(119), null)).isFalse();
        assertThat(BookingCancellationService.canCustomerCancel("AWAITING_PAYMENT", START, START.minusHours(3), null)).isFalse();
    }

    @Test
    void rejectsCustomerCancellationAfterEarlyWorkHasStarted() {
        assertThat(BookingCancellationService.canCustomerCancel("CONFIRMED", START, START.minusHours(3), START.minusHours(4))).isFalse();
    }

    @Test
    void noShowReportAloneDoesNotGrantFreeCancellationInsideTwoHourWindow() {
        BookingPersistenceRepository bookings = mock(BookingPersistenceRepository.class);
        LocalDateTime now = LocalDateTime.now(ZoneOffset.ofHours(7)).withNano(0);
        LocalDateTime start = now.plusMinutes(30);
        when(bookings.lockBookingWorkflow(42L)).thenReturn(Optional.of(state(start, null, null,
                now.minusMinutes(1), null)));

        assertThatThrownBy(() -> new com.housekeeping.management.service.impl.BookingCancellationServiceImpl(bookings)
                .cancelByCustomer(42L, 3L)).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("2 giờ");
        verify(bookings, never()).cancelByCustomer(any(), any(), any(), any());
    }

    @Test
    void missedAcceptedEtaAllowsFreeCancellationInsideTwoHourWindow() {
        BookingPersistenceRepository bookings = mock(BookingPersistenceRepository.class);
        LocalDateTime now = LocalDateTime.now(ZoneOffset.ofHours(7)).withNano(0);
        LocalDateTime start = now.plusMinutes(30);
        when(bookings.lockBookingWorkflow(42L)).thenReturn(Optional.of(state(start,
                now.minusMinutes(1), "ACCEPTED", null, now.minusMinutes(2))));
        when(bookings.cancelByCustomer(eq(42L), eq(3L), anyString(), any())).thenReturn(1);

        new com.housekeeping.management.service.impl.BookingCancellationServiceImpl(bookings).cancelByCustomer(42L, 3L);

        verify(bookings).cancelByCustomer(eq(42L), eq(3L), contains("ETA"), any());
    }

    private static BookingPersistenceRepository.BookingWorkflowState state(LocalDateTime start,
            LocalDateTime eta, String response, LocalDateTime customerReport, LocalDateTime incidentReportedAt) {
        return new BookingPersistenceRepository.BookingWorkflowState(42L, 3L, 7L, start, start.plusHours(1),
                "CONFIRMED", new BigDecimal("150000"), null, null, incidentReportedAt, null, eta, response,
                null, null, BigDecimal.ZERO, BigDecimal.ZERO, customerReport, null, null, null, null, null, null);
    }

}
