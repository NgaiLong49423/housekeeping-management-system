package com.housekeeping.management.repository;

import com.housekeeping.management.entity.FinancialPayoutResponse;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FinancialPayoutResponseRepository extends JpaRepository<FinancialPayoutResponse, Long> {
    Optional<FinancialPayoutResponse> findByEntryId(Long entryId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select response from FinancialPayoutResponse response where response.entryId=:entryId")
    Optional<FinancialPayoutResponse> findByEntryIdForUpdate(@Param("entryId") Long entryId);

    List<FinancialPayoutResponse> findAllByEntryIdIn(List<Long> entryIds);
}
