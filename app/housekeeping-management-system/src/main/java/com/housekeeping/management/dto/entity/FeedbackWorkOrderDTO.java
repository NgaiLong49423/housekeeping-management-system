package com.housekeeping.management.dto.entity;

import com.housekeeping.management.entity.FeedbackWorkOrder;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record FeedbackWorkOrderDTO(Long workOrderId, Long caseId, Long actionId, Long assignedStaffId,
        LocalDateTime scheduledAt, String scope, BigDecimal estimatedCost, String estimatedMaterials,
        BigDecimal actualCost, String actualMaterials, String completionEvidence, String status,
        String customerConfirmation, String customerFeedback, Long createdBy, LocalDateTime createdAt,
        LocalDateTime completedAt) {
    public static FeedbackWorkOrderDTO convertEntityToDTO(FeedbackWorkOrder e) {
        return e==null?null:new FeedbackWorkOrderDTO(e.getWorkOrderId(),e.getCaseId(),e.getActionId(),e.getAssignedStaffId(),
                e.getScheduledAt(),e.getScope(),e.getEstimatedCost(),e.getEstimatedMaterials(),e.getActualCost(),
                e.getActualMaterials(),e.getCompletionEvidence(),e.getStatus(),e.getCustomerConfirmation(),
                e.getCustomerFeedback(),e.getCreatedBy(),e.getCreatedAt(),e.getCompletedAt());
    }
    public FeedbackWorkOrder convertDTOToEntity() {
        FeedbackWorkOrder e=new FeedbackWorkOrder(); e.setWorkOrderId(workOrderId); e.setCaseId(caseId); e.setActionId(actionId);
        e.setAssignedStaffId(assignedStaffId); e.setScheduledAt(scheduledAt); e.setScope(scope); e.setEstimatedCost(estimatedCost);
        e.setEstimatedMaterials(estimatedMaterials); e.setActualCost(actualCost); e.setActualMaterials(actualMaterials);
        e.setCompletionEvidence(completionEvidence); e.setStatus(status); e.setCustomerConfirmation(customerConfirmation);
        e.setCustomerFeedback(customerFeedback); e.setCreatedBy(createdBy); e.setCreatedAt(createdAt); e.setCompletedAt(completedAt); return e;
    }
}
