package com.housekeeping.management.service;

import com.housekeeping.management.dto.request.PortalForms.FinancialEntryForm;
import com.housekeeping.management.dto.response.PortalViews.FinancialEntryCard;
import com.housekeeping.management.dto.response.PortalViews.FinancialSummary;
import com.housekeeping.management.dto.response.PortalViews.FinancialLedgerCard;
import com.housekeeping.management.dto.response.PortalViews.CustomerCompensationOfferCard;
import com.housekeeping.management.dto.response.PortalViews.AdminPayoutDetails;
import com.housekeeping.management.dto.request.PortalForms.CustomerCompensationResponseForm;

public interface BookingFinancialService {

    public Long propose(Long bookingId, Long caseId, Long adminId, FinancialEntryForm form);
    public void approve(Long entryId, Long managerId);
    public void reject(Long entryId, Long managerId, String reason);
    public void cancel(Long entryId, Long adminId, String reason);
    public void settle(Long entryId, Long adminId, String method, String reference, boolean simulated);
    public java.util.List<FinancialEntryCard> forBooking(Long bookingId, Long actorId);
    public FinancialSummary summary(Long actorId);
    public java.util.List<FinancialEntryCard> all(Long actorId);
    public java.util.Optional<FinancialEntryCard> entry(Long entryId, Long actorId);
    public java.util.List<com.housekeeping.management.dto.response.PortalViews.CustomerFinancialCard> customerVisible(Long bookingId, Long customerId);
    public java.util.List<FinancialLedgerCard> ledger(Long actorId);
    public java.util.List<CustomerCompensationOfferCard> customerCompensationOffers(Long customerId);
    public java.util.List<CustomerCompensationOfferCard> customerCompensationOffersForBooking(Long bookingId, Long customerId);
    public void respondToCompensationOffer(Long bookingId, Long entryId, Long customerId, CustomerCompensationResponseForm form);
    void confirmCompensationReceived(Long bookingId, Long entryId, Long customerId);
    public java.util.Optional<AdminPayoutDetails> adminPayoutDetails(Long entryId, Long adminId);
}
