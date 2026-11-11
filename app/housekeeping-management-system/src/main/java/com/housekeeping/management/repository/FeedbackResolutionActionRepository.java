package com.housekeeping.management.repository;

import com.housekeeping.management.entity.FeedbackResolutionAction;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FeedbackResolutionActionRepository extends JpaRepository<FeedbackResolutionAction,Long> {
    List<FeedbackResolutionAction> findAllByOptionIdOrderByActionIdAsc(Long optionId);
    List<FeedbackResolutionAction> findAllByOptionIdInOrderByActionIdAsc(List<Long> optionIds);
}
