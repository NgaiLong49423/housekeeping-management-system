package com.housekeeping.management.dto.entity;

import com.housekeeping.management.domain.PaymentMethod;
import com.housekeeping.management.domain.PaymentStatus;
import com.housekeeping.management.entity.Payment;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record PaymentDTO(Long paymentId, Long bookingId, BigDecimal amount, PaymentMethod paymentMethod,
        PaymentStatus status, String transactionCode, LocalDateTime createdAt, LocalDateTime paidAt, boolean simulated) {
    public static PaymentDTO convertEntityToDTO(Payment entity) {
        if (entity == null) return null;
        return new PaymentDTO(entity.getPaymentId(), entity.getBookingId(), entity.getAmount(), entity.getPaymentMethod(),
                entity.getStatus(), entity.getTransactionCode(), entity.getCreatedAt(), entity.getPaidAt(), entity.isSimulated());
    }

    public Payment convertDTOToEntity() {
        Payment entity = new Payment();
        entity.setPaymentId(paymentId);
        entity.setBookingId(bookingId);
        entity.setAmount(amount);
        entity.setPaymentMethod(paymentMethod);
        entity.setStatus(status);
        entity.setTransactionCode(transactionCode);
        entity.setCreatedAt(createdAt);
        entity.setPaidAt(paidAt);
        entity.setSimulated(simulated);
        return entity;
    }
}
