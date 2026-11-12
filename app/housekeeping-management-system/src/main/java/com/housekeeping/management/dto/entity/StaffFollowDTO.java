package com.housekeeping.management.dto.entity;

import com.housekeeping.management.entity.StaffFollow;
import java.time.LocalDateTime;

public record StaffFollowDTO(StaffFollowIdDTO id, LocalDateTime createdAt) {
    public static StaffFollowDTO convertEntityToDTO(StaffFollow entity) {
        if (entity == null) return null;
        return new StaffFollowDTO(StaffFollowIdDTO.convertEntityToDTO(entity.getId()), entity.getCreatedAt());
    }

    public StaffFollow convertDTOToEntity() {
        StaffFollow entity = new StaffFollow();
        entity.setId(id == null ? null : id.convertDTOToEntity());
        entity.setCreatedAt(createdAt);
        return entity;
    }
}
