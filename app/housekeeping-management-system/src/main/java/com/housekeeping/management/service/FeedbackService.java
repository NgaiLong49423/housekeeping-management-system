package com.housekeeping.management.service;

import com.housekeeping.management.enums.FeedbackTopic;
import java.util.List;

public interface FeedbackService {

    public void submit(Long bookingId, Long customerId, Integer rating, List<String> topics, String content);
    public void update(Long bookingId, Long customerId, Integer rating, List<String> topics, String content);
    public void reply(Long feedbackId, Long adminId, String reply);
    public void resolve(Long feedbackId, Long adminId);
    List<com.housekeeping.management.dto.response.PortalViews.FeedbackCard> findAll();
}
