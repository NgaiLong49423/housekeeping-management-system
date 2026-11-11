package com.housekeeping.management.repository;

import com.housekeeping.management.entity.CompanySettings;
import jakarta.persistence.LockModeType;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface CompanySettingsRepository extends JpaRepository<CompanySettings, Long> {
    List<CompanySettings> findAllByOrderByCompanyIdAsc();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select settings from CompanySettings settings order by settings.companyId")
    List<CompanySettings> findAllForUpdate();
}
