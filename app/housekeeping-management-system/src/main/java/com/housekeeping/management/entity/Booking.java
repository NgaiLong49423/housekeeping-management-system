package com.housekeeping.management.entity;

import com.housekeeping.management.domain.BookingStatus;
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
}
