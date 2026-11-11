package com.housekeeping.management.dto.entity;

import com.housekeeping.management.entity.StaffRecoveryInstallment;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record StaffRecoveryInstallmentDTO(Long installmentId, Long actionId, BigDecimal amount,
        String paymentMethod, String transactionReference, String receiptPath, Long receivedBy, LocalDateTime receivedAt) {
    public static StaffRecoveryInstallmentDTO convertEntityToDTO(StaffRecoveryInstallment entity) {
        if (entity == null) return null;
        return new StaffRecoveryInstallmentDTO(entity.getInstallmentId(), entity.getActionId(), entity.getAmount(),
                entity.getPaymentMethod(), entity.getTransactionReference(), entity.getReceiptPath(), entity.getReceivedBy(), entity.getReceivedAt());
    }
    public StaffRecoveryInstallment convertDTOToEntity() {
        StaffRecoveryInstallment entity = new StaffRecoveryInstallment();
        entity.setInstallmentId(installmentId); entity.setActionId(actionId); entity.setAmount(amount);
        entity.setPaymentMethod(paymentMethod); entity.setTransactionReference(transactionReference);
        entity.setReceiptPath(receiptPath); entity.setReceivedBy(receivedBy); entity.setReceivedAt(receivedAt);
        return entity;
    }
}
