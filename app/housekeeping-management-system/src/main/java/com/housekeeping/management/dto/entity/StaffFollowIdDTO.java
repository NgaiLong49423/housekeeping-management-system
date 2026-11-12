package com.housekeeping.management.dto.entity;

import com.housekeeping.management.entity.StaffFollowId;

public record StaffFollowIdDTO(Long customerId, Long staffId) {
    public static StaffFollowIdDTO convertEntityToDTO(StaffFollowId entity) {
        if (entity == null) return null;
        return new StaffFollowIdDTO(entity.getCustomerId(), entity.getStaffId());
    }

    public StaffFollowId convertDTOToEntity() {
        return new StaffFollowId(customerId, staffId);
    }
}
