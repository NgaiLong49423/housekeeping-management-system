package com.housekeeping.management.service;

import com.housekeeping.management.dto.request.PortalForms.FeedbackForm;
import java.util.List;
import org.springframework.web.multipart.MultipartFile;

public interface CustomerFeedbackSubmissionService {
    void submit(Long bookingId, Long customerId, FeedbackForm form, boolean edit, List<MultipartFile> images);
}
