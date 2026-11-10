package com.housekeeping.management.dto.entity;

import com.housekeeping.management.entity.CompanySettings;
import java.math.BigDecimal;

public record CompanySettingsDTO(Long companyId, String companyAddress, BigDecimal latitude, BigDecimal longitude,
        BigDecimal serviceRadiusKm) {
    public static CompanySettingsDTO convertEntityToDTO(CompanySettings entity) {
        if (entity == null) return null;
        return new CompanySettingsDTO(entity.getCompanyId(), entity.getCompanyAddress(), entity.getLatitude(),
                entity.getLongitude(), entity.getServiceRadiusKm());
    }

    public CompanySettings convertDTOToEntity() {
        CompanySettings entity = new CompanySettings();
        entity.setCompanyId(companyId);
        entity.setCompanyAddress(companyAddress);
        entity.setLatitude(latitude);
        entity.setLongitude(longitude);
        entity.setServiceRadiusKm(serviceRadiusKm);
        return entity;
    }
}
