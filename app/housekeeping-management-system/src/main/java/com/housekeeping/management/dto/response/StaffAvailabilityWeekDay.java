package com.housekeeping.management.dto.response;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public record StaffAvailabilityWeekDay(LocalDate date, List<AdminWindow> adminWindows,
        List<PortalViews.ScheduleCard> staffWindows, boolean canRegister,
        LocalTime defaultStart, LocalTime defaultEnd) {
    public StaffAvailabilityWeekDay {
        adminWindows = List.copyOf(adminWindows);
        staffWindows = List.copyOf(staffWindows);
    }

    public record AdminWindow(LocalTime start, LocalTime end) { }
}
