package com.housekeeping.management.dto.entity;

import com.housekeeping.management.entity.FeedbackCaseVerification;
import java.time.LocalDateTime;

public record FeedbackCaseVerificationDTO(Long verificationId, Long caseId, Long adminId, String caseStatus,
        String reviewedEvidenceIds, String internalFinding, LocalDateTime createdAt) {
    public static FeedbackCaseVerificationDTO convertEntityToDTO(FeedbackCaseVerification e) {
        if (e == null) return null;
        return new FeedbackCaseVerificationDTO(e.getVerificationId(), e.getCaseId(), e.getAdminId(), e.getCaseStatus(),
                e.getReviewedEvidenceIds(), e.getInternalFinding(), e.getCreatedAt());
    }
    public FeedbackCaseVerification convertDTOToEntity() {
        FeedbackCaseVerification e = new FeedbackCaseVerification();
        e.setVerificationId(verificationId); e.setCaseId(caseId); e.setAdminId(adminId); e.setCaseStatus(caseStatus);
        e.setReviewedEvidenceIds(reviewedEvidenceIds); e.setInternalFinding(internalFinding); e.setCreatedAt(createdAt);
        return e;
    }
}
