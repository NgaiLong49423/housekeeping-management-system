package com.housekeeping.management.repository;

import com.housekeeping.management.domain.PaymentStatus;
import com.housekeeping.management.entity.Payment;
import jakarta.persistence.LockModeType;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PaymentPersistenceRepository extends JpaRepository<Payment, Long> {
    List<Payment> findAllByBookingIdInOrderByCreatedAtDescPaymentIdDesc(List<Long> bookingIds);
    List<Payment> findAllByStatusOrderByPaidAtDescPaymentIdDesc(PaymentStatus status);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<Payment> findByBookingIdOrderByCreatedAtDescPaymentIdDesc(Long bookingId);

    Optional<Payment> findByTransactionCode(String transactionCode);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select payment from Payment payment where payment.transactionCode=:transactionCode")
    Optional<Payment> findByTransactionCodeForUpdate(@Param("transactionCode") String transactionCode);

    boolean existsByBookingIdAndStatus(Long bookingId, PaymentStatus status);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Payment payment set payment.status=com.housekeeping.management.domain.PaymentStatus.FAILED "
            + "where payment.bookingId=:bookingId and payment.paymentMethod=com.housekeeping.management.domain.PaymentMethod.ONLINE "
            + "and payment.status=com.housekeeping.management.domain.PaymentStatus.PENDING")
    int failPendingOnlineAttempts(@Param("bookingId") Long bookingId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Payment payment set payment.status=com.housekeeping.management.domain.PaymentStatus.SUCCESS, payment.paidAt=:paidAt "
            + "where payment.paymentId=:paymentId and payment.paymentMethod=com.housekeeping.management.domain.PaymentMethod.ONLINE "
            + "and payment.status in (com.housekeeping.management.domain.PaymentStatus.PENDING, com.housekeeping.management.domain.PaymentStatus.FAILED)")
    int markSuccess(@Param("paymentId") Long paymentId, @Param("paidAt") java.time.LocalDateTime paidAt);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Payment payment set payment.status=com.housekeeping.management.domain.PaymentStatus.FAILED "
            + "where payment.paymentId=:paymentId and payment.paymentMethod=com.housekeeping.management.domain.PaymentMethod.ONLINE "
            + "and payment.status=com.housekeeping.management.domain.PaymentStatus.PENDING")
    int markFailed(@Param("paymentId") Long paymentId);

    @Query("select coalesce(sum(payment.amount), 0) from Payment payment "
            + "where payment.status=com.housekeeping.management.domain.PaymentStatus.SUCCESS and payment.simulated=false")
    BigDecimal sumSuccessfulRealPayments();

    @Query("select coalesce(sum(payment.amount), 0) from Payment payment "
            + "where payment.status=com.housekeeping.management.domain.PaymentStatus.SUCCESS and payment.simulated=true")
    BigDecimal sumSuccessfulSimulatedPayments();
}
