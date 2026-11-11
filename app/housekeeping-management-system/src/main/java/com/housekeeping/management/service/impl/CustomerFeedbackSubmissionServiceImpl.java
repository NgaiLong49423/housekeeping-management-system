package com.housekeeping.management.service.impl;

import com.housekeeping.management.dto.request.PortalForms.FeedbackForm;
import com.housekeeping.management.dto.request.PortalForms.IncidentItemDetails;
import com.housekeeping.management.service.CaseAttachmentStorageService;
import com.housekeeping.management.service.CustomerFeedbackSubmissionService;
import com.housekeeping.management.service.FeedbackCaseService;
import com.housekeeping.management.service.FeedbackService;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class CustomerFeedbackSubmissionServiceImpl implements CustomerFeedbackSubmissionService {
    private final FeedbackService feedback;
    private final FeedbackCaseService cases;
    private final CaseAttachmentStorageService attachments;

    public CustomerFeedbackSubmissionServiceImpl(FeedbackService feedback, FeedbackCaseService cases,
            CaseAttachmentStorageService attachments) {
        this.feedback = feedback;
        this.cases = cases;
        this.attachments = attachments;
    }

    @Override
    @Transactional
    public void submit(Long bookingId, Long customerId, FeedbackForm form, boolean edit, List<MultipartFile> images) {
        List<MultipartFile> files = images == null ? List.of()
                : images.stream().filter(file -> file != null && !file.isEmpty()).toList();
        if (files.size() > 5) throw new IllegalArgumentException("Mỗi lần gửi tối đa 5 ảnh bằng chứng.");
        boolean reportIncident = Boolean.TRUE.equals(form.reportIncident());
        if(reportIncident && form.incidentDriveLink()!=null && !form.incidentDriveLink().isBlank() && files.size()>=5)
            throw new IllegalArgumentException("Ảnh/tệp và liên kết Drive cộng lại tối đa 5 mục cho một tin nhắn.");
        if (!reportIncident && !files.isEmpty())
            throw new IllegalArgumentException("Hãy chọn mục báo sự cố để gửi ảnh bằng chứng.");
        List<String> incidentTypes = form.incidentTypes();
        String incidentDescription = form.incidentDescription();
        if (reportIncident && (incidentTypes == null || incidentTypes.isEmpty()
                || incidentTypes.size() > 4 || incidentTypes.stream().anyMatch(java.util.Objects::isNull)
                || incidentTypes.stream().distinct().count() != incidentTypes.size()
                || incidentDescription == null || incidentDescription.isBlank() || incidentDescription.length() > 4000)) {
            throw new IllegalArgumentException("Hãy chọn loại sự cố và nhập mô tả tối đa 4.000 ký tự.");
        }

        if (edit) feedback.update(bookingId, customerId, form.rating(), form.topics(), form.content());
        else feedback.submit(bookingId, customerId, form.rating(), form.topics(), form.content());

        if (reportIncident) {
            IncidentItemDetails itemDetails = new IncidentItemDetails(form.incidentItemDescription(),
                    form.estimatedItemValue(), form.itemAgeMonths());
            Long caseId = cases.open(bookingId, customerId, incidentTypes, incidentDescription, itemDetails);
            if (!files.isEmpty()) attachments.uploadCustomerEvidence(caseId, bookingId, customerId, files);
            if(form.incidentDriveLink()!=null && !form.incidentDriveLink().isBlank()) {
                Long messageId=cases.initialCustomerMessageId(caseId);
                attachments.addCustomerDriveEvidence(caseId,bookingId,customerId,messageId,form.incidentDriveLink());
            }
        }
    }
}
