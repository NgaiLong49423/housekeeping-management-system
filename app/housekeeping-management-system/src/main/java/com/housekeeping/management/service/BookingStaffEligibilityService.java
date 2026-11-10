package com.housekeeping.management.service;

public interface BookingStaffEligibilityService {
    boolean hasUnfinishedBookingForStaff(Long staffId);
}
