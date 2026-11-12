package com.housekeeping.management.service;

import java.time.LocalDateTime;

public interface BookingCancellationService {

    public void cancelByCustomer(Long bookingId, Long customerId);
    static boolean canCustomerCancel(String status, LocalDateTime startTime, LocalDateTime now, LocalDateTime workStartedAt) {
        return "CONFIRMED".equals(status) && workStartedAt == null && !now.isAfter(startTime.minusHours(2));
    }
}
