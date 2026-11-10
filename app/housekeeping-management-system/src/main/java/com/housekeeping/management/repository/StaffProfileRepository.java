package com.housekeeping.management.repository;

import com.housekeeping.management.entity.StaffProfile;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StaffProfileRepository extends JpaRepository<StaffProfile, Long> {
    Optional<StaffProfile> findByStaffId(Long staffId);
}
