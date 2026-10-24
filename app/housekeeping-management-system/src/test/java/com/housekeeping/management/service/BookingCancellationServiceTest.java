package com.housekeeping.management.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class BookingCancellationServiceTest {
    private static final LocalDateTime START = LocalDateTime.parse("2026-11-01T12:00:00");

    @Test
    void acceptsCancellationAtExactTwoHourBoundary() {
        assertThat(BookingCancellationService.canCustomerCancel("CONFIRMED", START, START.minusHours(2))).isTrue();
    }

    @Test
    void rejectsCancellationInsideTwoHourWindowAndNonConfirmedBooking() {
        assertThat(BookingCancellationService.canCustomerCancel("CONFIRMED", START, START.minusMinutes(119))).isFalse();
        assertThat(BookingCancellationService.canCustomerCancel("AWAITING_PAYMENT", START, START.minusHours(3))).isFalse();
    }
}
