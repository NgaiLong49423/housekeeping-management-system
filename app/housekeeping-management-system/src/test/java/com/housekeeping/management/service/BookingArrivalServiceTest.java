package com.housekeeping.management.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.housekeeping.management.repository.BookingPersistenceRepository;
import com.housekeeping.management.domain.BookingDelayCompensation.Tier;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class BookingArrivalServiceTest {
    @Test
    void recordsAssignedStaffArrivalAndAppliesTenPercentAtSixteenMinutesLate() {
        BookingPersistenceRepository bookings = mock(BookingPersistenceRepository.class);
        LocalDateTime start = LocalDateTime.now(ZoneOffset.ofHours(7)).minusMinutes(16).withNano(0);
        when(bookings.lockBookingWorkflow(42L)).thenReturn(Optional.of(workflow(7L, start)));
        when(bookings.confirmStaffArrival(eq(42L), eq(7L), any(), any(Integer.class), any(), any())).thenReturn(1);

        new com.housekeeping.management.service.impl.BookingArrivalServiceImpl(bookings, settings()).confirmStaffArrival(42L, 7L);

        ArgumentCaptor<LocalDateTime> recordedAt = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<Integer> delayedMinutes = ArgumentCaptor.forClass(Integer.class);
        ArgumentCaptor<BigDecimal> percent = ArgumentCaptor.forClass(BigDecimal.class);
        ArgumentCaptor<BigDecimal> discount = ArgumentCaptor.forClass(BigDecimal.class);
        verify(bookings).confirmStaffArrival(eq(42L), eq(7L), recordedAt.capture(),
                delayedMinutes.capture(), percent.capture(), discount.capture());
        assertThat(delayedMinutes.getValue()).isGreaterThanOrEqualTo(16);
        assertThat(percent.getValue()).isEqualByComparingTo("10");
        assertThat(discount.getValue()).isEqualByComparingTo("15000.00");
    }

    @Test
    void rejectsArrivalWhenRepositoryConditionsDoNotMatch() {
        BookingPersistenceRepository bookings = mock(BookingPersistenceRepository.class);
        LocalDateTime start = LocalDateTime.now(ZoneOffset.ofHours(7)).plusDays(1);
        when(bookings.lockBookingWorkflow(42L)).thenReturn(Optional.of(workflow(7L, start)));
        when(bookings.confirmStaffArrival(eq(42L), eq(8L), any(), any(Integer.class), any(), any())).thenReturn(0);

        assertThatThrownBy(() -> new com.housekeeping.management.service.impl.BookingArrivalServiceImpl(bookings, settings()).confirmStaffArrival(42L, 8L))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void assignedStaffMayConfirmArrivalBeforeScheduledStart() {
        BookingPersistenceRepository bookings = mock(BookingPersistenceRepository.class);
        LocalDateTime start = LocalDateTime.now(ZoneOffset.ofHours(7)).plusDays(1);
        when(bookings.lockBookingWorkflow(42L)).thenReturn(Optional.of(workflow(7L, start)));
        when(bookings.confirmStaffArrival(eq(42L), eq(7L), any(), any(Integer.class), any(), any())).thenReturn(1);

        new com.housekeeping.management.service.impl.BookingArrivalServiceImpl(bookings, settings()).confirmStaffArrival(42L, 7L);

        verify(bookings).confirmStaffArrival(eq(42L), eq(7L), any(), any(Integer.class), any(), any());
    }

    private BookingPersistenceRepository.BookingWorkflowState workflow(Long staffId, LocalDateTime start) {
        return new BookingPersistenceRepository.BookingWorkflowState(42L, 3L, staffId, start, start.plusHours(1),
                "CONFIRMED", new BigDecimal("150000.00"), null, null, null, null, null, null, null, null,
                BigDecimal.ZERO, BigDecimal.ZERO);
    }

    private BookingDelayCompensationSettingsService settings() {
        BookingDelayCompensationSettingsService settings = mock(BookingDelayCompensationSettingsService.class);
        when(settings.get()).thenReturn(List.of(new Tier(1, 15, 5), new Tier(16, 30, 10), new Tier(31, null, 20)));
        return settings;
    }
}
