package com.housekeeping.management.repository;

import com.housekeeping.management.entity.Staff;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StaffAccountRepository extends JpaRepository<Staff, Long> {
    Optional<Staff> findByUser_UserId(Long userId);
}
