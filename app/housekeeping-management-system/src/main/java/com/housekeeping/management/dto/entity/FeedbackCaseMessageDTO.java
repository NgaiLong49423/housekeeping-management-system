package com.housekeeping.management.dto.entity;

import com.housekeeping.management.entity.FeedbackCaseMessage;
import java.time.LocalDateTime;

public record FeedbackCaseMessageDTO(Long messageId, Long caseId, Long senderUserId, String senderRole,
        String messageText, LocalDateTime sentAt, boolean legacyImported, boolean legacyTimestampUnknown) {
    public static FeedbackCaseMessageDTO convertEntityToDTO(FeedbackCaseMessage entity) {
        if (entity == null) return null;
        return new FeedbackCaseMessageDTO(entity.getMessageId(), entity.getCaseId(), entity.getSenderUserId(),
                entity.getSenderRole(), entity.getMessageText(), entity.getSentAt(), entity.isLegacyImported(),
                entity.isLegacyTimestampUnknown());
    }
    public FeedbackCaseMessage convertDTOToEntity() {
        FeedbackCaseMessage entity = new FeedbackCaseMessage();
        entity.setMessageId(messageId); entity.setCaseId(caseId); entity.setSenderUserId(senderUserId);
        entity.setSenderRole(senderRole); entity.setMessageText(messageText); entity.setSentAt(sentAt);
        entity.setLegacyImported(legacyImported); entity.setLegacyTimestampUnknown(legacyTimestampUnknown);
        return entity;
    }
}
