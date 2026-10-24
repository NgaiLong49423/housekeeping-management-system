package com.housekeeping.management.repository;

import com.housekeeping.management.entity.StaffLeave;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StaffLeaveRepository extends JpaRepository<StaffLeave, Long> {
    List<StaffLeave> findAllByStaff_StaffIdOrderByCreatedAtDesc(Long staffId);

    @Query("select request from StaffLeave request join fetch request.staff staff join fetch staff.user order by request.createdAt desc, request.leaveId desc")
    List<StaffLeave> findAllForAdmin();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select request from StaffLeave request where request.leaveId = :leaveId")
    Optional<StaffLeave> findByIdForUpdate(@Param("leaveId") Long leaveId);
}
