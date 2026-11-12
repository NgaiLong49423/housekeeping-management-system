package com.housekeeping.management.dto.entity;

import com.housekeeping.management.entity.FeedbackCaseEvidence;
import java.time.LocalDateTime;

public record FeedbackCaseEvidenceDTO(Long evidenceId, Long caseId, String imagePath,
        Long uploadedBy, LocalDateTime uploadedAt, Long messageId, String mediaType, String contentType,
        String originalName, String externalUrl) {
    public FeedbackCaseEvidenceDTO(Long evidenceId, Long caseId, String imagePath, Long uploadedBy,
            LocalDateTime uploadedAt, Long messageId) {
        this(evidenceId,caseId,imagePath,uploadedBy,uploadedAt,messageId,"IMAGE",null,null,null);
    }
    public static FeedbackCaseEvidenceDTO convertEntityToDTO(FeedbackCaseEvidence entity) {
        if (entity == null) return null;
        return new FeedbackCaseEvidenceDTO(entity.getEvidenceId(), entity.getCaseId(), entity.getImagePath(),
                entity.getUploadedBy(), entity.getUploadedAt(), entity.getMessageId(), entity.getMediaType(),
                entity.getContentType(), entity.getOriginalName(), entity.getExternalUrl());
    }

    public FeedbackCaseEvidence convertDTOToEntity() {
        FeedbackCaseEvidence entity = new FeedbackCaseEvidence();
        entity.setEvidenceId(evidenceId);
        entity.setCaseId(caseId);
        entity.setImagePath(imagePath);
        entity.setUploadedBy(uploadedBy);
        entity.setUploadedAt(uploadedAt);
        entity.setMessageId(messageId);
        entity.setMediaType(mediaType == null ? "IMAGE" : mediaType);
        entity.setContentType(contentType);
        entity.setOriginalName(originalName);
        entity.setExternalUrl(externalUrl);
        return entity;
    }
}
