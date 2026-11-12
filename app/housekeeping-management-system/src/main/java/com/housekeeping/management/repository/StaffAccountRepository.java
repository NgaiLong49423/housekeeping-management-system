package com.housekeeping.management.repository;

import com.housekeeping.management.entity.Staff;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StaffAccountRepository extends JpaRepository<Staff, Long> {
    java.util.List<Staff> findAllByOrderByUser_FullNameAsc();
    java.util.List<Staff> findAllByUser_StatusOrderByUser_FullNameAsc(com.housekeeping.management.enums.UserStatus status);
    java.util.List<Staff> findAllByStaffIdIn(java.util.Collection<Long> staffIds);
    Optional<Staff> findByUser_UserId(Long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select staff from Staff staff join fetch staff.user where staff.user.userId=:userId and staff.user.role=com.housekeeping.management.enums.UserRole.STAFF and staff.user.status=com.housekeeping.management.enums.UserStatus.ACTIVE")
    Optional<Staff> findActiveByUserIdForUpdate(@Param("userId") Long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select staff from Staff staff where staff.staffId=:staffId")
    Optional<Staff> findByStaffIdForUpdate(@Param("staffId") Long staffId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select staff from Staff staff join fetch staff.user where staff.staffId=:staffId and staff.user.role=com.housekeeping.management.enums.UserRole.STAFF and staff.user.status=com.housekeeping.management.enums.UserStatus.ACTIVE")
    Optional<Staff> findActiveByStaffIdForUpdate(@Param("staffId") Long staffId);
}
