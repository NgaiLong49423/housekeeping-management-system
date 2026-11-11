package com.housekeeping.management.dto.entity;

import com.housekeeping.management.entity.CompanyOperatingHour;
import java.time.LocalDate;
import java.time.LocalTime;

public record CompanyOperatingHourDTO(Long operatingHourId, Long companyId, LocalDate effectiveFrom,
        Integer dayOfWeek, Short periodNo, LocalTime openingTime, LocalTime closingTime, boolean closed) {
    public static CompanyOperatingHourDTO convertEntityToDTO(CompanyOperatingHour entity) {
        if (entity == null) return null;
        return new CompanyOperatingHourDTO(entity.getOperatingHourId(), entity.getCompanyId(), entity.getEffectiveFrom(),
                entity.getDayOfWeek(), entity.getPeriodNo(), entity.getOpeningTime(), entity.getClosingTime(), entity.isClosed());
    }

    public CompanyOperatingHour convertDTOToEntity() {
        CompanyOperatingHour entity = new CompanyOperatingHour();
        entity.setOperatingHourId(operatingHourId);
        entity.setCompanyId(companyId);
        entity.setEffectiveFrom(effectiveFrom);
        entity.setDayOfWeek(dayOfWeek);
        entity.setPeriodNo(periodNo);
        entity.setOpeningTime(openingTime);
        entity.setClosingTime(closingTime);
        entity.setClosed(closed);
        return entity;
    }
}
