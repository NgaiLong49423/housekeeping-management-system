package com.housekeeping.management.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class BookingDelayCompensationTest {
    private static final LocalDateTime START = LocalDateTime.parse("2026-11-01T10:00:00");
    private static final BigDecimal PRICE = new BigDecimal("150000.00");

    @Test
    void appliesTheApprovedDiscountBandsAtTheirBoundaries() {
        assertCompensation(0, 0, "0.00");
        assertCompensation(15, 5, "7500.00");
        assertCompensation(16, 10, "15000.00");
        assertCompensation(30, 10, "15000.00");
        assertCompensation(31, 20, "30000.00");
    }

    @Test
    void roundsAnyPartialMinuteUpAndRoundsDiscountToWholeDong() {
        var result = BookingDelayCompensation.calculate(START, START.plusMinutes(15).plusSeconds(1), PRICE);
        assertThat(result.delayedMinutes()).isEqualTo(16);
        assertThat(result.discountPercent()).isEqualTo(10);
        assertThat(result.discountAmount()).isEqualByComparingTo("15000.00");

        var rounded = BookingDelayCompensation.calculate(START, START.plusMinutes(1), new BigDecimal("100011.00"));
        assertThat(rounded.discountAmount()).isEqualByComparingTo("5001.00");
    }

    private static void assertCompensation(int minutes, int percent, String amount) {
        var result = BookingDelayCompensation.calculate(START, START.plusMinutes(minutes), PRICE);
        assertThat(result.delayedMinutes()).isEqualTo(minutes);
        assertThat(result.discountPercent()).isEqualTo(percent);
        assertThat(result.discountAmount()).isEqualByComparingTo(amount);
    }
}
