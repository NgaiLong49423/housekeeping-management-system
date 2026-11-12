package com.housekeeping.management.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

public record BookingDelayCompensation(int delayedMinutes, int discountPercent, BigDecimal discountAmount) {
    public static final int GRACE_PERIOD_MINUTES = 0;

    public static BookingDelayCompensation calculate(LocalDateTime scheduledStart, LocalDateTime staffArrivedAt,
            BigDecimal totalAmount, List<Tier> tiers) {
        long lateSeconds = Math.max(0, Duration.between(scheduledStart, staffArrivedAt).getSeconds());
        int delayedMinutes = Math.toIntExact((lateSeconds + 59) / 60);
        int percent = delayedMinutes == 0 ? 0 : tiers.stream()
                .filter(tier -> tier.minDelayMinutes() <= delayedMinutes
                        && (tier.maxDelayMinutes() == null || tier.maxDelayMinutes() >= delayedMinutes))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Không tìm thấy mức giảm phù hợp với số phút trễ."))
                .discountPercent();
        BigDecimal discount = totalAmount.multiply(BigDecimal.valueOf(percent))
                .movePointLeft(2).setScale(0, RoundingMode.HALF_UP).setScale(2);
        return new BookingDelayCompensation(delayedMinutes, percent, discount);
    }

    public record Tier(int minDelayMinutes, Integer maxDelayMinutes, int discountPercent) { }
}
