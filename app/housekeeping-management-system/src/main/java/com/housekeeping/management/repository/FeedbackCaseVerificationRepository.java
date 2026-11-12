package com.housekeeping.management.repository;

import com.housekeeping.management.entity.FeedbackCaseVerification;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FeedbackCaseVerificationRepository extends JpaRepository<FeedbackCaseVerification, Long> {
    List<FeedbackCaseVerification> findAllByCaseIdOrderByCreatedAtDescVerificationIdDesc(Long caseId);
}
