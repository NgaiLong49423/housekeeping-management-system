package com.housekeeping.management.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Nationalized;

@Entity
@Table(name = "CancellationLogs", schema = "dbo")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CancellationLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "log_id", nullable = false)
    private Long logId;

    @Column(name = "booking_id", nullable = false)
    private Long bookingId;

    @Column(name = "customer_id", nullable = false)
    private Long customerId;

    @Column(name = "worker_id")
    private Long workerId;

    @Column(name = "cancelled_by_type", length = 30, nullable = false)
    private String cancelledByType;

    @Column(name = "reason_code", length = 50, nullable = false)
    private String reasonCode;

    @Nationalized
    @Column(name = "reason_note", length = 1000)
    private String reasonNote;

    @Column(name = "booking_total_amount", precision = 12, scale = 2, nullable = false)
    private BigDecimal bookingTotalAmount;

    @Column(name = "cancellation_fee", precision = 12, scale = 2, nullable = false)
    private BigDecimal cancellationFee;

    @Column(name = "worker_compensation", precision = 12, scale = 2, nullable = false)
    private BigDecimal workerCompensation;

    @Column(name = "platform_fee", precision = 12, scale = 2, nullable = false)
    private BigDecimal platformFee;

    @Column(name = "refund_amount", precision = 12, scale = 2, nullable = false)
    private BigDecimal refundAmount;

    @Column(name = "delta_minutes_to_start", nullable = false)
    private Long deltaMinutesToStart;

    @Column(name = "is_worker_fault", nullable = false)
    private Boolean isWorkerFault = false;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
