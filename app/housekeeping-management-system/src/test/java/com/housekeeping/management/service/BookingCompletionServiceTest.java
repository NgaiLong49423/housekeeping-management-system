package com.housekeeping.management.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.housekeeping.management.repository.BookingPersistenceRepository;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class BookingCompletionServiceTest {
    @Test
    void assignedStaffMovesConfirmedBookingToAwaitingPayment() {
        BookingPersistenceRepository bookings = mock(BookingPersistenceRepository.class);
        BookingCompletionService service = new BookingCompletionService(bookings);
        when(bookings.lockBooking(42L)).thenReturn(Optional.of(new BookingPersistenceRepository.BookingState(
                42L, 3L, 7L, 9L, LocalDateTime.parse("2026-11-02T10:00"),
                LocalDateTime.parse("2026-11-02T11:00"), "CONFIRMED")));
        when(bookings.changeStatus(42L, "CONFIRMED", "AWAITING_PAYMENT")).thenReturn(1);

        service.completeBooking(42L, 7L);

        verify(bookings).changeStatus(42L, "CONFIRMED", "AWAITING_PAYMENT");
    }

    @Test
    void anotherStaffCannotCompleteBooking() {
        BookingPersistenceRepository bookings = mock(BookingPersistenceRepository.class);
        BookingCompletionService service = new BookingCompletionService(bookings);
        when(bookings.lockBooking(42L)).thenReturn(Optional.of(new BookingPersistenceRepository.BookingState(
                42L, 3L, 7L, 9L, LocalDateTime.parse("2026-11-02T10:00"),
                LocalDateTime.parse("2026-11-02T11:00"), "CONFIRMED")));

        assertThatThrownBy(() -> service.completeBooking(42L, 8L))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
