package com.housekeeping.management.repository;


import org.springframework.data.repository.NoRepositoryBean;
import com.housekeeping.management.domain.entity.FavoriteStaff;
import com.housekeeping.management.domain.entity.FavoriteStaffId;
import org.springframework.data.jpa.repository.JpaRepository;

@NoRepositoryBean
public interface FavoriteStaffRepository extends JpaRepository<FavoriteStaff, FavoriteStaffId> {
}
