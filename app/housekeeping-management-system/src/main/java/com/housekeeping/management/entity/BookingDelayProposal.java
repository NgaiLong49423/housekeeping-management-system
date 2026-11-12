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
@Table(name = "Booking_Delay_Proposals", schema = "dbo")
@Getter @Setter @NoArgsConstructor
public class BookingDelayProposal {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "proposal_id", nullable = false)
    private Long proposalId;
    @Column(name = "booking_id", nullable = false)
    private Long bookingId;
    @Column(name = "staff_id", nullable = false)
    private Long staffId;
    @Column(name = "expected_arrival_at", nullable = false)
    private LocalDateTime expectedArrivalAt;
    @Nationalized @Column(name = "reason", nullable = false, length = 500)
    private String reason;
    @Column(name = "proposed_at", nullable = false)
    private LocalDateTime proposedAt;
    @Column(name = "status", nullable = false, length = 10, columnDefinition = "varchar(10)")
    private String status;
    @Column(name = "responded_at")
    private LocalDateTime respondedAt;
}
