package com.housekeeping.management.dto.entity;

import com.housekeeping.management.entity.StaffIncidentActionEvidence;
import java.time.LocalDateTime;

public record StaffIncidentActionEvidenceDTO(Long evidenceId, Long actionId, Long uploadedBy,
        String evidenceStage, String mediaType, String contentType, String originalName,
        String storagePath, LocalDateTime uploadedAt) {
    public static StaffIncidentActionEvidenceDTO convertEntityToDTO(StaffIncidentActionEvidence entity) {
        if (entity == null) return null;
        return new StaffIncidentActionEvidenceDTO(entity.getEvidenceId(), entity.getActionId(), entity.getUploadedBy(),
                entity.getEvidenceStage(), entity.getMediaType(), entity.getContentType(), entity.getOriginalName(),
                entity.getStoragePath(), entity.getUploadedAt());
    }
    public StaffIncidentActionEvidence convertDTOToEntity() {
        StaffIncidentActionEvidence entity = new StaffIncidentActionEvidence();
        entity.setEvidenceId(evidenceId); entity.setActionId(actionId); entity.setUploadedBy(uploadedBy);
        entity.setEvidenceStage(evidenceStage); entity.setMediaType(mediaType); entity.setContentType(contentType);
        entity.setOriginalName(originalName); entity.setStoragePath(storagePath); entity.setUploadedAt(uploadedAt);
        return entity;
    }
}
