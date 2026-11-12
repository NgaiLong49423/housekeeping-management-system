package com.housekeeping.management.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Nationalized;

@Entity
@Table(name = "Staff_Recovery_Installments", schema = "dbo")
@Getter @Setter @NoArgsConstructor
public class StaffRecoveryInstallment {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "installment_id", nullable = false) private Long installmentId;
    @Column(name = "action_id", nullable = false) private Long actionId;
    @Column(name = "amount", nullable = false, precision = 12, scale = 2) private BigDecimal amount;
    @Column(name = "payment_method", nullable = false, length = 20, columnDefinition = "varchar(20)") private String paymentMethod;
    @Nationalized @Column(name = "transaction_reference", nullable = false, length = 255) private String transactionReference;
    @Nationalized @Column(name = "receipt_path", nullable = false, length = 500) private String receiptPath;
    @Column(name = "received_by", nullable = false) private Long receivedBy;
    @Column(name = "received_at", nullable = false) private LocalDateTime receivedAt;
}
