package com.housekeeping.management.dto.entity;

import com.housekeeping.management.entity.StaffAvailability;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

public record StaffAvailabilityDTO(Long availabilityId, Long staffId, LocalDate workDate, LocalTime startTime,
        LocalTime endTime, LocalDateTime createdAt, boolean active) {
    public static StaffAvailabilityDTO convertEntityToDTO(StaffAvailability entity) {
        if (entity == null) return null;
        return new StaffAvailabilityDTO(entity.getAvailabilityId(), entity.getStaffId(), entity.getWorkDate(),
                entity.getStartTime(), entity.getEndTime(), entity.getCreatedAt(), entity.isActive());
    }

    public StaffAvailability convertDTOToEntity() {
        StaffAvailability entity = new StaffAvailability();
        entity.setAvailabilityId(availabilityId);
        entity.setStaffId(staffId);
        entity.setWorkDate(workDate);
        entity.setStartTime(startTime);
        entity.setEndTime(endTime);
        entity.setCreatedAt(createdAt);
        entity.setActive(active);
        return entity;
    }
}
