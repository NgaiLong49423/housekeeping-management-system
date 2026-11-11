package com.housekeeping.management.repository;

import com.housekeeping.management.entity.FeedbackResolutionPlan;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FeedbackResolutionPlanRepository extends JpaRepository<FeedbackResolutionPlan,Long> {
    List<FeedbackResolutionPlan> findAllByCaseIdOrderByVersionNoDesc(Long caseId);
    Optional<FeedbackResolutionPlan> findFirstByCaseIdOrderByVersionNoDesc(Long caseId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from FeedbackResolutionPlan p where p.planId=:id")
    Optional<FeedbackResolutionPlan> findByIdForUpdate(@Param("id") Long id);
}
