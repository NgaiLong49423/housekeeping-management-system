package com.housekeeping.management.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Nationalized;

@Entity @Table(name="Feedback_Resolution_Plans", schema="dbo") @Getter @Setter @NoArgsConstructor
public class FeedbackResolutionPlan {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) @Column(name="plan_id") private Long planId;
    @Column(name="case_id", nullable=false) private Long caseId;
    @Column(name="version_no", nullable=false) private Integer versionNo;
    @Column(nullable=false, length=32) private String status="DRAFT";
    @Nationalized @Column(name="internal_finding", length=4000) private String internalFinding;
    @Nationalized @Column(name="customer_response", length=2000) private String customerResponse;
    @Nationalized @Column(name="completion_feedback", length=1000) private String completionFeedback;
    @Column(name="customer_completed_at") private LocalDateTime customerCompletedAt;
    @Column(name="selected_option_id") private Long selectedOptionId;
    @Column(name="customer_payout_method", length=20) private String customerPayoutMethod;
    @Column(name="created_by", nullable=false) private Long createdBy;
    @Column(name="created_at", nullable=false) private LocalDateTime createdAt;
    @Column(name="responded_at") private LocalDateTime respondedAt;
    @Column(name="manager_id") private Long managerId;
    @Column(name="manager_decision_at") private LocalDateTime managerDecisionAt;
    @Nationalized @Column(name="manager_reason", length=2000) private String managerReason;
}
