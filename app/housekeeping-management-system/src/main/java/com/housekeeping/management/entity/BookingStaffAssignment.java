package com.housekeeping.management.entity;

import com.housekeeping.management.enums.BookingStaffAssignmentStatus;
import com.housekeeping.management.enums.BookingStaffRole;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "Booking_Staff_Assignments", schema = "dbo")
@Getter
@Setter
@NoArgsConstructor
public class BookingStaffAssignment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "booking_staff_id", nullable = false)
    private Long bookingStaffId;

    @Column(name = "booking_id", nullable = false)
    private Long bookingId;

    @Column(name = "staff_id", nullable = false)
    private Long staffId;

    @Enumerated(EnumType.STRING)
    @Column(name = "assignment_role", length = 10, nullable = false, columnDefinition = "varchar(10)")
    private BookingStaffRole role = BookingStaffRole.MEMBER;

    @Enumerated(EnumType.STRING)
    @Column(name = "assignment_status", length = 20, nullable = false, columnDefinition = "varchar(20)")
    private BookingStaffAssignmentStatus status = BookingStaffAssignmentStatus.ASSIGNED;

    @Column(name = "assigned_at", nullable = false)
    private LocalDateTime assignedAt;

    @Column(name = "arrived_at")
    private LocalDateTime arrivedAt;
}
