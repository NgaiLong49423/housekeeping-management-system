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
import com.housekeeping.management.repository.StaffIncidentActionEvidenceRepository;
import com.housekeeping.management.repository.StaffRecoveryInstallmentRepository;
import com.housekeeping.management.entity.FeedbackCase;
import com.housekeeping.management.entity.FeedbackCaseMessage;
import com.housekeeping.management.entity.StaffIncidentAction;
import com.housekeeping.management.entity.StaffIncidentActionEvidence;
import com.housekeeping.management.entity.StaffRecoveryInstallment;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import java.util.Optional;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

class FeedbackCaseServiceTest {
    FeedbackCaseRepository cases=mock(FeedbackCaseRepository.class);
    BookingReservationService bookings=mock(BookingReservationService.class);
    StaffManagementService staffManagement=mock(StaffManagementService.class);
    FeedbackCaseMessageRepository messages=mock(FeedbackCaseMessageRepository.class);
    FeedbackCaseEvidenceRepository evidence=mock(FeedbackCaseEvidenceRepository.class);
    FeedbackCaseVerificationRepository verifications=mock(FeedbackCaseVerificationRepository.class);
    StaffIncidentActionRepository actions=mock(StaffIncidentActionRepository.class);
    UserRepository users=mock(UserRepository.class);
    StaffIncidentActionEvidenceRepository actionEvidence=mock(StaffIncidentActionEvidenceRepository.class);
    StaffRecoveryInstallmentRepository recoveryInstallments=mock(StaffRecoveryInstallmentRepository.class);
    AccountAuthorizationService authorization=mock(AccountAuthorizationService.class);
    FeedbackCaseService service=new com.housekeeping.management.service.impl.FeedbackCaseServiceImpl(
            cases,bookings,staffManagement,messages,evidence,verifications,actions,users,authorization,actionEvidence,recoveryInstallments);

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
                new BookingReservationService.BookingFeedbackState(7L,"COMPLETED",LocalDateTime.now(ZoneOffset.ofHours(7)))));
        when(cases.eligibleFeedbackId(4L,7L)).thenReturn(Optional.empty());
        assertThatThrownBy(()->service.open(4L,7L,java.util.List.of("PROPERTY_DAMAGE","CLEANLINESS"),"Đồ bị vỡ và nhà chưa sạch",
                new IncidentItemDetails("Bình hoa",new java.math.BigDecimal("500000"),12),null,true))
                .isInstanceOf(AccessDeniedException.class);
        verify(cases).eligibleFeedbackId(4L,7L);
    }

    @Test void requiresEvidenceBeforeCreatingCase() {
        assertThatThrownBy(() -> service.open(4L,7L,java.util.List.of("CLEANLINESS"),"Còn bụi",
                null,null,false)).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("bằng chứng");
        verifyNoInteractions(cases,bookings);
    }

    @Test void rejectsNewCaseAfter24HoursFromServiceCompletion() {
        when(cases.eligibleFeedbackId(4L,7L)).thenReturn(Optional.of(19L));
        when(bookings.lockBookingForFeedback(4L)).thenReturn(Optional.of(
                new BookingReservationService.BookingFeedbackState(7L,"COMPLETED",
                        LocalDateTime.now(ZoneOffset.ofHours(7)).minusHours(25))));
        assertThatThrownBy(() -> service.open(4L,7L,java.util.List.of("CLEANLINESS"),"Còn bụi",
                null,null,true)).isInstanceOf(IllegalStateException.class).hasMessageContaining("24 giờ");
        verify(cases,never()).create(anyLong(),anyList(),anyString(),any(),any(),any(),any());
    }

    @Test void locksBookingBeforeCheckingWhetherAnIncidentCaseAlreadyExists() {
        when(bookings.lockBookingForFeedback(4L)).thenReturn(Optional.of(
                new BookingReservationService.BookingFeedbackState(7L,"COMPLETED",LocalDateTime.now(ZoneOffset.ofHours(7)))));
        when(cases.eligibleFeedbackId(4L,7L)).thenReturn(Optional.of(19L));
        FeedbackCase existing = new FeedbackCase();
        existing.setCaseId(23L);
        when(cases.findByFeedbackId(19L)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.open(4L,7L,java.util.List.of("PROPERTY_DAMAGE"),"Đồ bị hư",
                new IncidentItemDetails("Bàn gỗ",new java.math.BigDecimal("1000000"),24),null,true))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("đã có hồ sơ sự cố");
        org.mockito.InOrder order = inOrder(bookings,cases);
        order.verify(cases).eligibleFeedbackId(4L,7L);
        order.verify(bookings).lockBookingForFeedback(4L);
        order.verify(cases).eligibleFeedbackId(4L,7L);
        order.verify(cases).findByFeedbackId(19L);
        verify(cases,never()).create(anyLong(),anyList(),anyString(),anyString(),any(),any(),any());
    }

    @Test void storesCustomerDeclaredItemDetailsOnTheCase() {
        when(bookings.lockBookingForFeedback(4L)).thenReturn(Optional.of(
                new BookingReservationService.BookingFeedbackState(7L,"COMPLETED",LocalDateTime.now(ZoneOffset.ofHours(7)))));
        when(cases.eligibleFeedbackId(4L,7L)).thenReturn(Optional.of(19L));
        when(cases.findByFeedbackId(19L)).thenReturn(Optional.empty());
        var value=new java.math.BigDecimal("750000");
        var details=new IncidentItemDetails("Bàn gỗ trầy mặt",value,30);
        when(cases.create(19L,java.util.List.of("PROPERTY_DAMAGE"),"Mặt bàn bị trầy",
                "Bàn gỗ trầy mặt",value,30,null)).thenReturn(23L);

        assertThat(service.open(4L,7L,java.util.List.of("PROPERTY_DAMAGE"),"Mặt bàn bị trầy",details,null,true)).isEqualTo(23L);
        verify(cases).create(19L,java.util.List.of("PROPERTY_DAMAGE"),"Mặt bàn bị trầy","Bàn gỗ trầy mặt",value,30,null);
    }

    @Test void assetIncidentMustIdentifyTheAffectedItem() {
        assertThatThrownBy(() -> service.open(4L,7L,java.util.List.of("MISSING_PROPERTY"),"Thiếu đồng hồ",
                new IncidentItemDetails(" ",null,null),null,true))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("tên hoặc mô tả món đồ");
        verifyNoInteractions(cases,bookings);
    }

    @Test void managerCannotReenterAdminIncidentReview() {
        when(authorization.isManager(5L)).thenReturn(true);
        assertThatThrownBy(() -> service.review(11L,5L,new IncidentReviewForm("VERIFIED",null)))
                .isInstanceOf(AccessDeniedException.class).hasMessageContaining("Manager duyệt khoản tài chính");
        verifyNoInteractions(cases);
    }

    @Test void recordsEachVerificationAndTheEvidenceReviewed() {
        FeedbackCase incident=caseEntity();
        when(cases.findById(11L)).thenReturn(Optional.of(incident));
        when(cases.findByIdForUpdate(11L)).thenReturn(Optional.of(incident));
        var item=new com.housekeeping.management.entity.FeedbackCaseEvidence(); item.setEvidenceId(51L); item.setCaseId(11L);
        when(evidence.findAllByCaseIdOrderByUploadedAtAscEvidenceIdAsc(11L)).thenReturn(java.util.List.of(item));
        service.review(11L,3L,new IncidentReviewForm("VERIFIED","Ảnh cho thấy vết xước.",java.util.List.of(51L)));
        var saved=org.mockito.ArgumentCaptor.forClass(com.housekeeping.management.entity.FeedbackCaseVerification.class);
        verify(verifications).save(saved.capture());
        assertThat(saved.getValue().getReviewedEvidenceIds()).isEqualTo("51");
        assertThat(saved.getValue().getInternalFinding()).isEqualTo("Ảnh cho thấy vết xước.");
    }

    @Test void rejectsEvidenceIdsFromAnotherCaseDuringVerification() {
        FeedbackCase incident=caseEntity();
        when(cases.findById(11L)).thenReturn(Optional.of(incident));
        when(cases.findByIdForUpdate(11L)).thenReturn(Optional.of(incident));
        when(evidence.findAllByCaseIdOrderByUploadedAtAscEvidenceIdAsc(11L)).thenReturn(java.util.List.of());
        assertThatThrownBy(()->service.review(11L,3L,new IncidentReviewForm("VERIFIED",null,java.util.List.of(900L))))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("không thuộc hồ sơ");
        verify(verifications,never()).save(any());
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

    @Test void staffActionStartsWithFortyEightHourExplanationWindow() {
        FeedbackCase incident=caseEntity(); incident.setReviewedAt(LocalDateTime.now(ZoneOffset.ofHours(7)));
        when(cases.findByIdForUpdate(11L)).thenReturn(Optional.of(incident));
        when(cases.staffIdForCase(11L)).thenReturn(Optional.of(42L));
        when(actions.save(any())).thenAnswer(call->call.getArgument(0));
        service.proposeStaffAction(11L,3L,new com.housekeeping.management.dto.request.PortalForms.StaffIncidentActionForm("PROPOSE_RECOVERY","Vui lòng giải trình về món đồ hư hại."));
        var saved=org.mockito.ArgumentCaptor.forClass(com.housekeeping.management.entity.StaffIncidentAction.class);
        verify(actions).save(saved.capture());
        assertThat(saved.getValue().getStatus()).isEqualTo("AWAITING_STAFF");
        assertThat(saved.getValue().getResponseDeadline()).isAfter(saved.getValue().getCreatedAt().plusHours(47));
        assertThat(incident.getStaffActionStatus()).isEqualTo("AWAITING_STAFF");
    }

    @Test void staffCanSubmitExplanationAndMovesCaseToManager() {
        var action=new com.housekeeping.management.entity.StaffIncidentAction(); action.setActionId(9L); action.setStaffId(42L);
        action.setStatus("AWAITING_STAFF"); action.setResponseDeadline(LocalDateTime.now(ZoneOffset.ofHours(7)).plusHours(1));
        when(actions.findByIdForUpdate(9L)).thenReturn(Optional.of(action));
        service.respondToStaffAction(9L,42L,"Tôi đã bàn giao đồ nguyên vẹn.",false);
        assertThat(action.getStatus()).isEqualTo("WAITING_MANAGER");
        assertThat(action.getStaffResponse()).contains("bàn giao");
        verify(actions).save(action);
    }

    @Test void managerCannotConcludeBeforeStaffRespondsOrDeadlineExpires() {
        var action=new com.housekeeping.management.entity.StaffIncidentAction(); action.setActionId(9L); action.setCaseId(11L);
        action.setStatus("AWAITING_STAFF"); action.setProposedBy(3L); action.setResponseDeadline(LocalDateTime.now(ZoneOffset.ofHours(7)).plusHours(2));
        when(actions.findFirstByCaseIdAndStatusInOrderByActionIdDesc(eq(11L),anyCollection())).thenReturn(Optional.of(action));
        assertThatThrownBy(()->service.decideStaffAction(11L,5L,"NO_LIABILITY",null,"Đã xem",false))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("48 giờ");
        verify(actions,never()).save(any());
    }

    @Test void expiredExplanationIsRecordedWithoutTreatingSilenceAsAdmission() {
        var expiredAt=LocalDateTime.now(ZoneOffset.ofHours(7)).minusMinutes(1).withNano(0);
        StaffIncidentAction action=new StaffIncidentAction(); action.setActionId(9L); action.setCaseId(11L);
        action.setStaffId(42L); action.setStatus("AWAITING_STAFF"); action.setResponseDeadline(expiredAt);
        FeedbackCase incident=caseEntity();
        when(actions.findAllByStatusAndResponseDeadlineLessThanEqual(eq("AWAITING_STAFF"),any()))
                .thenReturn(java.util.List.of(action));
        when(actions.findByIdForUpdate(9L)).thenReturn(Optional.of(action));
        when(actions.findAllByCaseIdOrderByCreatedAtAscActionIdAsc(11L)).thenReturn(java.util.List.of(action));
        when(cases.findByIdForUpdate(11L)).thenReturn(Optional.of(incident));

        var cards=service.actions(11L,3L);

        assertThat(cards.getFirst().status()).isEqualTo("WAITING_MANAGER");
        assertThat(cards.getFirst().staffResponseExpiredAt()).isEqualTo(expiredAt);
        assertThat(action.getStaffResponse()).isNull();
        assertThat(action.getStatus()).isEqualTo("WAITING_MANAGER");
        verify(actions).save(action);
    }

    @Test void managerDecisionOpensOneAppealWindowAndKeepsActualLiabilitySeparate() {
        var action=new com.housekeeping.management.entity.StaffIncidentAction(); action.setActionId(9L); action.setCaseId(11L);
        action.setStatus("WAITING_MANAGER"); action.setActionType("PROPOSE_RECOVERY"); action.setProposedBy(3L);
        var incident=caseEntity();
        when(actions.findFirstByCaseIdAndStatusInOrderByActionIdDesc(eq(11L),anyCollection())).thenReturn(Optional.of(action));
        when(cases.findByIdForUpdate(11L)).thenReturn(Optional.of(incident));
        var amount=new java.math.BigDecimal("70000");
        service.decideStaffAction(11L,5L,"RECOVERY",amount,"Hóa đơn sửa chữa xác nhận mức thiệt hại.",false);
        assertThat(action.getStatus()).isEqualTo("WAITING_STAFF_APPEAL");
        assertThat(action.getLiabilityAmount()).isEqualByComparingTo(amount);
        assertThat(action.getResponseDeadline()).isAfter(action.getDecidedAt().plusHours(47));
        assertThat(incident.getStaffActionApprovedBy()).isEqualTo(5L);
    }

    @Test void appealCanBeSentOnlyOnceWithinTheDecisionWindow() {
        var action=new com.housekeeping.management.entity.StaffIncidentAction(); action.setActionId(9L); action.setStaffId(42L);
        action.setStatus("WAITING_STAFF_APPEAL"); action.setResponseDeadline(LocalDateTime.now(ZoneOffset.ofHours(7)).plusHours(1));
        when(actions.findByIdForUpdate(9L)).thenReturn(Optional.of(action));
        service.respondToStaffAction(9L,42L,"Tôi đề nghị xem lại biên bản.",true);
        assertThat(action.getStatus()).isEqualTo("WAITING_MANAGER_APPEAL");
        assertThat(action.getAppealText()).isNotBlank();
        assertThatThrownBy(()->service.respondToStaffAction(9L,42L,"Gửi lần hai",true))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test void staffRecoveryIsRecordedAsSeparateInstallmentsAndStopsAtManagerAmount() {
        StaffIncidentAction action=new StaffIncidentAction(); action.setActionId(9L); action.setStatus("RECOVERY_DUE");
        action.setLiabilityAmount(new java.math.BigDecimal("100000"));
        when(actions.findByIdForUpdate(9L)).thenReturn(Optional.of(action));
        when(recoveryInstallments.sumByActionId(9L)).thenReturn(new java.math.BigDecimal("40000"));
        when(recoveryInstallments.save(any())).thenAnswer(call->call.getArgument(0));
        var installment=new com.housekeeping.management.dto.request.PortalForms.StaffRecoveryInstallmentForm(
                new java.math.BigDecimal("60000"),"BANK_TRANSFER","REC-77");

        service.recordStaffRecoveryInstallment(9L,3L,installment,"receipt.pdf");

        assertThat(action.getStatus()).isEqualTo("RECOVERY_SETTLED");
        var saved=org.mockito.ArgumentCaptor.forClass(StaffRecoveryInstallment.class);
        verify(recoveryInstallments).save(saved.capture());
        assertThat(saved.getValue().getAmount()).isEqualByComparingTo("60000");
        assertThat(saved.getValue().getReceiptPath()).isEqualTo("receipt.pdf");
    }

    @Test void staffRecoveryInstallmentsCannotExceedManagerDecision() {
        StaffIncidentAction action=new StaffIncidentAction(); action.setActionId(9L); action.setStatus("RECOVERY_DUE");
        action.setLiabilityAmount(new java.math.BigDecimal("100000"));
        when(actions.findByIdForUpdate(9L)).thenReturn(Optional.of(action));
        when(recoveryInstallments.sumByActionId(9L)).thenReturn(new java.math.BigDecimal("40000"));
        var installment=new com.housekeeping.management.dto.request.PortalForms.StaffRecoveryInstallmentForm(
                new java.math.BigDecimal("60001"),"CASH","REC-78");

        assertThatThrownBy(()->service.recordStaffRecoveryInstallment(9L,3L,installment,"receipt.pdf"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("vượt quá");
        verify(recoveryInstallments,never()).save(any());
    }

    @Test void staffEvidenceStoresUserPrimaryKeyWhileOwnershipUsesStaffProfileId() {
        StaffIncidentAction action=new StaffIncidentAction(); action.setActionId(9L); action.setStaffId(42L);
        action.setStatus("AWAITING_STAFF"); action.setResponseDeadline(LocalDateTime.now(ZoneOffset.ofHours(7)).plusHours(1));
        StaffIncidentActionEvidence evidenceRow=new StaffIncidentActionEvidence(); evidenceRow.setActionId(9L);
        evidenceRow.setUploadedBy(22L); evidenceRow.setEvidenceStage("RESPONSE"); evidenceRow.setStoragePath("proof.png");
        when(actions.findByActionIdAndStaffId(9L,42L)).thenReturn(Optional.of(action));
        when(staffManagement.findUserIdByStaffId(42L)).thenReturn(Optional.of(22L));

        service.addStaffActionEvidence(9L,42L,22L,"RESPONSE",java.util.List.of(evidenceRow));

        verify(actionEvidence).saveAll(java.util.List.of(evidenceRow));
    }

    private static FeedbackCase caseEntity() { FeedbackCase incident=new FeedbackCase(); incident.setCaseId(11L); incident.setCaseStatus("RECEIVED"); return incident; }
}
