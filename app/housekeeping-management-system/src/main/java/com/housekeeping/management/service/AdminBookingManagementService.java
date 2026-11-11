package com.housekeeping.management.service;

import com.housekeeping.management.domain.StaffType;
import com.housekeeping.management.entity.Staff;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

public interface AdminBookingManagementService {

    public void cancelConfirmedBooking(Long bookingId, Long adminId, String reason);
    public void reassignConfirmedBooking(Long bookingId, Long adminId, Long replacementStaffId);
    public void verifyDisputedArrival(Long bookingId, Long adminId, LocalDateTime actualArrivalAt, String note);}
