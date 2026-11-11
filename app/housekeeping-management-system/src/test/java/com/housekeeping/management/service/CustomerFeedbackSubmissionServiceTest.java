package com.housekeeping.management.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import com.housekeeping.management.dto.request.PortalForms.FeedbackForm;
import com.housekeeping.management.dto.request.PortalForms.IncidentItemDetails;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

class CustomerFeedbackSubmissionServiceTest {
    private FeedbackService feedback;
    private FeedbackCaseService cases;
    private CaseAttachmentStorageService attachments;
    private CustomerFeedbackSubmissionService submission;

    @BeforeEach
    void setUp() {
        feedback = mock(FeedbackService.class);
        cases = mock(FeedbackCaseService.class);
        attachments = mock(CaseAttachmentStorageService.class);
        submission = new com.housekeeping.management.service.impl.CustomerFeedbackSubmissionServiceImpl(
                feedback, cases, attachments);
    }

    @Test
    void submitsOrdinaryFeedbackWithoutCreatingIncidentOrUploadingEvidence() {
        submission.submit(42L, 7L, new FeedbackForm(5, List.of(), "Rất tốt"), false, null);

        verify(feedback).submit(42L, 7L, 5, List.of(), "Rất tốt");
        verifyNoInteractions(cases, attachments);
    }

    @Test
    void submitsFeedbackAndIncidentAndUploadsEvidenceInOneUseCase() {
        var image = new MockMultipartFile("images", "evidence.png", "image/png", new byte[] {1});
        var form = new FeedbackForm(2, List.of("CLEANING_QUALITY"), "Vẫn còn bụi", true,
                List.of("CLEANLINESS", "PROPERTY_DAMAGE"), "Bụi bẩn và đồ bị vỡ", "Bình hoa sứ", new java.math.BigDecimal("800000"), 18);
        var itemDetails = new IncidentItemDetails("Bình hoa sứ", new java.math.BigDecimal("800000"), 18);
        when(cases.open(42L, 7L, form.incidentTypes(), form.incidentDescription(), itemDetails,
                form.customerRequestedResolution(),true)).thenReturn(99L);

        submission.submit(42L, 7L, form, false, List.of(image));

        var order = inOrder(feedback, cases, attachments);
        order.verify(feedback).submit(42L, 7L, 2, List.of("CLEANING_QUALITY"), "Vẫn còn bụi");
        order.verify(cases).open(42L, 7L, form.incidentTypes(), form.incidentDescription(), itemDetails,
                form.customerRequestedResolution(),true);
        order.verify(attachments).uploadCustomerEvidence(99L, 42L, 7L, List.of(image));
    }

    @Test
    void rejectsIncidentWithoutTypesAndDescriptionBeforeSavingFeedback() {
        var form = new FeedbackForm(3, List.of(), "Không hài lòng", true, List.of(), " ");

        assertThatThrownBy(() -> submission.submit(42L, 7L, form, false, null))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(feedback, cases, attachments);
    }

    @Test
    void rejectsPhotosUnlessIncidentReportingIsSelected() {
        var image = new MockMultipartFile("images", "evidence.png", "image/png", new byte[] {1});

        assertThatThrownBy(() -> submission.submit(42L, 7L,
                new FeedbackForm(4, List.of(), "Tốt"), false, List.of(image)))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(feedback, cases, attachments);
    }

    @Test
    void incidentReportRequiresAtLeastOneEvidenceItem() {
        var form = new FeedbackForm(2,List.of(),"Đồ bị hư",true,List.of("PROPERTY_DAMAGE"),
                "Bình bị nứt","Bình gốm",null,null);
        assertThatThrownBy(() -> submission.submit(42L,7L,form,false,null))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("bằng chứng");
        verifyNoInteractions(feedback,cases,attachments);
    }
}
