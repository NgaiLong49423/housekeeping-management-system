package com.housekeeping.management.repository;

import com.housekeeping.management.domain.entity.FavoriteStaff;
import com.housekeeping.management.domain.entity.FavoriteStaffId;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FavoriteStaffRepository extends JpaRepository<FavoriteStaff, FavoriteStaffId> {
}
