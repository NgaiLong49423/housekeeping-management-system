package com.housekeeping.management.domain.entity;

import com.housekeeping.management.domain.PaymentMethod;
import com.housekeeping.management.domain.PaymentStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "Payment", uniqueConstraints = @UniqueConstraint(name = "UQ_Payment_Booking", columnNames = "booking_id"))
@Getter
@Setter
@NoArgsConstructor
public class Payment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "payment_id", nullable = false)
    private Long paymentId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "booking_id", nullable = false, foreignKey = @ForeignKey(name = "FK_Payment_Booking"))
    private Booking booking;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", length = 20, columnDefinition = "varchar(20)")
    private PaymentMethod paymentMethod;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_status", length = 10, nullable = false, columnDefinition = "varchar(10)")
    private PaymentStatus paymentStatus;

    @Column(name = "amount", precision = 18, scale = 0, nullable = false)
    private BigDecimal amount;

    @Column(name = "created_at", nullable = false, columnDefinition = "datetimeoffset(0)")
    private OffsetDateTime createdAt;

    @Column(name = "verified_at", columnDefinition = "datetimeoffset(0)")
    private OffsetDateTime verifiedAt;
}
