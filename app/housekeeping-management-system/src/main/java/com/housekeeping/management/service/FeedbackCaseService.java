package com.housekeeping.management.service;

import com.housekeeping.management.dto.request.PortalForms.IncidentReviewForm;
import com.housekeeping.management.dto.request.PortalForms.StaffIncidentActionForm;
import com.housekeeping.management.dto.request.PortalForms.IncidentItemDetails;
import com.housekeeping.management.dto.response.PortalViews.IncidentCard;
import com.housekeeping.management.dto.response.PortalViews.FeedbackCaseMessageCard;
public interface FeedbackCaseService {

    Long open(Long bookingId, Long customerId, java.util.List<String> types, String description,
            IncidentItemDetails itemDetails, String customerRequestedResolution, boolean hasEvidence);
    default Long open(Long bookingId, Long customerId, java.util.List<String> types, String description,
            IncidentItemDetails itemDetails) { return open(bookingId,customerId,types,description,itemDetails,null,false); }
    default Long open(Long bookingId, Long customerId, java.util.List<String> types, String description) {
        return open(bookingId, customerId, types, description, null, null, false);
    }
    public IncidentCard customerCase(Long bookingId, Long customerId);
    public IncidentCard adminCase(Long feedbackId, Long adminId);
    IncidentCard adminCaseById(Long caseId, Long adminId);
    java.util.List<FeedbackCaseMessageCard> customerMessages(Long caseId, Long customerId);
    java.util.List<FeedbackCaseMessageCard> adminMessages(Long caseId, Long adminId);
    Long addCustomerMessage(Long caseId, Long bookingId, Long customerId, String text, boolean hasAttachments);
    Long addAdminMessage(Long caseId, Long adminId, String text, boolean hasAttachments);
    void confirmAdminMessage(Long caseId, Long messageId, Long adminId);
    void discardUnsentMessage(Long caseId, Long messageId, Long actorId, boolean admin);
    public java.util.List<com.housekeeping.management.dto.response.PortalViews.StaffIncidentActionCard> actions(Long caseId, Long adminId);
    java.util.List<com.housekeeping.management.dto.response.PortalViews.CaseVerificationCard> verificationHistory(Long caseId, Long adminId);
    boolean hasSufficientEvidence(Long caseId);
    public void review(Long caseId, Long adminId, IncidentReviewForm form);
    void proposeStaffAction(Long caseId, Long adminId, StaffIncidentActionForm form);
    public void decideStaffAction(Long caseId, Long managerId, boolean approve);
    void decideStaffAction(Long caseId, Long managerId, String decision, java.math.BigDecimal liabilityAmount, String reason, boolean appealDecision);
    java.util.List<com.housekeeping.management.dto.response.PortalViews.StaffActionNoticeCard> ownStaffActions(Long staffId);
    void respondToStaffAction(Long actionId, Long staffId, String response, boolean appeal);
    void recordStaffRecoveryInstallment(Long actionId, Long adminId, com.housekeeping.management.dto.request.PortalForms.StaffRecoveryInstallmentForm form, String receiptPath);
    java.util.List<com.housekeeping.management.dto.response.PortalViews.StaffRecoveryPaymentCard> staffRecoveryPayments(Long actionId, Long adminId);
    java.math.BigDecimal totalStaffRecoveryCollected();
    java.nio.file.Path staffRecoveryReceiptPath(Long installmentId, Long adminId);
    boolean staffInvestigationFinalized(Long caseId);
    boolean staffOwnsAction(Long actionId, Long staffId);
    boolean adminCanViewStaffAction(Long actionId, Long adminId);
    void addStaffActionEvidence(Long actionId, Long staffId, Long staffUserId, String stage, java.util.List<com.housekeeping.management.entity.StaffIncidentActionEvidence> evidence);
    java.nio.file.Path staffActionEvidencePath(Long evidenceId, Long actorId, boolean admin);
    void resolveAfterCustomerConfirmation(Long caseId, Long customerId);
    void acceptIntake(Long caseId, Long adminId);
    void startSearchingItem(Long caseId, Long adminId);
    void sendOutcome(Long caseId, Long adminId, String outcome);
    void customerNeedsMoreHelp(Long caseId, Long bookingId, Long customerId, String message);
    void updateLifecycle(Long caseId, String status);
    java.util.List<com.housekeeping.management.dto.response.PortalViews.StaffActionNoticeCard> approvedActionsForStaff(Long staffId);
    void acknowledgeStaffAction(Long actionId, Long staffId);
    boolean belongsToBooking(Long caseId, Long bookingId);
    boolean authorizesStaffRecovery(Long caseId, Long bookingId);
    Long initialCustomerMessageId(Long caseId);
    boolean authorizesMessageEvidence(Long caseId, Long messageId, Long actorId, boolean admin, Long bookingId);
    boolean customerOwnsEvidence(Long evidenceId, Long customerId);
    boolean customerOwnsCase(Long caseId, Long customerId);
}
