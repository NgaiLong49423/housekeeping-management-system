package com.housekeeping.management.repository;


import org.springframework.data.repository.NoRepositoryBean;
import com.housekeeping.management.domain.entity.WorkSchedule;
import org.springframework.data.jpa.repository.JpaRepository;

@NoRepositoryBean
public interface WorkScheduleRepository extends JpaRepository<WorkSchedule, Long> {
}
