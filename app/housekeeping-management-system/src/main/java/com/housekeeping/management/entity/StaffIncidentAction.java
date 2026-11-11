package com.housekeeping.management.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Nationalized;

@Entity
@Table(name = "Staff_Incident_Actions", schema = "dbo")
@Getter @Setter @NoArgsConstructor
public class StaffIncidentAction {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "action_id", nullable = false)
    private Long actionId;
    @Column(name = "case_id", nullable = false)
    private Long caseId;
    @Column(name = "staff_id", nullable = false)
    private Long staffId;
    @Column(name = "action_type", nullable = false, length = 30, columnDefinition = "varchar(30)")
    private String actionType;
    @Column(name = "status", nullable = false, length = 30, columnDefinition = "varchar(30)")
    private String status = "PENDING";
    @Nationalized @Column(name = "reason", nullable = false, length = 1000)
    private String reason;
    @Nationalized @Column(name = "staff_notice", length = 1000)
    private String staffNotice;
    @Column(name = "proposed_by", nullable = false)
    private Long proposedBy;
    @Column(name = "decided_by")
    private Long decidedBy;
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
    @Column(name = "decided_at")
    private LocalDateTime decidedAt;
    @Column(name = "acknowledged_at")
    private LocalDateTime acknowledgedAt;
    @Column(name = "response_deadline")
    private LocalDateTime responseDeadline;
    @Column(name = "staff_response_expired_at")
    private LocalDateTime staffResponseExpiredAt;
    @Nationalized @Column(name = "staff_response", length = 4000)
    private String staffResponse;
    @Column(name = "staff_responded_at")
    private LocalDateTime staffRespondedAt;
    @Nationalized @Column(name = "appeal_text", length = 4000)
    private String appealText;
    @Column(name = "appealed_at")
    private LocalDateTime appealedAt;
    @Nationalized @Column(name = "decision_reason", length = 1000)
    private String decisionReason;
    @Column(name = "liability_amount", precision = 12, scale = 2)
    private java.math.BigDecimal liabilityAmount;
    @Column(name = "finalized_at")
    private LocalDateTime finalizedAt;
    @Column(name = "appeal_decided_by")
    private Long appealDecidedBy;
    @Column(name = "appeal_decided_at")
    private LocalDateTime appealDecidedAt;
    @Column(name = "decision_outcome", length = 20, columnDefinition = "varchar(20)")
    private String decisionOutcome;
}
