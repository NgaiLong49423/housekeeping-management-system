package com.housekeeping.management.repository;

import com.housekeeping.management.domain.entity.Services;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ServicesRepository extends JpaRepository<Services, Long> {
}
