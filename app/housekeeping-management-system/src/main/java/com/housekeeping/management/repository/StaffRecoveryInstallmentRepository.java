package com.housekeeping.management.repository;

import com.housekeeping.management.entity.StaffRecoveryInstallment;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StaffRecoveryInstallmentRepository extends JpaRepository<StaffRecoveryInstallment, Long> {
    List<StaffRecoveryInstallment> findAllByActionIdOrderByReceivedAtAscInstallmentIdAsc(Long actionId);
    @Query("select coalesce(sum(payment.amount), 0) from StaffRecoveryInstallment payment where payment.actionId=:actionId")
    BigDecimal sumByActionId(@Param("actionId") Long actionId);
    @Query("select coalesce(sum(payment.amount), 0) from StaffRecoveryInstallment payment")
    BigDecimal sumAllReceived();
}
