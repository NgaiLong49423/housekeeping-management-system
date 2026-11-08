package com.housekeeping.management.dto.response;

import java.time.LocalDate;
import java.util.List;

public record BookingAvailabilityDates(List<LocalDate> dates, String message) {
    public BookingAvailabilityDates {
        dates = List.copyOf(dates);
    }
}
