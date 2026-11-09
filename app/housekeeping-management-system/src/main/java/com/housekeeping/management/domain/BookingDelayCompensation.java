package com.housekeeping.management.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;

public record BookingDelayCompensation(int delayedMinutes, int discountPercent, BigDecimal discountAmount) {
    public static BookingDelayCompensation calculate(LocalDateTime scheduledStart, LocalDateTime staffArrivedAt,
            BigDecimal totalAmount) {
        long lateSeconds = Math.max(0, Duration.between(scheduledStart, staffArrivedAt).getSeconds());
        int delayedMinutes = Math.toIntExact((lateSeconds + 59) / 60);
        int percent = delayedMinutes == 0 ? 0 : delayedMinutes <= 15 ? 5 : delayedMinutes <= 30 ? 10 : 20;
        BigDecimal discount = totalAmount.multiply(BigDecimal.valueOf(percent))
                .movePointLeft(2).setScale(0, RoundingMode.HALF_UP).setScale(2);
        return new BookingDelayCompensation(delayedMinutes, percent, discount);
    }
}
