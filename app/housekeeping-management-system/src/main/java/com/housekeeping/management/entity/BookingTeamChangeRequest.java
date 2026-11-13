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
@Table(name = "Booking_Team_Change_Requests", schema = "dbo")
@Getter @Setter @NoArgsConstructor
public class BookingTeamChangeRequest {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "request_id", nullable = false)
    private Long requestId;

    @Column(name = "booking_id", nullable = false)
    private Long bookingId;

    @Column(name = "requested_by", nullable = false)
    private Long requestedBy;

    @Column(name = "affected_staff_id", nullable = false)
    private Long affectedStaffId;

    @Column(name = "proposed_staff_count", nullable = false)
    private Integer proposedStaffCount;

    @Nationalized @Column(name = "reason", length = 1000, nullable = false)
    private String reason;

    @Column(name = "request_status", length = 30, nullable = false, columnDefinition = "varchar(30)")
    private String requestStatus = "PENDING";

    @Column(name = "resolution", length = 30, columnDefinition = "varchar(30)")
    private String resolution;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    @Column(name = "resolved_by")
    private Long resolvedBy;
}
