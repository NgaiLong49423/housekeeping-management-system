package com.housekeeping.management.repository;


import org.springframework.data.repository.NoRepositoryBean;
import com.housekeeping.management.domain.entity.Staff;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

@NoRepositoryBean
public interface StaffRepository extends JpaRepository<Staff, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select staff from Staff staff where staff.staffId = :staffId")
    Optional<Staff> findByIdForUpdate(@Param("staffId") Long staffId);
}
