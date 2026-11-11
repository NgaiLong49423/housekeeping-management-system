package com.housekeeping.management.service;

import com.housekeeping.management.dto.request.PortalForms.IncidentReviewForm;
import com.housekeeping.management.dto.request.PortalForms.IncidentItemDetails;
import com.housekeeping.management.dto.response.PortalViews.IncidentCard;
import com.housekeeping.management.dto.response.PortalViews.FeedbackCaseMessageCard;
public interface FeedbackCaseService {

    Long open(Long bookingId, Long customerId, java.util.List<String> types, String description,
            IncidentItemDetails itemDetails);
    default Long open(Long bookingId, Long customerId, java.util.List<String> types, String description) {
        return open(bookingId, customerId, types, description, null);
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
    public void review(Long caseId, Long adminId, IncidentReviewForm form);
    public void decideStaffAction(Long caseId, Long managerId, boolean approve);
    public void resolve(Long caseId, Long adminId);
    void resolveAfterCustomerConfirmation(Long caseId, Long customerId);
    java.util.List<com.housekeeping.management.dto.response.PortalViews.StaffActionNoticeCard> approvedActionsForStaff(Long staffId);
    void acknowledgeStaffAction(Long actionId, Long staffId);
    boolean belongsToBooking(Long caseId, Long bookingId);
    boolean authorizesStaffRecovery(Long caseId, Long bookingId);
    Long initialCustomerMessageId(Long caseId);
    boolean authorizesMessageEvidence(Long caseId, Long messageId, Long actorId, boolean admin, Long bookingId);
    boolean customerOwnsEvidence(Long evidenceId, Long customerId);
    boolean customerOwnsCase(Long caseId, Long customerId);
}
