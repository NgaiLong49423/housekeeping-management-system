package com.housekeeping.management.repository;

import com.housekeeping.management.entity.FeedbackCaseEvidence;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface FeedbackCaseEvidenceRepository extends JpaRepository<FeedbackCaseEvidence, Long> {
    List<FeedbackCaseEvidence> findAllByCaseIdOrderByUploadedAtAscEvidenceIdAsc(Long caseId);
    List<FeedbackCaseEvidence> findAllByCaseIdAndMessageIdOrderByUploadedAtAscEvidenceIdAsc(Long caseId, Long messageId);
    List<FeedbackCaseEvidence> findAllByMessageIdOrderByUploadedAtAscEvidenceIdAsc(Long messageId);
    long countByMessageId(Long messageId);

    @org.springframework.data.jpa.repository.Query("select (count(evidence)>0) from FeedbackCaseEvidence evidence "
            + "join FeedbackCase incident on incident.caseId=evidence.caseId "
            + "join Feedback feedback on feedback.feedbackId=incident.feedbackId "
            + "where evidence.evidenceId=:id and feedback.customerId=:customerId")
    boolean customerOwnsEvidence(@org.springframework.data.repository.query.Param("id") Long id,
            @org.springframework.data.repository.query.Param("customerId") Long customerId);
}
