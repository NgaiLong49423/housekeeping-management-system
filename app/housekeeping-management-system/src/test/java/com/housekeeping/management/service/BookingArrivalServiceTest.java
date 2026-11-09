package com.housekeeping.management.service;

import static org.assertj.core.api.Assertions.assertThat;
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
import org.mockito.ArgumentCaptor;

class BookingArrivalServiceTest {
    @Test
    void recordsAssignedStaffArrivalAndAppliesTenPercentAtSixteenMinutesLate() {
        BookingPersistenceRepository bookings = mock(BookingPersistenceRepository.class);
        LocalDateTime start = LocalDateTime.now(ZoneOffset.ofHours(7)).minusMinutes(16).withNano(0);
        when(bookings.lockBookingWorkflow(42L)).thenReturn(Optional.of(workflow(7L, start)));
        when(bookings.confirmStaffArrival(eq(42L), eq(7L), any(), any(), any(Integer.class), any(), any())).thenReturn(1);

        new BookingArrivalService(bookings, 5).confirmStaffArrival(42L, 7L);

        ArgumentCaptor<LocalDateTime> recordedAt = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<LocalDateTime> eligibleThrough = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<Integer> delayedMinutes = ArgumentCaptor.forClass(Integer.class);
        ArgumentCaptor<BigDecimal> percent = ArgumentCaptor.forClass(BigDecimal.class);
        ArgumentCaptor<BigDecimal> discount = ArgumentCaptor.forClass(BigDecimal.class);
        verify(bookings).confirmStaffArrival(eq(42L), eq(7L), recordedAt.capture(), eligibleThrough.capture(),
                delayedMinutes.capture(), percent.capture(), discount.capture());
        assertThat(eligibleThrough.getValue()).isEqualTo(recordedAt.getValue().plusMinutes(5));
        assertThat(delayedMinutes.getValue()).isGreaterThanOrEqualTo(16);
        assertThat(percent.getValue()).isEqualByComparingTo("10");
        assertThat(discount.getValue()).isEqualByComparingTo("15000.00");
    }

    @Test
    void rejectsArrivalWhenRepositoryConditionsDoNotMatch() {
        BookingPersistenceRepository bookings = mock(BookingPersistenceRepository.class);
        LocalDateTime start = LocalDateTime.now(ZoneOffset.ofHours(7)).plusDays(1);
        when(bookings.lockBookingWorkflow(42L)).thenReturn(Optional.of(workflow(7L, start)));
        when(bookings.confirmStaffArrival(eq(42L), eq(8L), any(), any(), any(Integer.class), any(), any())).thenReturn(0);

        assertThatThrownBy(() -> new BookingArrivalService(bookings, 5).confirmStaffArrival(42L, 8L))
                .isInstanceOf(IllegalStateException.class);
    }

    private BookingPersistenceRepository.BookingWorkflowState workflow(Long staffId, LocalDateTime start) {
        return new BookingPersistenceRepository.BookingWorkflowState(42L, 3L, staffId, start, start.plusHours(1),
                "CONFIRMED", new BigDecimal("150000.00"), null, null, null, null, null, null, null, null,
                BigDecimal.ZERO, BigDecimal.ZERO);
    }
}
