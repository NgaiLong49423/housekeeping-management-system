package com.housekeeping.management.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.housekeeping.management.dto.request.PortalForms.IncidentReviewForm;
import com.housekeeping.management.dto.request.PortalForms.IncidentItemDetails;
import com.housekeeping.management.repository.FeedbackCaseRepository;
import com.housekeeping.management.repository.FeedbackCaseMessageRepository;
import com.housekeeping.management.repository.FeedbackCaseEvidenceRepository;
import com.housekeeping.management.repository.FeedbackCaseVerificationRepository;
import com.housekeeping.management.repository.StaffIncidentActionRepository;
import com.housekeeping.management.repository.UserRepository;
import com.housekeeping.management.entity.FeedbackCase;
import com.housekeeping.management.entity.FeedbackCaseMessage;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import java.util.Optional;

class FeedbackCaseServiceTest {
    FeedbackCaseRepository cases=mock(FeedbackCaseRepository.class);
    BookingReservationService bookings=mock(BookingReservationService.class);
    FeedbackCaseMessageRepository messages=mock(FeedbackCaseMessageRepository.class);
    FeedbackCaseEvidenceRepository evidence=mock(FeedbackCaseEvidenceRepository.class);
    FeedbackCaseVerificationRepository verifications=mock(FeedbackCaseVerificationRepository.class);
    StaffIncidentActionRepository actions=mock(StaffIncidentActionRepository.class);
    UserRepository users=mock(UserRepository.class);
    AccountAuthorizationService authorization=mock(AccountAuthorizationService.class);
    FeedbackCaseService service=new com.housekeeping.management.service.impl.FeedbackCaseServiceImpl(
            cases,bookings,messages,evidence,verifications,actions,users,authorization);

    @Test void newCaseUsesRequiredDatabaseDefaults() {
        FeedbackCase incident = new FeedbackCase();
        assertThat(incident.getCaseStatus()).isEqualTo("RECEIVED");
        assertThat(incident.getStaffActionStatus()).isEqualTo("NOT_PROPOSED");
    }

    @Test void rejectsEmptyOrUnsupportedIncidentType() {
        assertThatThrownBy(()->service.open(4L,7L,java.util.List.of("THEFT"),"Bị mất đồ"))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(cases);
    }

    @Test void onlyOwnerWithOpenCompletedFeedbackCanCreateCase() {
        when(bookings.lockBookingForFeedback(4L)).thenReturn(Optional.of(
                new BookingReservationService.BookingFeedbackState(7L,"COMPLETED")));
        when(cases.eligibleFeedbackId(4L,7L)).thenReturn(Optional.empty());
        assertThatThrownBy(()->service.open(4L,7L,java.util.List.of("PROPERTY_DAMAGE","CLEANLINESS"),"Đồ bị vỡ và nhà chưa sạch",
                new IncidentItemDetails("Bình hoa",new java.math.BigDecimal("500000"),12)))
                .isInstanceOf(AccessDeniedException.class);
        verify(cases).eligibleFeedbackId(4L,7L);
    }

    @Test void locksBookingBeforeCheckingWhetherAnIncidentCaseAlreadyExists() {
        when(bookings.lockBookingForFeedback(4L)).thenReturn(Optional.of(
                new BookingReservationService.BookingFeedbackState(7L,"COMPLETED")));
        when(cases.eligibleFeedbackId(4L,7L)).thenReturn(Optional.of(19L));
        FeedbackCase existing = new FeedbackCase();
        existing.setCaseId(23L);
        when(cases.findByFeedbackId(19L)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.open(4L,7L,java.util.List.of("PROPERTY_DAMAGE"),"Đồ bị hư",
                new IncidentItemDetails("Bàn gỗ",new java.math.BigDecimal("1000000"),24)))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("đã có hồ sơ sự cố");
        org.mockito.InOrder order = inOrder(bookings,cases);
        order.verify(cases).eligibleFeedbackId(4L,7L);
        order.verify(bookings).lockBookingForFeedback(4L);
        order.verify(cases).eligibleFeedbackId(4L,7L);
        order.verify(cases).findByFeedbackId(19L);
        verify(cases,never()).create(anyLong(),anyList(),anyString(),anyString(),any(),any());
    }

    @Test void storesCustomerDeclaredItemDetailsOnTheCase() {
        when(bookings.lockBookingForFeedback(4L)).thenReturn(Optional.of(
                new BookingReservationService.BookingFeedbackState(7L,"COMPLETED")));
        when(cases.eligibleFeedbackId(4L,7L)).thenReturn(Optional.of(19L));
        when(cases.findByFeedbackId(19L)).thenReturn(Optional.empty());
        var value=new java.math.BigDecimal("750000");
        var details=new IncidentItemDetails("Bàn gỗ trầy mặt",value,30);
        when(cases.create(19L,java.util.List.of("PROPERTY_DAMAGE"),"Mặt bàn bị trầy",
                "Bàn gỗ trầy mặt",value,30)).thenReturn(23L);

        assertThat(service.open(4L,7L,java.util.List.of("PROPERTY_DAMAGE"),"Mặt bàn bị trầy",details)).isEqualTo(23L);
        verify(cases).create(19L,java.util.List.of("PROPERTY_DAMAGE"),"Mặt bàn bị trầy","Bàn gỗ trầy mặt",value,30);
    }

