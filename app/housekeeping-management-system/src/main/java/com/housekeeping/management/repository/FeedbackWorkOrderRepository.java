package com.housekeeping.management.repository;

import com.housekeeping.management.entity.FeedbackWorkOrder;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;

public interface FeedbackWorkOrderRepository extends JpaRepository<FeedbackWorkOrder,Long> {
    List<FeedbackWorkOrder> findAllByCaseIdOrderByWorkOrderIdAsc(Long caseId);
    Optional<FeedbackWorkOrder> findByActionId(Long actionId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<FeedbackWorkOrder> findFirstByWorkOrderId(Long id);
}
