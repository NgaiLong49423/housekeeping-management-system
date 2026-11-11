package com.housekeeping.management.repository;

import com.housekeeping.management.entity.FeedbackResolutionOption;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FeedbackResolutionOptionRepository extends JpaRepository<FeedbackResolutionOption,Long> {
    List<FeedbackResolutionOption> findAllByPlanIdOrderByOptionNoAsc(Long planId);
}
