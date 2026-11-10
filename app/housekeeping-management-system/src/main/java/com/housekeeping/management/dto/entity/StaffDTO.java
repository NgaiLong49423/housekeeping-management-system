package com.housekeeping.management.dto.entity;

import com.housekeeping.management.domain.StaffType;
import com.housekeeping.management.entity.Staff;

public record StaffDTO(Long staffId, UserDTO user, StaffType staffType) {
    public static StaffDTO convertEntityToDTO(Staff entity) {
        if (entity == null) return null;
        var user = entity.getUser();
        return new StaffDTO(entity.getStaffId(), UserDTO.convertEntityToDTO(user), entity.getStaffType());
    }

    public Staff convertDTOToEntity() {
        Staff entity = new Staff();
        entity.setStaffId(staffId);
        entity.setStaffType(staffType);
        // The service resolves and assigns the managed User entity.
        return entity;
    }
}
