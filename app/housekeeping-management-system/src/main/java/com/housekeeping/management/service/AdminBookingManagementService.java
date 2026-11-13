package com.housekeeping.management.service;

import java.time.LocalDateTime;

public interface AdminBookingManagementService {

    void cancelConfirmedBooking(Long bookingId, Long adminId, String reason);
    void reassignConfirmedBooking(Long bookingId, Long adminId, Long replacementStaffId);
    void replaceAssignedStaff(Long bookingId, Long adminId, Long currentStaffId, Long replacementStaffId);
    void verifyDisputedArrival(Long bookingId, Long adminId, LocalDateTime actualArrivalAt, String note);
    void verifyStaffNotArrived(Long bookingId, Long adminId, String note);
}
