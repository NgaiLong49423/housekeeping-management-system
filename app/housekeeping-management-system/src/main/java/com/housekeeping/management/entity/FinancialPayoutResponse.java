package com.housekeeping.management.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "Financial_Payout_Responses", schema = "dbo")
@Getter @Setter @NoArgsConstructor
public class FinancialPayoutResponse {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "payout_response_id", nullable = false)
    private Long payoutResponseId;

    @Column(name = "entry_id", nullable = false, unique = true)
    private Long entryId;

    @Column(name = "customer_id", nullable = false)
    private Long customerId;

    @Column(name = "response_status", nullable = false, length = 24, columnDefinition = "varchar(24)")
    private String responseStatus;

    @Column(name = "payout_method", length = 20, columnDefinition = "varchar(20)")
    private String payoutMethod;

    @Column(name = "encrypted_account_details", columnDefinition = "nvarchar(max)")
    private String encryptedAccountDetails;

    @Column(name = "responded_at", nullable = false)
    private LocalDateTime respondedAt;

    @Column(name = "customer_confirmed_at")
    private LocalDateTime customerConfirmedAt;

    @Column(name = "amount_agreed_at")
    private LocalDateTime amountAgreedAt;

    @Column(name = "payout_method_selected_at")
    private LocalDateTime payoutMethodSelectedAt;
}
