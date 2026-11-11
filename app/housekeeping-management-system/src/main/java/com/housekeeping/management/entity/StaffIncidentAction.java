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
    @Column(name = "status", nullable = false, length = 20, columnDefinition = "varchar(20)")
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
}
