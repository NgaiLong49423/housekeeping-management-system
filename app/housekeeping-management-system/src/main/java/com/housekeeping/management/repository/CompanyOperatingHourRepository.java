package com.housekeeping.management.repository;

import com.housekeeping.management.entity.CompanyOperatingHour;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CompanyOperatingHourRepository extends JpaRepository<CompanyOperatingHour, Long> {
    @Query("select hour from CompanyOperatingHour hour where hour.companyId=:companyId and hour.effectiveFrom="
            + "(select max(version.effectiveFrom) from CompanyOperatingHour version where version.companyId=:companyId "
            + "and version.effectiveFrom<=:date) order by hour.dayOfWeek,hour.periodNo")
    List<CompanyOperatingHour> findEffectiveForDate(@Param("companyId") Long companyId,
            @Param("date") LocalDate date);

    @Query("select distinct hour.effectiveFrom from CompanyOperatingHour hour order by hour.effectiveFrom desc")
    List<LocalDate> findVersionDates();

    boolean existsByEffectiveFrom(LocalDate effectiveFrom);

    @Query("select min(hour.effectiveFrom) from CompanyOperatingHour hour where hour.effectiveFrom>:date")
    LocalDate findNextVersionDate(@Param("date") LocalDate date);

    void deleteByCompanyIdAndEffectiveFrom(Long companyId, LocalDate effectiveFrom);
}
