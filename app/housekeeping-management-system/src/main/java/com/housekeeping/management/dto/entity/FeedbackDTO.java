package com.housekeeping.management.dto.entity;

import com.housekeeping.management.domain.FeedbackStatus;
import com.housekeeping.management.entity.Feedback;

public record FeedbackDTO(Long feedbackId, Long bookingId, Long customerId, String content, Integer rating,
        String topics, String adminReply, FeedbackStatus status) {
    public static FeedbackDTO convertEntityToDTO(Feedback entity) {
        if (entity == null) return null;
        return new FeedbackDTO(entity.getFeedbackId(), entity.getBookingId(), entity.getCustomerId(), entity.getContent(),
                entity.getRating(), entity.getTopics(), entity.getAdminReply(), entity.getStatus());
    }

    public Feedback convertDTOToEntity() {
        Feedback entity = new Feedback();
        entity.setFeedbackId(feedbackId);
        entity.setBookingId(bookingId);
        entity.setCustomerId(customerId);
        entity.setContent(content);
        entity.setRating(rating);
        entity.setTopics(topics);
        entity.setAdminReply(adminReply);
        entity.setStatus(status);
        return entity;
    }
}
