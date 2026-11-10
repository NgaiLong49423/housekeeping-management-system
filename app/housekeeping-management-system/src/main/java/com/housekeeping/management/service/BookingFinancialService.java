package com.housekeeping.management.service;

import com.housekeeping.management.dto.request.PortalForms.FinancialEntryForm;
import com.housekeeping.management.dto.response.PortalViews.FinancialEntryCard;
import com.housekeeping.management.dto.response.PortalViews.FinancialSummary;

public interface BookingFinancialService {

    public Long propose(Long bookingId, Long caseId, Long adminId, FinancialEntryForm form);
    public void approve(Long entryId, Long managerId);
    public void reject(Long entryId, Long managerId, String reason);
    public void cancel(Long entryId, Long adminId, String reason);
    public void settle(Long entryId, Long adminId, String method, String reference, boolean simulated);
    public java.util.List<FinancialEntryCard> forBooking(Long bookingId, Long actorId);
    public FinancialSummary summary(Long actorId);
    public java.util.List<FinancialEntryCard> all(Long actorId);
    public java.util.List<com.housekeeping.management.dto.response.PortalViews.CustomerFinancialCard> customerVisible(Long bookingId, Long customerId);}
