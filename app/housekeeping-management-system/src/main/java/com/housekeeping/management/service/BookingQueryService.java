package com.housekeeping.management.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Read-only booking queries used by other business domains. */
public interface BookingQueryService {
    boolean hasActiveBookingForHouse(Long houseId);
    boolean hasFutureConfirmedBookingOverlapping(Long staffId, LocalDate date, java.time.LocalTime start,
            java.time.LocalTime end);
    List<Long> activeBookingIdsInInterval(LocalDate from, LocalDate until);
    List<ActiveBookingWindow> activeBookingWindowsInInterval(LocalDate from, LocalDate until);
    List<BookingLocation> futureActiveBookingsWithLocation();

    record BookingLocation(Long bookingId, BigDecimal latitude, BigDecimal longitude) { }
    record ActiveBookingWindow(Long bookingId, java.time.LocalDateTime start, java.time.LocalDateTime end) { }
}
