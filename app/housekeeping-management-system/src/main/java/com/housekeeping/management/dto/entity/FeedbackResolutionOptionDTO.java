package com.housekeeping.management.dto.entity;

import com.housekeeping.management.entity.FeedbackResolutionOption;

public record FeedbackResolutionOptionDTO(Long optionId, Long planId, Integer optionNo, String title, String description) {
    public static FeedbackResolutionOptionDTO convertEntityToDTO(FeedbackResolutionOption e) {
        return e==null?null:new FeedbackResolutionOptionDTO(e.getOptionId(),e.getPlanId(),e.getOptionNo(),e.getTitle(),e.getDescription());
    }
    public FeedbackResolutionOption convertDTOToEntity() {
        FeedbackResolutionOption e=new FeedbackResolutionOption(); e.setOptionId(optionId); e.setPlanId(planId);
        e.setOptionNo(optionNo); e.setTitle(title); e.setDescription(description); return e;
    }
}
