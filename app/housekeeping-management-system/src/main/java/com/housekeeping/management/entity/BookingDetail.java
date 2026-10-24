package com.housekeeping.management.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Nationalized;

@Entity
@Table(name = "Booking_Details", schema = "dbo")
@Getter @Setter @NoArgsConstructor
public class BookingDetail {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "booking_detail_id", nullable = false)
    private Long bookingDetailId;

    @Column(name = "booking_id", nullable = false)
    private Long bookingId;

    @Column(name = "service_id", nullable = false)
    private Long serviceId;

    @Nationalized @Column(name = "service_name_snapshot", length = 150, nullable = false)
    private String serviceNameSnapshot;

    @Column(name = "unit_price", precision = 12, scale = 2, nullable = false)
    private BigDecimal unitPrice;

    @Column(name = "duration_minutes", nullable = false)
    private Integer durationMinutes;
}
