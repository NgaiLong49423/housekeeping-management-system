package com.housekeeping.management.dto.response;

import java.util.List;

public record BookingAvailabilityOptions(List<StaffOption> staffOptions, List<String> startTimes,
        List<TimeOption> timeOptions, String message) {
    public BookingAvailabilityOptions {
        staffOptions = List.copyOf(staffOptions);
        startTimes = List.copyOf(startTimes);
        timeOptions = List.copyOf(timeOptions);
    }

    public BookingAvailabilityOptions(List<StaffOption> staffOptions, List<String> startTimes) {
        this(staffOptions,startTimes,List.of(),null);
    }

    public record StaffOption(Long id, String name, String staffType) { }
    public record TimeOption(String startTime, List<StaffOption> staffOptions) {
        public TimeOption { staffOptions = List.copyOf(staffOptions); }
    }
}
