package com.housekeeping.management.domain.entity;

import com.housekeeping.management.domain.PaymentAttemptStatus;
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
@Table(name = "Payment_Attempt", uniqueConstraints = {
        @UniqueConstraint(name = "UQ_Payment_Attempt_Order", columnNames = "provider_order_id"),
        @UniqueConstraint(name = "UQ_Payment_Attempt_Request", columnNames = "request_id")
})
@Getter
@Setter
@NoArgsConstructor
public class PaymentAttempt {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "attempt_id", nullable = false)
    private Long attemptId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payment_id", nullable = false, foreignKey = @ForeignKey(name = "FK_Payment_Attempt_Payment"))
    private Payment payment;

    @Column(name = "provider_order_id", length = 64, nullable = false)
    private String providerOrderId;

    @Column(name = "request_id", length = 64, nullable = false)
    private String requestId;

    @Column(name = "provider_transaction_id")
    private String providerTransactionId;

    @Column(name = "provider_payment_link_id", length = 100)
    private String providerPaymentLinkId;

    @Column(name = "amount", precision = 18, scale = 0, nullable = false)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "attempt_status", length = 12, nullable = false, columnDefinition = "varchar(12)")
    private PaymentAttemptStatus status;

    @Column(name = "checkout_url", length = 2000)
    private String checkoutUrl;

    @Column(name = "created_at", nullable = false, columnDefinition = "datetimeoffset(0)")
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false, columnDefinition = "datetimeoffset(0)")
    private OffsetDateTime updatedAt;
}
