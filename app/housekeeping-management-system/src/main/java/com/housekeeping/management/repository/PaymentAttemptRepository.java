package com.housekeeping.management.repository;

import com.housekeeping.management.domain.entity.PaymentAttempt;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PaymentAttemptRepository extends JpaRepository<PaymentAttempt, Long> {
    @Query(value = "SELECT NEXT VALUE FOR dbo.PayOS_Order_Code_Seq", nativeQuery = true)
    Long nextPayosOrderCode();

    List<PaymentAttempt> findByPayment_PaymentIdOrderByCreatedAtDescAttemptIdDesc(Long paymentId);

    Optional<PaymentAttempt> findTopByPayment_PaymentIdOrderByCreatedAtDescAttemptIdDesc(Long paymentId);

    Optional<PaymentAttempt> findByProviderOrderId(String providerOrderId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select attempt from PaymentAttempt attempt join fetch attempt.payment where attempt.providerOrderId = :orderId")
    Optional<PaymentAttempt> findByProviderOrderIdForUpdate(@Param("orderId") String providerOrderId);
}