    @Test void assetIncidentMustIdentifyTheAffectedItem() {
        assertThatThrownBy(() -> service.open(4L,7L,java.util.List.of("MISSING_PROPERTY"),"Thiếu đồng hồ",
                new IncidentItemDetails(" ",null,null)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("tên hoặc mô tả món đồ");
        verifyNoInteractions(cases,bookings);
    }

    @Test void verifiedCaseRequiresCustomerFacingOutcome() {
        assertThatThrownBy(()->service.review(11L,3L,new IncidentReviewForm("VERIFIED","COMPENSATION"," ","Staff làm rơi đồ",false,"PROPOSE_WARNING","Thông báo cảnh cáo")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("kết quả xác minh");
        verify(cases,never()).save(any());
    }

    @Test void managerCannotReenterAdminIncidentReview() {
        when(authorization.isManager(5L)).thenReturn(true);
        assertThatThrownBy(() -> service.review(11L,5L,new IncidentReviewForm(
                "VERIFIED","COMPENSATION","Công ty sẽ bồi thường.",null,false,null,null)))
                .isInstanceOf(AccessDeniedException.class).hasMessageContaining("Manager duyệt khoản tài chính");
        verifyNoInteractions(cases);
    }

    @Test void recordsEachVerificationAndTheEvidenceReviewed() {
        FeedbackCase incident=caseEntity(); incident.setResolutionWorkflowRequired(true);
        when(cases.findById(11L)).thenReturn(Optional.of(incident));
        when(cases.findByIdForUpdate(11L)).thenReturn(Optional.of(incident));
        var item=new com.housekeeping.management.entity.FeedbackCaseEvidence(); item.setEvidenceId(51L); item.setCaseId(11L);
        when(evidence.findAllByCaseIdOrderByUploadedAtAscEvidenceIdAsc(11L)).thenReturn(java.util.List.of(item));
        service.review(11L,3L,new IncidentReviewForm("VERIFIED",null,null,"Ảnh cho thấy vết xước.",false,null,null,java.util.List.of(51L)));
        var saved=org.mockito.ArgumentCaptor.forClass(com.housekeeping.management.entity.FeedbackCaseVerification.class);
        verify(verifications).save(saved.capture());
        assertThat(saved.getValue().getReviewedEvidenceIds()).isEqualTo("51");
        assertThat(saved.getValue().getInternalFinding()).isEqualTo("Ảnh cho thấy vết xước.");
    }

    @Test void rejectsEvidenceIdsFromAnotherCaseDuringVerification() {
        FeedbackCase incident=caseEntity(); incident.setResolutionWorkflowRequired(true);
        when(cases.findById(11L)).thenReturn(Optional.of(incident));
        when(cases.findByIdForUpdate(11L)).thenReturn(Optional.of(incident));
        when(evidence.findAllByCaseIdOrderByUploadedAtAscEvidenceIdAsc(11L)).thenReturn(java.util.List.of());
        assertThatThrownBy(()->service.review(11L,3L,new IncidentReviewForm("VERIFIED",null,null,null,false,null,null,java.util.List.of(900L))))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("không thuộc hồ sơ");
        verify(verifications,never()).save(any());
    }

    @Test void cannotMarkRemedyCompleteBeforeChoosingARemedy() {
        assertThatThrownBy(()->service.review(11L,3L,new IncidentReviewForm("VERIFIED",null,
                "Đang xác minh và sẽ cập nhật sau.","",true,null,null)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("chọn biện pháp");
        verify(cases,never()).save(any());
    }

    @Test void customerCanSendTextOrAttachmentButNotAnEmptyMessage() {
        assertThatThrownBy(()->service.addCustomerMessage(11L,4L,7L,"  ",false))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("nội dung hoặc");
        verify(messages,never()).save(any());

        when(cases.findByIdForUpdate(11L)).thenReturn(Optional.of(caseEntity()));
        when(cases.customerCanMessage(11L,4L,7L)).thenReturn(true);
        when(messages.save(any())).thenAnswer(call -> { FeedbackCaseMessage message=call.getArgument(0); message.setMessageId(21L); return message; });
        org.assertj.core.api.Assertions.assertThat(service.addCustomerMessage(11L,4L,7L,"Bổ sung ảnh",true)).isEqualTo(21L);
        verify(messages).save(any());
    }

    @Test void adminTextReplyCreatesConversationMessageAndLocksFeedback() {
        when(cases.findByIdForUpdate(11L)).thenReturn(Optional.of(caseEntity()));
        when(messages.save(any())).thenAnswer(call -> { FeedbackCaseMessage message=call.getArgument(0); message.setMessageId(22L); return message; });
        org.assertj.core.api.Assertions.assertThat(service.addAdminMessage(11L,3L,"Chúng tôi đang xác minh.",false)).isEqualTo(22L);
        verify(authorization).requireActiveAdmin(3L);
        verify(cases).markFeedbackResponded(11L,"Chúng tôi đang xác minh.");
    }

    @Test void rejectsOversizedMessageAndDoesNotPersistIt() {
        assertThatThrownBy(()->service.addAdminMessage(11L,3L,"x".repeat(4001),false))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("4.000");
        verify(messages,never()).save(any());
    }

    private static FeedbackCase caseEntity() { FeedbackCase incident=new FeedbackCase(); incident.setCaseId(11L); incident.setCaseStatus("RECEIVED"); return incident; }
}
