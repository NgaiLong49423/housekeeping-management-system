package com.housekeeping.management.service;


public interface BookingCompletionService {

    public void startBooking(Long bookingId, Long staffId);
    public void completeBooking(Long bookingId, Long staffId);}
