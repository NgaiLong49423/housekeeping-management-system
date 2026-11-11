package com.housekeeping.management.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Nationalized;

@Entity
@Table(name = "Feedback_Cases", schema = "dbo")
@Getter @Setter @NoArgsConstructor
public class FeedbackCase {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "case_id", nullable = false)
    private Long caseId;
    @Column(name = "feedback_id", nullable = false)
    private Long feedbackId;
    @Nationalized @Column(name = "incident_types", nullable = false, length = 150)
    private String incidentTypes;
    @Nationalized @Column(name = "customer_description", nullable = false, columnDefinition = "nvarchar(max)")
    private String customerDescription;
    @Nationalized @Column(name = "item_description", length = 300)
    private String itemDescription;
    @Column(name = "customer_estimated_value", precision = 12, scale = 2)
    private BigDecimal customerEstimatedValue;
    @Column(name = "item_age_months")
    private Integer itemAgeMonths;
    @Column(name = "case_status", nullable = false, length = 20, columnDefinition = "varchar(20)")
    private String caseStatus = "RECEIVED";
    @Nationalized @Column(name = "customer_outcome", columnDefinition = "nvarchar(max)")
    private String customerOutcome;
    @Column(name = "remedy_type", length = 30, columnDefinition = "varchar(30)")
    private String remedyType;
    @Column(name = "remedy_completed", nullable = false)
    private boolean remedyCompleted;
    @Nationalized @Column(name = "internal_finding", columnDefinition = "nvarchar(max)")
    private String internalFinding;
    @Column(name = "staff_action", length = 30, columnDefinition = "varchar(30)")
    private String staffAction;
    @Column(name = "staff_action_status", nullable = false, length = 20, columnDefinition = "varchar(20)")
    private String staffActionStatus = "NOT_PROPOSED";
    @Column(name = "staff_action_by")
    private Long staffActionBy;
    @Column(name = "staff_action_approved_by")
    private Long staffActionApprovedBy;
    @Column(name = "reviewed_by")
    private Long reviewedBy;
    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;
    @Column(name = "resolution_workflow_required", nullable=false)
    private boolean resolutionWorkflowRequired;
}
