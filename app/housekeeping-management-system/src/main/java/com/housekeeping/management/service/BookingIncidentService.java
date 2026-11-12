package com.housekeeping.management.service;

import com.housekeeping.management.dto.BookingDelayCompensation;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

public interface BookingIncidentService {

    public void report(Long bookingId, Long staffId, String note, LocalDateTime expectedArrivalAt);
    public void respond(Long bookingId, Long customerId, String response);}
