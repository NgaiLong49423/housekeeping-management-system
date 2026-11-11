package com.housekeeping.management.service.impl;

import com.housekeeping.management.dto.request.PortalForms.OperatingHoursForm;
import com.housekeeping.management.entity.CompanyOperatingHour;
import com.housekeeping.management.repository.CompanyOperatingHourRepository;
import com.housekeeping.management.repository.CompanySettingsRepository;
import com.housekeeping.management.service.CompanyOperatingHoursService;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CompanyOperatingHoursServiceImpl implements CompanyOperatingHoursService {
    private final CompanyOperatingHourRepository hours;
    private final CompanySettingsRepository settings;

    public CompanyOperatingHoursServiceImpl(CompanyOperatingHourRepository hours, CompanySettingsRepository settings) {
        this.hours = hours;
        this.settings = settings;
    }

    @Override
    @Transactional(readOnly = true)
    public List<OperatingHoursForm> forDate(LocalDate date) {
        List<com.housekeeping.management.entity.CompanySettings> companies = settings.findAllByOrderByCompanyIdAsc();
        if (companies.isEmpty()) return List.of();
        return hours.findEffectiveForDate(companies.getFirst().getCompanyId(), date).stream()
                .map(hour -> new OperatingHoursForm(hour.getDayOfWeek(), hour.isClosed(),
                        hour.getOpeningTime(), hour.getClosingTime())).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<LocalDate> versionDates() {
        return hours.findVersionDates();
    }

    @Override
    @Transactional(readOnly = true)
    public boolean hasAnySchedule() {
        return hours.count() > 0;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<LocalDate> exactVersionDate(LocalDate date) {
        return hours.existsByEffectiveFrom(date) ? Optional.of(date) : Optional.empty();
    }

    @Override
    @Transactional(readOnly = true)
    public LocalDate nextVersionDate(LocalDate date) {
        return hours.findNextVersionDate(date);
    }

    @Override
    @Transactional
    public void saveVersion(Long companyId, LocalDate effectiveFrom, List<OperatingHoursForm> schedule) {
        hours.deleteByCompanyIdAndEffectiveFrom(companyId, effectiveFrom);
        HashMap<Integer, Short> periods = new HashMap<>();
        List<CompanyOperatingHour> entities = new ArrayList<>(schedule.size());
        for (OperatingHoursForm row : schedule) {
            CompanyOperatingHour entity = new CompanyOperatingHour();
            entity.setCompanyId(companyId);
            entity.setEffectiveFrom(effectiveFrom);
            entity.setDayOfWeek(row.dayOfWeek());
            entity.setPeriodNo(periods.merge(row.dayOfWeek(), (short) 1, (first, increment) -> (short) (first + increment)));
            entity.setClosed(row.closed());
            entity.setOpeningTime(row.closed() ? null : row.openingTime());
            entity.setClosingTime(row.closed() ? null : row.closingTime());
            entities.add(entity);
        }
        hours.saveAll(entities);
    }

    @Override
    @Transactional
    public void deleteVersion(Long companyId, LocalDate effectiveFrom) {
        hours.deleteByCompanyIdAndEffectiveFrom(companyId, effectiveFrom);
    }

    @Override
    @Transactional
    public void lockSettingsForScheduling() {
        List<com.housekeeping.management.entity.CompanySettings> locked = settings.findAllForUpdate();
        if (locked.size() != 1) {
            throw new IllegalStateException("Cần cấu hình chính xác một vị trí và giờ hoạt động của công ty.");
        }
    }
}
