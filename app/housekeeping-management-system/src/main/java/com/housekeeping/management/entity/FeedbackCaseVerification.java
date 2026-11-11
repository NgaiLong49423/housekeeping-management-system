package com.housekeeping.management.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Nationalized;

@Entity @Table(name="Feedback_Case_Verifications", schema="dbo") @Getter @Setter @NoArgsConstructor
public class FeedbackCaseVerification {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) @Column(name="verification_id") private Long verificationId;
    @Column(name="case_id", nullable=false) private Long caseId;
    @Column(name="admin_id", nullable=false) private Long adminId;
    @Column(name="case_status", nullable=false, length=30) private String caseStatus;
    @Nationalized @Column(name="reviewed_evidence_ids", nullable=false, length=2000) private String reviewedEvidenceIds;
    @Nationalized @Column(name="internal_finding", length=4000) private String internalFinding;
    @Column(name="created_at", nullable=false) private LocalDateTime createdAt;
}
