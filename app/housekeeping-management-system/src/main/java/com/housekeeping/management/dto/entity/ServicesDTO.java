package com.housekeeping.management.dto.entity;

import com.housekeeping.management.enums.ServiceStatus;
import com.housekeeping.management.entity.Services;
import java.math.BigDecimal;

public record ServicesDTO(Long serviceId, String serviceName, String description, String imageUrl,
        Integer durationMinutes, BigDecimal price, ServiceStatus status) {
    public static ServicesDTO convertEntityToDTO(Services entity) {
        if (entity == null) return null;
        return new ServicesDTO(entity.getServiceId(), entity.getServiceName(), entity.getDescription(),
                entity.getImageUrl(), entity.getDurationMinutes(), entity.getPrice(), entity.getStatus());
    }

    public Services convertDTOToEntity() {
        return convertDTOToEntity(new Services());
    }

    public Services convertDTOToEntity(Services entity) {
        if (entity == null) entity = new Services();
        entity.setServiceId(serviceId);
        entity.setServiceName(serviceName);
        entity.setDescription(description);
        entity.setImageUrl(imageUrl);
        entity.setDurationMinutes(durationMinutes);
        entity.setPrice(price);
        entity.setStatus(status);
        return entity;
    }
}
