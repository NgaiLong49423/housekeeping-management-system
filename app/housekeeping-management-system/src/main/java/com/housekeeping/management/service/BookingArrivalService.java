package com.housekeeping.management.service;

import com.housekeeping.management.dto.BookingDelayCompensation;
import com.housekeeping.management.dto.BookingDelayCompensation.Tier;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

public interface BookingArrivalService {

    public void confirm(Long bookingId, Long customerId);
    public void confirmStaffArrival(Long bookingId, Long staffId);}
