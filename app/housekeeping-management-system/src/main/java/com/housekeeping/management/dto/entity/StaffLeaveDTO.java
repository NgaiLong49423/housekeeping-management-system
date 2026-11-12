package com.housekeeping.management.dto.entity;

import com.housekeeping.management.enums.LeaveStatus;
import com.housekeeping.management.entity.StaffLeave;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record StaffLeaveDTO(Long leaveId, StaffDTO staff, LocalDate startDate, LocalDate endDate, String reason,
        LeaveStatus status, LocalDateTime createdAt, Long reviewedBy, LocalDateTime reviewedAt) {
    public static StaffLeaveDTO convertEntityToDTO(StaffLeave entity) {
        if (entity == null) return null;
        var staff = entity.getStaff();
        return new StaffLeaveDTO(entity.getLeaveId(), StaffDTO.convertEntityToDTO(staff), entity.getStartDate(),
                entity.getEndDate(), entity.getReason(), entity.getStatus(), entity.getCreatedAt(),
                entity.getReviewedBy(), entity.getReviewedAt());
    }

    public StaffLeave convertDTOToEntity() {
        StaffLeave entity = new StaffLeave();
        entity.setLeaveId(leaveId);
        entity.setStartDate(startDate);
        entity.setEndDate(endDate);
        entity.setReason(reason);
        entity.setStatus(status);
        entity.setCreatedAt(createdAt);
        entity.setReviewedBy(reviewedBy);
        entity.setReviewedAt(reviewedAt);
        // The service resolves and assigns the managed Staff entity.
        return entity;
    }
}
