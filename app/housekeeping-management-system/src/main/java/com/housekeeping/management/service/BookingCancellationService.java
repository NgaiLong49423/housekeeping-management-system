package com.housekeeping.management.service;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.springframework.security.access.AccessDeniedException;

public interface BookingCancellationService {

    public void cancelByCustomer(Long bookingId, Long customerId);static boolean canCustomerCancel(String status, LocalDateTime startTime, LocalDateTime now, LocalDateTime workStartedAt) {
        return "CONFIRMED".equals(status) && workStartedAt == null && !now.isAfter(startTime.minusHours(2));
    }
}
