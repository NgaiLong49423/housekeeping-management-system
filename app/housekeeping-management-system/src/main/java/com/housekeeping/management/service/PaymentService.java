package com.housekeeping.management.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.springframework.security.access.AccessDeniedException;

public interface PaymentService {
    public record AccountingTotals(BigDecimal paidAmount, BigDecimal cashAmount, BigDecimal onlineAmount) { }
    public record AccountingPayment(Long paymentId, Long bookingId, BigDecimal amount, String method,
            String transactionCode, LocalDateTime paidAt, boolean simulated) { }
    public record OnlineCheckout(long orderCode, long amount, LocalDateTime createdAt, boolean recoverExisting) { }
    public record OnlinePayment(Long paymentId, Long bookingId, BigDecimal amount, String method, String status,
            String transactionCode, LocalDateTime createdAt) { }
    public OnlineCheckout prepareOnlineCheckout(Long bookingId, Long customerId);
    public Optional<OnlinePayment> findOnlinePayment(long orderCode);
    public void confirmCashReceived(Long bookingId, Long assignedStaffId);
    public void confirmOnlinePayment(long orderCode, BigDecimal amount);
    public void failOnlinePayment(long orderCode, BigDecimal amount);
    AccountingTotals accountingTotals();
    List<AccountingPayment> successfulPaymentsForAccounting();
}
