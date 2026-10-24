package com.housekeeping.management.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class BookingReservationServiceTest {
    private final LocalDateTime now = LocalDateTime.parse("2026-11-01T08:00:00");

    @Test
    void acceptsExactMinimumLeadTimeAndMaximumHorizon() {
        assertThatCode(() -> BookingReservationService.validateRequestedStart(now.plusMinutes(120), now))
                .doesNotThrowAnyException();
        assertThatCode(() -> BookingReservationService.validateRequestedStart(now.plusDays(30), now))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsShortLeadTimeOffSlotAndBeyondHorizon() {
        assertThatThrownBy(() -> BookingReservationService.validateRequestedStart(now.plusMinutes(119), now))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("120 phút");
        assertThatThrownBy(() -> BookingReservationService.validateRequestedStart(now.plusMinutes(135), now))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("30 phút");
        assertThatThrownBy(() -> BookingReservationService.validateRequestedStart(now.plusDays(30).plusMinutes(30), now))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("30 ngày");
    }
}
