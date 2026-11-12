package com.housekeeping.management.repository;

import com.housekeeping.management.entity.CancellationLog;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CancellationLogRepository extends JpaRepository<CancellationLog, Long> {
    List<CancellationLog> findAllByCustomerIdOrderByCreatedAtDesc(Long customerId);

    List<CancellationLog> findAllByCustomerIdAndCreatedAtGreaterThanEqualOrderByCreatedAtDesc(
            Long customerId, LocalDateTime since);

    @Query("""
        SELECT COUNT(c) FROM CancellationLog c 
        WHERE c.customerId = :customerId 
          AND c.cancelledByType = 'CUSTOMER'
          AND c.createdAt >= :since
    """)
    long countCustomerCancellationsWithinDays(
        @Param("customerId") Long customerId, 
        @Param("since") LocalDateTime since
    );
}
