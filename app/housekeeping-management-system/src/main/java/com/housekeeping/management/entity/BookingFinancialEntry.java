package com.housekeeping.management.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
@Table(name = "Booking_Financial_Entries", schema = "dbo")
@Getter @Setter @NoArgsConstructor
public class BookingFinancialEntry {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "entry_id", nullable = false)
    private Long entryId;

    @Column(name = "booking_id", nullable = false)
    private Long bookingId;

    @Column(name = "case_id")
    private Long caseId;

    @Column(name = "resolution_action_id")
    private Long resolutionActionId;

    @Column(name = "entry_type", nullable = false, length = 30, columnDefinition = "varchar(30)")
    private String entryType;

    @Column(name = "amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(name = "currency", nullable = false, length = 3, columnDefinition = "char(3)")
    private String currency = "VND";

    @Nationalized @Column(name = "reason", nullable = false, length = 1000)
    private String reason;

    @Column(name = "status", nullable = false, length = 20, columnDefinition = "varchar(20)")
    private String status = "PROPOSED";

    @Column(name = "payment_method", length = 20, columnDefinition = "varchar(20)")
    private String paymentMethod;

    @Nationalized @Column(name = "transaction_reference", length = 255)
    private String transactionReference;

    @Nationalized @Column(name = "receipt_path", length = 500)
    private String receiptPath;

    @Column(name = "is_simulated", nullable = false)
    private boolean simulated;

    @Column(name = "created_by", nullable = false)
    private Long createdBy;

    @Column(name = "approved_by")
    private Long approvedBy;

    @Column(name = "settled_by")
    private Long settledBy;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    @Column(name = "cancelled_by")
    private Long cancelledBy;

    @Nationalized @Column(name = "cancellation_reason", length = 1000)
    private String cancellationReason;

    @Nationalized @Column(name = "rejection_reason", length = 1000)
    private String rejectionReason;
}
