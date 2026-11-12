package com.housekeeping.management.repository;

import com.housekeeping.management.enums.ServiceStatus;
import com.housekeeping.management.entity.Services;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ServicesRepository extends JpaRepository<Services, Long> {
    java.util.List<Services> findAllByOrderByServiceNameAsc();
    java.util.List<Services> findAllByStatusOrderByServiceNameAsc(ServiceStatus status);
}
