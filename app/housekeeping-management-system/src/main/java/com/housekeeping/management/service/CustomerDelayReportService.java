package com.housekeeping.management.service;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

public interface CustomerDelayReportService {

    public void reportStaffNotArrived(Long bookingId, Long customerId);}
