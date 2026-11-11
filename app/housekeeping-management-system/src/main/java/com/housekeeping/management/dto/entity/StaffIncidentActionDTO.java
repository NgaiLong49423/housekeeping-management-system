package com.housekeeping.management.dto.entity;

import com.housekeeping.management.entity.StaffIncidentAction;
import java.time.LocalDateTime;

public record StaffIncidentActionDTO(Long actionId, Long caseId, Long staffId, String actionType, String status,
        String reason, String staffNotice, Long proposedBy, Long decidedBy, LocalDateTime createdAt,
        LocalDateTime decidedAt, LocalDateTime acknowledgedAt) {
    public static StaffIncidentActionDTO convertEntityToDTO(StaffIncidentAction entity) {
        if (entity == null) return null;
        return new StaffIncidentActionDTO(entity.getActionId(), entity.getCaseId(), entity.getStaffId(),
                entity.getActionType(), entity.getStatus(), entity.getReason(), entity.getStaffNotice(),
                entity.getProposedBy(), entity.getDecidedBy(), entity.getCreatedAt(), entity.getDecidedAt(),
                entity.getAcknowledgedAt());
    }
    public StaffIncidentAction convertDTOToEntity() {
        StaffIncidentAction entity = new StaffIncidentAction();
        entity.setActionId(actionId); entity.setCaseId(caseId); entity.setStaffId(staffId);
        entity.setActionType(actionType); entity.setStatus(status); entity.setReason(reason);
        entity.setStaffNotice(staffNotice); entity.setProposedBy(proposedBy); entity.setDecidedBy(decidedBy);
        entity.setCreatedAt(createdAt); entity.setDecidedAt(decidedAt); entity.setAcknowledgedAt(acknowledgedAt);
        return entity;
    }
}
