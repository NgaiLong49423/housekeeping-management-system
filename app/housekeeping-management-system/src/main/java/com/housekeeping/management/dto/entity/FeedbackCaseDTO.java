package com.housekeeping.management.dto.entity;

import com.housekeeping.management.entity.FeedbackCase;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record FeedbackCaseDTO(Long caseId, Long feedbackId, String incidentTypes, String customerDescription,
        String itemDescription, BigDecimal customerEstimatedValue, Integer itemAgeMonths,
        String caseStatus, String customerOutcome, String remedyType, boolean remedyCompleted,
        String internalFinding, String staffAction, String staffActionStatus, Long staffActionBy,
        Long staffActionApprovedBy, Long reviewedBy, LocalDateTime reviewedAt, LocalDateTime createdAt,
        LocalDateTime resolvedAt) {
    public FeedbackCaseDTO(Long caseId, Long feedbackId, String caseStatus) {
        this(caseId, feedbackId, null, null, null, null, null, caseStatus, null, null, false, null,
                null, null, null, null, null, null, null, null);
    }

    public static FeedbackCaseDTO convertEntityToDTO(FeedbackCase entity) {
        if (entity == null) return null;
        return new FeedbackCaseDTO(entity.getCaseId(), entity.getFeedbackId(), entity.getIncidentTypes(),
                entity.getCustomerDescription(), entity.getItemDescription(), entity.getCustomerEstimatedValue(),
                entity.getItemAgeMonths(), entity.getCaseStatus(), entity.getCustomerOutcome(),
                entity.getRemedyType(), entity.isRemedyCompleted(), entity.getInternalFinding(),
                entity.getStaffAction(), entity.getStaffActionStatus(), entity.getStaffActionBy(),
                entity.getStaffActionApprovedBy(), entity.getReviewedBy(), entity.getReviewedAt(),
                entity.getCreatedAt(), entity.getResolvedAt());
    }

    public FeedbackCase convertDTOToEntity() {
        FeedbackCase entity = new FeedbackCase();
        entity.setCaseId(caseId);
        entity.setFeedbackId(feedbackId);
        entity.setIncidentTypes(incidentTypes);
        entity.setCustomerDescription(customerDescription);
        entity.setItemDescription(itemDescription);
        entity.setCustomerEstimatedValue(customerEstimatedValue);
        entity.setItemAgeMonths(itemAgeMonths);
        entity.setCaseStatus(caseStatus == null ? "RECEIVED" : caseStatus);
        entity.setCustomerOutcome(customerOutcome);
        entity.setRemedyType(remedyType);
        entity.setRemedyCompleted(remedyCompleted);
        entity.setInternalFinding(internalFinding);
        entity.setStaffAction(staffAction);
        entity.setStaffActionStatus(staffActionStatus);
        entity.setStaffActionBy(staffActionBy);
        entity.setStaffActionApprovedBy(staffActionApprovedBy);
        entity.setReviewedBy(reviewedBy);
        entity.setReviewedAt(reviewedAt);
        entity.setCreatedAt(createdAt);
        entity.setResolvedAt(resolvedAt);
        return entity;
    }
}
