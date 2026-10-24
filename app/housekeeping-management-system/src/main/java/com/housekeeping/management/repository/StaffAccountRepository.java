package com.housekeeping.management.repository;

import com.housekeeping.management.entity.Staff;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StaffAccountRepository extends JpaRepository<Staff, Long> {
    Optional<Staff> findByUser_UserId(Long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select staff from Staff staff where staff.staffId=:staffId")
    Optional<Staff> findByStaffIdForUpdate(@Param("staffId") Long staffId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select staff from Staff staff join fetch staff.user where staff.staffId=:staffId and staff.user.status=com.housekeeping.management.domain.UserStatus.ACTIVE")
    Optional<Staff> findActiveByStaffIdForUpdate(@Param("staffId") Long staffId);
}
