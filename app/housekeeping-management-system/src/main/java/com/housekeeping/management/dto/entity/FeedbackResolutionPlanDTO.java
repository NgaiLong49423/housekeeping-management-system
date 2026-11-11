package com.housekeeping.management.dto.entity;

import com.housekeeping.management.entity.FeedbackResolutionPlan;
import java.time.LocalDateTime;

public record FeedbackResolutionPlanDTO(Long planId, Long caseId, Integer versionNo, String status,
        String internalFinding, String customerResponse, String completionFeedback, LocalDateTime customerCompletedAt,
        Long selectedOptionId, String customerPayoutMethod, Long createdBy, LocalDateTime createdAt, LocalDateTime respondedAt, Long managerId,
        LocalDateTime managerDecisionAt, String managerReason) {
    public static FeedbackResolutionPlanDTO convertEntityToDTO(FeedbackResolutionPlan e) {
        return e == null ? null : new FeedbackResolutionPlanDTO(e.getPlanId(),e.getCaseId(),e.getVersionNo(),e.getStatus(),
                e.getInternalFinding(),e.getCustomerResponse(),e.getCompletionFeedback(),e.getCustomerCompletedAt(),e.getSelectedOptionId(),e.getCustomerPayoutMethod(),
                e.getCreatedBy(),e.getCreatedAt(),e.getRespondedAt(),e.getManagerId(),e.getManagerDecisionAt(),e.getManagerReason());
    }
    public FeedbackResolutionPlan convertDTOToEntity() {
        FeedbackResolutionPlan e=new FeedbackResolutionPlan(); e.setPlanId(planId); e.setCaseId(caseId); e.setVersionNo(versionNo);
        e.setStatus(status); e.setInternalFinding(internalFinding); e.setCustomerResponse(customerResponse); e.setCompletionFeedback(completionFeedback); e.setCustomerCompletedAt(customerCompletedAt);
        e.setSelectedOptionId(selectedOptionId); e.setCustomerPayoutMethod(customerPayoutMethod); e.setCreatedBy(createdBy);
        e.setCreatedAt(createdAt); e.setRespondedAt(respondedAt); e.setManagerId(managerId);
        e.setManagerDecisionAt(managerDecisionAt); e.setManagerReason(managerReason); return e;
    }
}
