package com.housekeeping.management.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.housekeeping.management.repository.BookingPersistenceRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class CustomerDelayReportServiceTest {
    @Test
    void customerCanReportAfterStaffClaimsArrivalAndWorkStarts() {
        BookingPersistenceRepository bookings = mock(BookingPersistenceRepository.class);
        LocalDateTime start = LocalDateTime.now(ZoneOffset.ofHours(7)).minusMinutes(30).withNano(0);
        var booking = new BookingPersistenceRepository.BookingWorkflowState(42L, 3L, 7L, start,
                start.plusHours(1), "CONFIRMED", new BigDecimal("150000"), start.plusMinutes(10),
                start.plusMinutes(12), null, null, null, null, null, null, BigDecimal.ZERO, BigDecimal.ZERO);
        when(bookings.lockBookingWorkflow(42L)).thenReturn(Optional.of(booking));
        when(bookings.reportCustomerDelay(eq(42L), eq(3L), any())).thenReturn(1);

        new com.housekeeping.management.service.impl.CustomerDelayReportServiceImpl(bookings)
                .reportStaffNotArrived(42L, 3L);

        verify(bookings).reportCustomerDelay(eq(42L), eq(3L), any());
    }

    @Test
    void customerCanDisputeArrivalEvenAfterPreviouslyAcknowledgingStaffArrival() {
        BookingPersistenceRepository bookings = mock(BookingPersistenceRepository.class);
        LocalDateTime start = LocalDateTime.now(ZoneOffset.ofHours(7)).minusMinutes(30).withNano(0);
        var booking = new BookingPersistenceRepository.BookingWorkflowState(42L, 3L, 7L, start,
                start.plusHours(1), "CONFIRMED", new BigDecimal("150000"), start.plusMinutes(5),
                null, null, null, null, null, null, null, BigDecimal.ZERO, BigDecimal.ZERO,
                null, null, null, null, null, start.plusMinutes(6));
        when(bookings.lockBookingWorkflow(42L)).thenReturn(Optional.of(booking));
        when(bookings.reportCustomerDelay(eq(42L), eq(3L), any())).thenReturn(1);

        new com.housekeeping.management.service.impl.CustomerDelayReportServiceImpl(bookings)
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
        var service = new com.housekeeping.management.service.impl.CustomerDelayReportServiceImpl(bookings);
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
}
