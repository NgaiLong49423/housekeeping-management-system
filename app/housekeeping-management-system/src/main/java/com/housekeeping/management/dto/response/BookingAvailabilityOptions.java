package com.housekeeping.management.dto.response;

import java.util.List;

public record BookingAvailabilityOptions(List<StaffOption> staffOptions, List<String> startTimes,
        List<TimeOption> timeOptions, String message,String earliestStartTime,String latestStartTime) {
    public BookingAvailabilityOptions {
        staffOptions = List.copyOf(staffOptions);
        startTimes = List.copyOf(startTimes);
        timeOptions = List.copyOf(timeOptions);
    }

    public BookingAvailabilityOptions(List<StaffOption> staffOptions, List<String> startTimes) {
        this(staffOptions,startTimes,List.of(),null,null,null);
    }

    public BookingAvailabilityOptions(List<StaffOption> staffOptions,List<String> startTimes,List<TimeOption> timeOptions,String message) {
        this(staffOptions,startTimes,timeOptions,message,null,null);
    }

    public record StaffOption(Long id, String name, String staffType) { }
    public record TimeOption(String startTime, List<StaffOption> staffOptions) {
        public TimeOption { staffOptions = List.copyOf(staffOptions); }
    }
}
