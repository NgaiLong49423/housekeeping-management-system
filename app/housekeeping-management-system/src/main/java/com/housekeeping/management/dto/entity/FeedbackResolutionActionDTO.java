package com.housekeeping.management.dto.entity;

import com.housekeeping.management.entity.FeedbackResolutionAction;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record FeedbackResolutionActionDTO(Long actionId, Long optionId, String actionType, String description,
        LocalDateTime dueAt, BigDecimal amount, String calculationBasis) {
    public static FeedbackResolutionActionDTO convertEntityToDTO(FeedbackResolutionAction e) {
        return e==null?null:new FeedbackResolutionActionDTO(e.getActionId(),e.getOptionId(),e.getActionType(),e.getDescription(),e.getDueAt(),e.getAmount(),e.getCalculationBasis());
    }
    public FeedbackResolutionAction convertDTOToEntity() {
        FeedbackResolutionAction e=new FeedbackResolutionAction(); e.setActionId(actionId); e.setOptionId(optionId);
        e.setActionType(actionType); e.setDescription(description); e.setDueAt(dueAt); e.setAmount(amount);
        e.setCalculationBasis(calculationBasis); return e;
    }
}
