package com.housekeeping.management.entity;

import com.housekeeping.management.enums.BookingStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Nationalized;

@Entity
@Table(name = "Bookings", schema = "dbo")
@Getter @Setter @NoArgsConstructor
public class Booking {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "booking_id", nullable = false)
    private Long bookingId;

    @Column(name = "customer_id", nullable = false)
    private Long customerId;

    @Column(name = "staff_id", nullable = false)
    private Long staffId;

    @Column(name = "original_staff_id", nullable = false, updatable = false)
    private Long originalStaffId;

    @Column(name = "house_id", nullable = false)
    private Long houseId;

    @Nationalized @Column(name = "address_snapshot", length = 500, nullable = false)
    private String addressSnapshot;

    @Column(name = "latitude_snapshot", precision = 10, scale = 7)
    private BigDecimal latitudeSnapshot;

    @Column(name = "longitude_snapshot", precision = 10, scale = 7)
    private BigDecimal longitudeSnapshot;

    @Nationalized @Column(name = "address_note_snapshot", length = 500)
    private String addressNoteSnapshot;

    @Column(name = "start_time", nullable = false)
    private LocalDateTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalDateTime endTime;

    @Column(name = "total_duration_minutes", nullable = false)
    private Integer totalDurationMinutes;

    @Column(name = "total_amount", precision = 12, scale = 2, nullable = false)
    private BigDecimal totalAmount;

    @Nationalized @Column(name = "area_note", columnDefinition = "nvarchar(max)")
    private String areaNote;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false, columnDefinition = "varchar(20)")
    private BookingStatus status = BookingStatus.CONFIRMED;

    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;

    @Column(name = "cancelled_by")
    private Long cancelledBy;

    @Nationalized @Column(name = "cancellation_reason", length = 500)
    private String cancellationReason;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "customer_arrived_at")
    private LocalDateTime customerArrivedAt;

    @Column(name = "staff_arrived_at")
    private LocalDateTime staffArrivedAt;

    @Column(name = "customer_delay_reported_at")
    private LocalDateTime customerDelayReportedAt;

    @Column(name = "arrival_verified_at")
    private LocalDateTime arrivalVerifiedAt;

    @Column(name = "arrival_verification_recorded_at")
    private LocalDateTime arrivalVerificationRecordedAt;

    @Column(name = "arrival_verified_by")
    private Long arrivalVerifiedBy;

    @Column(name = "arrival_verification_source", length = 10, columnDefinition = "varchar(10)")
    private String arrivalVerificationSource;

    @Nationalized @Column(name = "arrival_verification_note", length = 500)
    private String arrivalVerificationNote;

    @Column(name = "incident_reported_at")
    private LocalDateTime incidentReportedAt;

    @Nationalized @Column(name = "incident_note", length = 500)
    private String incidentNote;

    @Column(name = "incident_expected_arrival_at")
    private LocalDateTime incidentExpectedArrivalAt;

    @Column(name = "incident_customer_response", length = 10, columnDefinition = "varchar(10)")
    private String incidentCustomerResponse;

    @Column(name = "incident_customer_responded_at")
    private LocalDateTime incidentCustomerRespondedAt;

    @Column(name = "work_started_at")
    private LocalDateTime workStartedAt;

    @Column(name = "service_completed_at")
    private LocalDateTime serviceCompletedAt;

    @Column(name = "delay_minutes")
    private Integer delayMinutes;

    @Column(name = "delay_discount_percent", precision = 5, scale = 2, nullable = false)
    private BigDecimal delayDiscountPercent = BigDecimal.ZERO;

    @Column(name = "delay_discount_amount", precision = 12, scale = 2, nullable = false)
    private BigDecimal delayDiscountAmount = BigDecimal.ZERO;
}
