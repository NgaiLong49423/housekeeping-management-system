package com.housekeeping.management.dto.entity;

import com.housekeeping.management.entity.BookingFinancialEntry;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record BookingFinancialEntryDTO(Long entryId, Long bookingId, Long caseId, String entryType,
        BigDecimal amount, String currency, String reason, String status, String paymentMethod,
        String transactionReference, String receiptPath, boolean simulated, Long createdBy,
        Long approvedBy, Long settledBy, LocalDateTime createdAt, LocalDateTime approvedAt,
        LocalDateTime paidAt, Long cancelledBy, String cancellationReason, String rejectionReason) {
    public static BookingFinancialEntryDTO convertEntityToDTO(BookingFinancialEntry entity) {
        if (entity == null) return null;
        return new BookingFinancialEntryDTO(entity.getEntryId(), entity.getBookingId(), entity.getCaseId(),
                entity.getEntryType(), entity.getAmount(), entity.getCurrency(), entity.getReason(), entity.getStatus(),
                entity.getPaymentMethod(), entity.getTransactionReference(), entity.getReceiptPath(), entity.isSimulated(),
                entity.getCreatedBy(), entity.getApprovedBy(), entity.getSettledBy(), entity.getCreatedAt(),
                entity.getApprovedAt(), entity.getPaidAt(), entity.getCancelledBy(), entity.getCancellationReason(),
                entity.getRejectionReason());
    }

    public BookingFinancialEntry convertDTOToEntity() {
        BookingFinancialEntry entity = new BookingFinancialEntry();
        entity.setEntryId(entryId);
        entity.setBookingId(bookingId);
        entity.setCaseId(caseId);
        entity.setEntryType(entryType);
        entity.setAmount(amount);
        entity.setCurrency(currency == null ? "VND" : currency);
        entity.setReason(reason);
        entity.setStatus(status == null ? "PROPOSED" : status);
        entity.setPaymentMethod(paymentMethod);
        entity.setTransactionReference(transactionReference);
        entity.setReceiptPath(receiptPath);
        entity.setSimulated(simulated);
        entity.setCreatedBy(createdBy);
        entity.setApprovedBy(approvedBy);
        entity.setSettledBy(settledBy);
        entity.setCreatedAt(createdAt);
        entity.setApprovedAt(approvedAt);
        entity.setPaidAt(paidAt);
        entity.setCancelledBy(cancelledBy);
        entity.setCancellationReason(cancellationReason);
        entity.setRejectionReason(rejectionReason);
        return entity;
    }
}
