package com.housekeeping.management.repository;

import com.housekeeping.management.entity.StaffFollow;
import com.housekeeping.management.entity.StaffFollowId;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StaffFollowRepository extends JpaRepository<StaffFollow, StaffFollowId> {
}
