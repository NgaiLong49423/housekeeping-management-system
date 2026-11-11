package com.housekeeping.management.service;

import com.housekeeping.management.dto.request.PortalForms.OperatingHoursForm;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface CompanyOperatingHoursService {
    List<OperatingHoursForm> forDate(LocalDate date);
    List<LocalDate> versionDates();
    boolean hasAnySchedule();
    Optional<LocalDate> exactVersionDate(LocalDate date);
    LocalDate nextVersionDate(LocalDate date);
    void saveVersion(Long companyId, LocalDate effectiveFrom, List<OperatingHoursForm> hours);
    void deleteVersion(Long companyId, LocalDate effectiveFrom);
    void lockSettingsForScheduling();
}
