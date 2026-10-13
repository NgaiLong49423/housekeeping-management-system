package com.housekeeping.management.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "Booking_Detail", uniqueConstraints = @UniqueConstraint(name = "UQ_Booking_Detail_Booking_Sequence", columnNames = {"booking_id", "sequence_no"}))
@Getter
@Setter
@NoArgsConstructor
public class BookingDetail {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "booking_detail_id", nullable = false)
    private Long bookingDetailId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "booking_id", nullable = false, foreignKey = @ForeignKey(name = "FK_Booking_Detail_Booking"))
    private Booking booking;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "work_schedule_id", nullable = false, foreignKey = @ForeignKey(name = "FK_Booking_Detail_Work_Schedule"))
    private WorkSchedule workSchedule;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "service_id", nullable = false, foreignKey = @ForeignKey(name = "FK_Booking_Detail_Services"))
    private Services service;

    @Column(name = "sequence_no", nullable = false)
    private Integer sequenceNo;

    @Column(name = "duration_minutes_snapshot", nullable = false)
    private Integer durationMinutesSnapshot;

    @Column(name = "unit_price_snapshot", precision = 18, scale = 0, nullable = false)
    private BigDecimal unitPriceSnapshot;
}
