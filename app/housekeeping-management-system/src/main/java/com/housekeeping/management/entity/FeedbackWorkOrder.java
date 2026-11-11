package com.housekeeping.management.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Nationalized;

@Entity @Table(name="Feedback_Work_Orders", schema="dbo") @Getter @Setter @NoArgsConstructor
public class FeedbackWorkOrder {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) @Column(name="work_order_id") private Long workOrderId;
    @Column(name="case_id", nullable=false) private Long caseId;
    @Column(name="action_id", nullable=false) private Long actionId;
    @Column(name="assigned_staff_id") private Long assignedStaffId;
    @Column(name="scheduled_at") private LocalDateTime scheduledAt;
    @Nationalized @Column(nullable=false, length=2000) private String scope;
    @Column(name="estimated_cost", precision=12, scale=2) private BigDecimal estimatedCost;
    @Nationalized @Column(name="estimated_materials", length=1000) private String estimatedMaterials;
    @Column(name="actual_cost", precision=12, scale=2) private BigDecimal actualCost;
    @Nationalized @Column(name="actual_materials", length=1000) private String actualMaterials;
    @Nationalized @Column(name="completion_evidence", length=1000) private String completionEvidence;
    @Column(nullable=false, length=20) private String status="OPEN";
    @Column(name="customer_confirmation", length=20) private String customerConfirmation;
    @Nationalized @Column(name="customer_feedback", length=1000) private String customerFeedback;
    @Column(name="created_by", nullable=false) private Long createdBy;
    @Column(name="created_at", nullable=false) private LocalDateTime createdAt;
    @Column(name="completed_at") private LocalDateTime completedAt;
}
