package com.housekeeping.management.service;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

public interface CustomerDelayReportService {

    void reportStaffNotArrived(Long bookingId, Long customerId);
    void respondAfterAdminReview(Long bookingId, Long customerId, String response);
}
