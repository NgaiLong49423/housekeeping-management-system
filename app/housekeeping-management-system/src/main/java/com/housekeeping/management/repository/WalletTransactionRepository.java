package com.housekeeping.management.repository;

import com.housekeeping.management.entity.WalletTransaction;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WalletTransactionRepository extends JpaRepository<WalletTransaction, Long> {
    List<WalletTransaction> findAllByUserIdOrderByCreatedAtDesc(Long userId);
}
