package com.housekeeping.management.service.impl;

import com.housekeeping.management.service.AccountAuthorizationService;
import com.housekeeping.management.service.FeedbackCaseService;
import com.housekeeping.management.service.BookingReservationService;
import com.housekeeping.management.service.StaffManagementService;

import com.housekeeping.management.dto.request.PortalForms.IncidentReviewForm;
import com.housekeeping.management.dto.request.PortalForms.StaffIncidentActionForm;
import com.housekeeping.management.dto.request.PortalForms.IncidentItemDetails;
import com.housekeeping.management.dto.response.PortalViews.IncidentCard;
import com.housekeeping.management.dto.response.PortalViews.FeedbackCaseMessageCard;
import com.housekeeping.management.repository.FeedbackCaseRepository;
import com.housekeeping.management.repository.FeedbackCaseMessageRepository;
import com.housekeeping.management.repository.FeedbackCaseEvidenceRepository;
import com.housekeeping.management.repository.FeedbackCaseVerificationRepository;
import com.housekeeping.management.repository.StaffIncidentActionRepository;
import com.housekeeping.management.repository.StaffIncidentActionEvidenceRepository;
import com.housekeeping.management.repository.StaffRecoveryInstallmentRepository;
import com.housekeeping.management.repository.UserRepository;
import com.housekeeping.management.entity.FeedbackCase;
import com.housekeeping.management.entity.FeedbackCaseMessage;
import com.housekeeping.management.entity.StaffIncidentAction;
import com.housekeeping.management.entity.StaffIncidentActionEvidence;
import com.housekeeping.management.entity.StaffRecoveryInstallment;
import com.housekeeping.management.entity.FeedbackCaseVerification;
import com.housekeeping.management.dto.entity.FeedbackCaseVerificationDTO;
import com.housekeeping.management.dto.response.PortalViews.StaffIncidentActionCard;
import com.housekeeping.management.dto.response.PortalViews.StaffActionNoticeCard;
import com.housekeeping.management.dto.response.PortalViews.CaseVerificationCard;
import java.util.Set;
import java.util.List;
import java.time.LocalDateTime;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FeedbackCaseServiceImpl implements FeedbackCaseService {
    private static final java.time.ZoneOffset BUSINESS_ZONE = java.time.ZoneOffset.ofHours(7);
    private static final Set<String> TYPES = Set.of("CLEANLINESS", "PROPERTY_DAMAGE", "MISSING_PROPERTY", "OTHER");
    private static final Set<String> ACTIONS = Set.of("NONE", "COACHING", "PROPOSE_WARNING", "PROPOSE_FINAL_WARNING", "PROPOSE_RECOVERY", "OTHER");
    private static final Set<String> LIFECYCLE_STATUSES = Set.of("INVESTIGATING", "WAITING_CUSTOMER", "WAITING_MANAGER",
            "WAITING_PAYOUT", "WAITING_CUSTOMER_CONFIRMATION");
    private final FeedbackCaseRepository cases;
    private final BookingReservationService bookings;
    private final StaffManagementService staffManagement;
    private final FeedbackCaseMessageRepository messages;
    private final FeedbackCaseEvidenceRepository evidence;
    private final FeedbackCaseVerificationRepository verifications;
    private final StaffIncidentActionRepository actions;
    private final StaffIncidentActionEvidenceRepository actionEvidence;
    private final StaffRecoveryInstallmentRepository recoveryInstallments;
    private final UserRepository users;
    private final AccountAuthorizationService authorization;

    public FeedbackCaseServiceImpl(FeedbackCaseRepository cases, BookingReservationService bookings,
            StaffManagementService staffManagement,
            FeedbackCaseMessageRepository messages,
            FeedbackCaseEvidenceRepository evidence, FeedbackCaseVerificationRepository verifications,
            StaffIncidentActionRepository actions, UserRepository users,
            AccountAuthorizationService authorization, StaffIncidentActionEvidenceRepository actionEvidence,
            StaffRecoveryInstallmentRepository recoveryInstallments) {
        this.cases = cases; this.bookings = bookings; this.staffManagement=staffManagement; this.messages = messages; this.evidence = evidence;
        this.verifications=verifications; this.actions = actions; this.users = users; this.authorization = authorization;
        this.actionEvidence=actionEvidence; this.recoveryInstallments=recoveryInstallments;
    }

    @Override @Transactional
    public void acceptIntake(Long caseId, Long adminId) {
        authorization.requireActiveAdmin(adminId);
        if (authorization.isManager(adminId)) throw new AccessDeniedException("Chỉ Admin tiếp nhận và xử lý phản ánh.");
        FeedbackCase incident=cases.findByIdForUpdate(caseId).orElseThrow(() -> new IllegalArgumentException("Hồ sơ không tồn tại."));
        if (!"RECEIVED".equals(incident.getCaseStatus())) throw new IllegalStateException("Hồ sơ không còn chờ tiếp nhận.");
        incident.setCaseStatus("INVESTIGATING"); cases.save(incident);
    }

    @Override @Transactional
    public void startSearchingItem(Long caseId, Long adminId) {
        authorization.requireActiveAdmin(adminId);
        if (authorization.isManager(adminId)) throw new AccessDeniedException("Chỉ Admin xử lý việc tìm tài sản.");
        FeedbackCase incident=cases.findByIdForUpdate(caseId).orElseThrow(() -> new IllegalArgumentException("Hồ sơ không tồn tại."));
        if (!"INVESTIGATING".equals(incident.getCaseStatus()) || !incident.getIncidentTypes().contains("MISSING_PROPERTY"))
            throw new IllegalStateException("Chỉ phản ánh mất tài sản đang được xử lý mới chuyển sang tìm kiếm.");
        incident.setCaseStatus("SEARCHING_ITEM"); cases.save(incident);
    }

    @Override @Transactional
    public void sendOutcome(Long caseId, Long adminId, String outcome) {
        authorization.requireActiveAdmin(adminId);
        if (authorization.isManager(adminId)) throw new AccessDeniedException("Admin gửi kết quả xử lý cho khách.");
        String clean=cleanMessage(outcome);
        if(clean==null || clean.length()>4000) throw new IllegalArgumentException("Hãy nhập kết quả xử lý gửi khách.");
        FeedbackCase incident=cases.findByIdForUpdate(caseId).orElseThrow(() -> new IllegalArgumentException("Hồ sơ không tồn tại."));
        if (!Set.of("INVESTIGATING","SEARCHING_ITEM").contains(incident.getCaseStatus()))
            throw new IllegalStateException("Hồ sơ chưa ở bước có thể gửi kết quả.");
        if (incident.getIncidentTypes().contains("MISSING_PROPERTY") && !"SEARCHING_ITEM".equals(incident.getCaseStatus()))
            throw new IllegalStateException("Với phản ánh mất đồ, trước hết hãy ghi nhận bước tìm lại tài sản.");
        incident.setCustomerOutcome(clean); incident.setCaseStatus("WAITING_CUSTOMER"); cases.save(incident);
        messages.save(initialMessage(caseId,adminId,"ADMIN",clean)); cases.markFeedbackResponded(caseId,clean);
    }

    @Override @Transactional
    public void customerNeedsMoreHelp(Long caseId, Long bookingId, Long customerId, String message) {
        String clean=cleanMessage(message);
        FeedbackCase incident=cases.findByIdForUpdate(caseId).filter(item -> cases.customerCanMessage(caseId,bookingId,customerId))
                .orElseThrow(() -> new AccessDeniedException("Hồ sơ không thuộc booking của bạn."));
        if (!"WAITING_CUSTOMER".equals(incident.getCaseStatus())) throw new IllegalStateException("Hồ sơ chưa chờ phản hồi của khách.");
        if(clean==null || clean.length()>4000) throw new IllegalArgumentException("Hãy mô tả nội dung cần trao đổi.");
        incident.setCaseStatus("INVESTIGATING"); cases.save(incident);
        messages.save(initialMessage(caseId,customerId,"CUSTOMER",clean));
    }

    @Override @Transactional
    public void updateLifecycle(Long caseId, String status) {
        if (!LIFECYCLE_STATUSES.contains(status)) throw new IllegalArgumentException("Trạng thái hồ sơ không hợp lệ.");
        FeedbackCase incident=cases.findByIdForUpdate(caseId).orElseThrow(() -> new IllegalArgumentException("Hồ sơ không tồn tại."));
        if ("RESOLVED".equals(incident.getCaseStatus())) throw new IllegalStateException("Hồ sơ đã hoàn tất.");
        incident.setCaseStatus(status); cases.save(incident);
    }

    @Transactional
    public Long open(Long bookingId, Long customerId, java.util.List<String> types, String description,
            IncidentItemDetails itemDetails, String customerRequestedResolution, boolean hasEvidence) {
        if (!hasEvidence) throw new IllegalArgumentException("Cần gửi ít nhất một ảnh, tệp hoặc link video làm bằng chứng.");
        if (types==null || types.isEmpty() || types.size()>4 || types.stream().anyMatch(java.util.Objects::isNull) || types.stream().distinct().count()!=types.size()
                || !TYPES.containsAll(types) || description == null || description.isBlank() || description.length() > 4000)
            throw new IllegalArgumentException("Loại sự cố hoặc nội dung mô tả không hợp lệ.");
        boolean assetIncident=types.contains("PROPERTY_DAMAGE") || types.contains("MISSING_PROPERTY");
        if(assetIncident && (itemDetails==null || itemDetails.description()==null || itemDetails.description().isBlank()
                || itemDetails.description().length()>300))
            throw new IllegalArgumentException("Hãy nhập tên hoặc mô tả món đồ bị hư hại/mất.");
        if(itemDetails!=null && itemDetails.estimatedValue()!=null && itemDetails.estimatedValue().signum()<=0)
            throw new IllegalArgumentException("Giá trị ước tính phải lớn hơn 0.");
        if(itemDetails!=null && itemDetails.ageMonths()!=null && (itemDetails.ageMonths()<0 || itemDetails.ageMonths()>1200))
            throw new IllegalArgumentException("Thời gian đã sử dụng phải từ 0 đến 1.200 tháng.");
        Long feedbackId = cases.eligibleFeedbackId(bookingId, customerId)
                .orElseThrow(() -> new AccessDeniedException("Không có feedback đang mở thuộc booking của bạn."));
        var booking = bookings.lockBookingForFeedback(bookingId)
                .orElseThrow(() -> new AccessDeniedException("Không có quyền gửi báo cáo cho lịch này."));
        if (!"COMPLETED".equals(booking.status())) throw new IllegalStateException("Chỉ có thể phản ánh sau khi dịch vụ hoàn tất.");
        if (booking.serviceCompletedAt() == null)
            throw new IllegalStateException("Không xác định được thời điểm hoàn tất dịch vụ để kiểm tra hạn phản ánh.");
        if (java.time.LocalDateTime.now(BUSINESS_ZONE).withNano(0).isAfter(booking.serviceCompletedAt().plusHours(24)))
            throw new IllegalStateException("Đã quá 24 giờ kể từ khi nhân viên xác nhận hoàn tất dịch vụ.");
        feedbackId = cases.eligibleFeedbackId(bookingId, customerId)
                .orElseThrow(() -> new AccessDeniedException("Không có feedback đang mở thuộc booking của bạn."));
        if (cases.findByFeedbackId(feedbackId).isPresent())
            throw new IllegalStateException("Lịch này đã có hồ sơ sự cố. Hãy dùng phần Theo dõi sự cố để gửi bổ sung thông tin.");
        Long caseId=cases.create(feedbackId, types, description.trim(),
                itemDetails==null?null:cleanMessage(itemDetails.description()),
                itemDetails==null?null:itemDetails.estimatedValue(), itemDetails==null?null:itemDetails.ageMonths(),
                cleanMessage(customerRequestedResolution));
        messages.save(initialMessage(caseId, customerId, "CUSTOMER", description.trim()));
        return caseId;
    }

    public IncidentCard customerCase(Long bookingId, Long customerId) {
        return cases.feedbackIdForCustomerBooking(bookingId, customerId)
                .flatMap(cases::findForFeedback).orElse(null);
    }

    public IncidentCard adminCase(Long feedbackId, Long adminId) {
        authorization.requireActiveAdmin(adminId);
        return cases.findForFeedback(feedbackId).orElse(null);
    }

    @Override
    @Transactional(readOnly = true)
    public IncidentCard adminCaseById(Long caseId, Long adminId) {
        authorization.requireActiveAdmin(adminId);
        return cases.feedbackIdForCase(caseId).flatMap(cases::findForFeedback).orElse(null);
    }

    @Override
    public java.util.List<FeedbackCaseMessageCard> customerMessages(Long caseId, Long customerId) {
        if(!cases.customerOwnsCase(caseId,customerId))
            throw new AccessDeniedException("Hồ sơ sự cố không thuộc booking của bạn.");
        return messageCards(caseId);
    }

    @Override
    public java.util.List<FeedbackCaseMessageCard> adminMessages(Long caseId, Long adminId) {
        authorization.requireActiveAdmin(adminId);
        return messageCards(caseId);
    }

    @Override
    @Transactional
    public Long addCustomerMessage(Long caseId, Long bookingId, Long customerId, String text, boolean hasAttachments) {
        String clean=cleanMessage(text);
        validateMessage(clean,hasAttachments);
        FeedbackCase incident = cases.findByIdForUpdate(caseId)
                .filter(item -> cases.customerCanMessage(caseId, bookingId, customerId))
                .orElseThrow(() -> new AccessDeniedException("Hồ sơ không thuộc booking của bạn hoặc đã được giải quyết."));
        return messages.save(initialMessage(incident.getCaseId(), customerId, "CUSTOMER", clean)).getMessageId();
    }

    @Override
    @Transactional
    public Long addAdminMessage(Long caseId, Long adminId, String text, boolean hasAttachments) {
        authorization.requireActiveAdmin(adminId);
        String clean=cleanMessage(text);
        validateMessage(clean,hasAttachments);
        FeedbackCase incident = cases.findByIdForUpdate(caseId)
                .filter(item -> !"RESOLVED".equals(item.getCaseStatus()))
                .orElseThrow(() -> new IllegalStateException("Hồ sơ sự cố không tồn tại hoặc đã được giải quyết."));
        Long messageId = messages.save(initialMessage(incident.getCaseId(), adminId, "ADMIN", clean)).getMessageId();
        if(clean!=null && !hasAttachments) cases.markFeedbackResponded(caseId,clean);
        return messageId;
    }

    @Override
    @Transactional
    public void confirmAdminMessage(Long caseId, Long messageId, Long adminId) {
        authorization.requireActiveAdmin(adminId);
        String text=messages.findAdminMessageText(caseId,messageId,adminId).orElse(null);
        if(text!=null && !text.isBlank()) cases.markFeedbackResponded(caseId,text);
    }

    @Override
    @Transactional
    public void discardUnsentMessage(Long caseId, Long messageId, Long actorId, boolean admin) {
        if(admin) authorization.requireActiveAdmin(actorId);
        else if(!cases.customerOwnsCase(caseId,actorId)) throw new AccessDeniedException("Hồ sơ không thuộc booking của bạn.");
        messages.discardUnsent(caseId,messageId,actorId,admin?"ADMIN":"CUSTOMER");
    }

    private java.util.List<FeedbackCaseMessageCard> messageCards(Long caseId) {
        return messages.findAllByCaseIdOrderBySentAtAscMessageIdAsc(caseId).stream().map(message -> {
            String senderName = message.getSenderUserId() == null ? "Gọn Nhà"
                    : users.findById(message.getSenderUserId()).map(com.housekeeping.management.entity.User::getFullName)
                            .orElse(message.getSenderRole().equals("ADMIN") ? "Gọn Nhà" : "Khách hàng");
            var messageEvidence=evidence.findAllByMessageIdOrderByUploadedAtAscEvidenceIdAsc(message.getMessageId());
            List<Long> evidenceIds = messageEvidence.stream().filter(item->item.getImagePath()!=null)
                    .map(com.housekeeping.management.entity.FeedbackCaseEvidence::getEvidenceId).toList();
            List<String> externalUrls=messageEvidence.stream().map(com.housekeeping.management.entity.FeedbackCaseEvidence::getExternalUrl)
                    .filter(java.util.Objects::nonNull).toList();
            List<com.housekeeping.management.dto.response.PortalViews.ExternalEvidenceCard> externalEvidenceItems=messageEvidence.stream()
                    .filter(item->item.getExternalUrl()!=null).map(item->new com.housekeeping.management.dto.response.PortalViews.ExternalEvidenceCard(
                            item.getEvidenceId(),item.getExternalUrl())).toList();
            return new FeedbackCaseMessageCard(message.getMessageId(), caseId, message.getSenderUserId(),
                    message.getSenderRole(), senderName, message.getMessageText(), message.getSentAt(),
                    message.isLegacyImported(), message.isLegacyTimestampUnknown(), evidenceIds,externalUrls,externalEvidenceItems);
        }).toList();
    }

    private static FeedbackCaseMessage initialMessage(Long caseId, Long senderId, String role, String text) {
        FeedbackCaseMessage message = new FeedbackCaseMessage();
        message.setCaseId(caseId); message.setSenderUserId(senderId); message.setSenderRole(role);
        message.setMessageText(text); message.setSentAt(java.time.LocalDateTime.now(java.time.ZoneOffset.ofHours(7)).withNano(0));
        message.setLegacyImported(false); message.setLegacyTimestampUnknown(false);
        return message;
    }

    private static void validateMessage(String text, boolean hasAttachments) {
        if((text==null || text.isBlank()) && !hasAttachments)
            throw new IllegalArgumentException("Hãy nhập nội dung hoặc chọn ít nhất một ảnh đính kèm.");
        if(text!=null && text.length()>4000)
            throw new IllegalArgumentException("Mỗi tin nhắn tối đa 4.000 ký tự.");
    }

    private static String cleanMessage(String text) {
        return text==null || text.isBlank() ? null : text.trim();
    }

    @Transactional
    public java.util.List<StaffIncidentActionCard> actions(Long caseId, Long adminId) {
        authorization.requireActiveAdmin(adminId);
        finalizeExpiredStaffAppeals();
        finalizeExpiredStaffResponses();
        LocalDateTime now=LocalDateTime.now(BUSINESS_ZONE).withNano(0);
        return actions.findAllByCaseIdOrderByCreatedAtAscActionIdAsc(caseId).stream().map(action ->
                new StaffIncidentActionCard(action.getActionId(), action.getActionType(), action.getStatus(),
                        action.getReason(), action.getStaffNotice(), action.getProposedBy(), action.getDecidedBy(),
                        action.getCreatedAt(), action.getDecidedAt(), action.getAcknowledgedAt(), action.getResponseDeadline(), action.getStaffResponseExpiredAt(),
                        action.getStaffResponse(), action.getStaffRespondedAt(), action.getAppealText(), action.getAppealedAt(),
                        action.getDecisionReason(), action.getLiabilityAmount(), action.getFinalizedAt(),
                        actionEvidence.findAllByActionIdOrderByUploadedAtAscEvidenceIdAsc(action.getActionId()).stream()
                                .map(StaffIncidentActionEvidence::getEvidenceId).toList()))
                .map(card -> "AWAITING_STAFF".equals(card.status()) && card.responseDeadline()!=null && !card.responseDeadline().isAfter(now)
                        ? new StaffIncidentActionCard(card.id(),card.type(),"WAITING_MANAGER",card.reason(),card.staffNotice(),card.proposedBy(),card.decidedBy(),card.createdAt(),card.decidedAt(),card.acknowledgedAt(),card.responseDeadline(),card.staffResponseExpiredAt(),card.staffResponse(),card.staffRespondedAt(),card.appealText(),card.appealedAt(),card.decisionReason(),card.liabilityAmount(),card.finalizedAt(),card.evidenceIds()) : card).toList();
    }

    @Transactional
    public void review(Long caseId, Long adminId, IncidentReviewForm form) {
        authorization.requireActiveAdmin(adminId);
        if (authorization.isManager(adminId))
            throw new AccessDeniedException("Phần xác minh và phản hồi khách do Admin cập nhật; Manager duyệt khoản tài chính riêng.");
        if (form==null || form.caseStatus()==null || !Set.of("INVESTIGATING", "VERIFIED", "NOT_SUBSTANTIATED").contains(form.caseStatus()))
            throw new IllegalArgumentException("Trạng thái xác minh không hợp lệ.");
        FeedbackCase incident = cases.findByIdForUpdate(caseId)
                .orElseThrow(() -> new IllegalStateException("Hồ sơ không tồn tại hoặc đã giải quyết."));
        if ("RESOLVED".equals(incident.getCaseStatus()))
            throw new IllegalStateException("Hồ sơ không tồn tại hoặc đã giải quyết.");
        List<Long> reviewedIds=form.reviewedEvidenceIds()==null?List.of():form.reviewedEvidenceIds().stream().distinct().toList();
        Set<Long> caseEvidenceIds=evidence.findAllByCaseIdOrderByUploadedAtAscEvidenceIdAsc(caseId).stream()
                .map(item->item.getEvidenceId()).collect(java.util.stream.Collectors.toSet());
        if(!caseEvidenceIds.containsAll(reviewedIds)) throw new IllegalArgumentException("Có bằng chứng được chọn không thuộc hồ sơ này.");
        if ("VERIFIED".equals(form.caseStatus()) && reviewedIds.isEmpty())
            throw new IllegalStateException("Chỉ đánh dấu đã xác minh khi đã xem ít nhất một bằng chứng của hồ sơ.");
        if (!Set.of("RECEIVED","INVESTIGATING","SEARCHING_ITEM","WAITING_CUSTOMER").contains(incident.getCaseStatus()))
            throw new IllegalStateException("Hồ sơ đang ở bước khác và không thể sửa xác minh tại đây.");
        if ("RECEIVED".equals(incident.getCaseStatus())) incident.setCaseStatus("INVESTIGATING");
        incident.setInternalFinding(cleanMessage(form.internalFinding()));
        incident.setReviewedBy(adminId);
        incident.setReviewedAt(java.time.LocalDateTime.now(java.time.ZoneOffset.ofHours(7)).withNano(0));
        cases.save(incident);
        FeedbackCaseVerification verification=new FeedbackCaseVerification();
        verification.setCaseId(caseId); verification.setAdminId(adminId); verification.setCaseStatus(form.caseStatus());
        verification.setReviewedEvidenceIds(reviewedIds.stream().map(String::valueOf).collect(java.util.stream.Collectors.joining(",")));
        verification.setInternalFinding(cleanMessage(form.internalFinding()));
        verification.setCreatedAt(java.time.LocalDateTime.now(java.time.ZoneOffset.ofHours(7)).withNano(0));
        verifications.save(FeedbackCaseVerificationDTO.convertEntityToDTO(verification).convertDTOToEntity());
    }

    @Override @Transactional
    public void proposeStaffAction(Long caseId, Long adminId,
            com.housekeeping.management.dto.request.PortalForms.StaffIncidentActionForm form) {
        authorization.requireActiveAdmin(adminId);
        if (authorization.isManager(adminId)) throw new AccessDeniedException("Chỉ Admin đề xuất xử lý nội bộ Staff.");
        if (form == null || form.staffAction() == null || !ACTIONS.contains(form.staffAction())
                || "NONE".equals(form.staffAction())) throw new IllegalArgumentException("Chọn cách xử lý Staff cần Manager xem xét.");
        FeedbackCase incident=cases.findByIdForUpdate(caseId).orElseThrow(() -> new IllegalArgumentException("Hồ sơ không tồn tại."));
        if ("RESOLVED".equals(incident.getCaseStatus())) throw new IllegalStateException("Hồ sơ đã hoàn tất, không thể mở đề xuất xử lý Staff.");
        if (!"VERIFIED".equals(incident.getCaseStatus()) || incident.getReviewedAt() == null
                || !hasSufficientEvidence(caseId)) {
            throw new IllegalStateException("Chỉ đề xuất xử lý Staff khi hồ sơ đã xác minh và có bằng chứng được Admin rà soát.");
        }
        if(!"NOT_PROPOSED".equals(incident.getStaffActionStatus())) throw new IllegalStateException("Hồ sơ này đã có luồng xử lý Staff.");
        if (actions.existsByCaseIdAndStatus(caseId,"PENDING")) throw new IllegalStateException("Đã có đề xuất Staff đang chờ Manager.");
        LocalDateTime now=LocalDateTime.now(java.time.ZoneOffset.ofHours(7)).withNano(0);
        StaffIncidentAction action=new StaffIncidentAction();
        action.setCaseId(caseId); action.setStaffId(cases.staffIdForCase(caseId).orElseThrow());
        action.setActionType(form.staffAction()); action.setStatus("AWAITING_STAFF");
        String reason=incident.getInternalFinding()==null?"Đề xuất theo bằng chứng đã xác minh":incident.getInternalFinding();
        action.setReason(reason.length()>1000?reason.substring(0,1000):reason);
        action.setStaffNotice(cleanMessage(form.staffNotice())); action.setProposedBy(adminId); action.setCreatedAt(now);
        action.setResponseDeadline(now.plusHours(48));
        actions.save(action);
        incident.setStaffAction(form.staffAction()); incident.setStaffActionStatus("AWAITING_STAFF"); incident.setStaffActionBy(adminId);
        incident.setStaffActionApprovedBy(null); cases.save(incident);
    }

    @Override @Transactional(readOnly=true)
    public List<CaseVerificationCard> verificationHistory(Long caseId, Long adminId) {
        authorization.requireActiveAdmin(adminId);
        if(cases.feedbackIdForCase(caseId).isEmpty()) throw new IllegalArgumentException("Hồ sơ không tồn tại.");
        return verifications.findAllByCaseIdOrderByCreatedAtDescVerificationIdDesc(caseId).stream()
                .map(FeedbackCaseVerificationDTO::convertEntityToDTO)
                .map(dto->new CaseVerificationCard(dto.verificationId(),dto.adminId(),dto.caseStatus(),
                        dto.reviewedEvidenceIds()==null||dto.reviewedEvidenceIds().isBlank()?List.of():java.util.Arrays.stream(dto.reviewedEvidenceIds().split(","))
                                .map(String::trim).filter(s->!s.isEmpty()).map(Long::valueOf).toList(),dto.internalFinding(),dto.createdAt()))
                .toList();
    }

    @Override @Transactional(readOnly=true)
    public boolean hasSufficientEvidence(Long caseId) {
        return verifications.findAllByCaseIdOrderByCreatedAtDescVerificationIdDesc(caseId).stream()
                .findFirst()
                .filter(verification -> "VERIFIED".equals(verification.getCaseStatus()))
                .map(verification -> verification.getReviewedEvidenceIds() != null
                        && !verification.getReviewedEvidenceIds().isBlank())
                .orElse(false);
    }

    @Transactional
    public void decideStaffAction(Long caseId, Long managerId, boolean approve) {
        authorization.requireManager(managerId);
        StaffIncidentAction pending = actions.findFirstByCaseIdAndStatusOrderByActionIdDesc(caseId, "PENDING")
                .orElseThrow(() -> new IllegalStateException("Không thể duyệt: chưa có đề xuất chờ xử lý."));
        String status = approve ? "APPROVED" : "REJECTED";
        if (actions.decide(pending.getActionId(), managerId, status,
                java.time.LocalDateTime.now(java.time.ZoneOffset.ofHours(7)).withNano(0)) != 1)
            throw new IllegalStateException("Không thể duyệt: quyết định chưa được đề xuất hoặc người duyệt trùng người đề xuất.");
        cases.findByIdForUpdate(caseId).ifPresent(incident -> {
            incident.setStaffActionStatus(status); incident.setStaffActionApprovedBy(managerId); cases.save(incident);
        });
    }

    @Override @Transactional
    public void decideStaffAction(Long caseId, Long managerId, String decision, java.math.BigDecimal liabilityAmount,
            String reason, boolean appealDecision) {
        authorization.requireManager(managerId);
        finalizeExpiredStaffResponses();
        Set<String> valid=Set.of("NO_LIABILITY","INTERNAL_ACTION","RECOVERY");
        if(decision==null || !valid.contains(decision) || reason==null || reason.isBlank())
            throw new IllegalArgumentException("Chọn kết luận và ghi căn cứ của Manager.");
        StaffIncidentAction action=actions.findFirstByCaseIdAndStatusInOrderByActionIdDesc(caseId,
                appealDecision?Set.of("WAITING_MANAGER_APPEAL"):Set.of("WAITING_MANAGER"))
                .orElseThrow(()->new IllegalStateException("Hồ sơ Staff chưa ở bước Manager kết luận."));
        if(appealDecision?!"WAITING_MANAGER_APPEAL".equals(action.getStatus()):!"WAITING_MANAGER".equals(action.getStatus()))
            throw new IllegalStateException("Staff chưa giải trình hoặc chưa hết hạn 48 giờ; chưa thể kết luận.");
        if(action.getProposedBy()!=null && action.getProposedBy().equals(managerId))
            throw new AccessDeniedException("Manager không thể duyệt đề xuất do chính mình lập.");
        if("RECOVERY".equals(decision) && (liabilityAmount==null || liabilityAmount.signum()<=0))
            throw new IllegalArgumentException("Kết luận có thu hồi phải có số tiền và căn cứ.");
        if("RECOVERY".equals(decision) && !"PROPOSE_RECOVERY".equals(action.getActionType()))
            throw new IllegalStateException("Admin chưa đề xuất xem xét trách nhiệm vật chất cho hồ sơ này.");
        if(!"RECOVERY".equals(decision)) liabilityAmount=java.math.BigDecimal.ZERO;
        LocalDateTime now=LocalDateTime.now(BUSINESS_ZONE).withNano(0);
        if(appealDecision) {
            action.setAppealDecidedBy(managerId); action.setAppealDecidedAt(now);
            action.setDecidedBy(managerId); action.setDecidedAt(now);
            action.setDecisionReason(reason.trim()); action.setDecisionOutcome(decision); action.setLiabilityAmount(liabilityAmount);
            action.setStatus(finalStaffStatus(decision,liabilityAmount)); action.setFinalizedAt(now);
        } else {
            action.setStatus("WAITING_STAFF_APPEAL"); action.setDecidedBy(managerId); action.setDecidedAt(now);
            action.setResponseDeadline(now.plusHours(48)); action.setDecisionReason(reason.trim());
            action.setDecisionOutcome(decision); action.setLiabilityAmount(liabilityAmount); action.setFinalizedAt(null);
        }
        actions.save(action);
        cases.findByIdForUpdate(caseId).ifPresent(incident->{
            incident.setStaffActionStatus(action.getStatus()); incident.setStaffActionApprovedBy(managerId); cases.save(incident);
        });
    }

    private String finalStaffStatus(String decision, java.math.BigDecimal amount) {
        if("NO_LIABILITY".equals(decision)) return "NO_LIABILITY";
        if("INTERNAL_ACTION".equals(decision)) return "INTERNAL_ACTION";
        return amount!=null && amount.signum()>0 ? "RECOVERY_DUE" : "NO_LIABILITY";
    }

    @Override @Transactional
    public List<StaffActionNoticeCard> ownStaffActions(Long staffId) {
        finalizeExpiredStaffAppeals();
        finalizeExpiredStaffResponses();
        return actions.findAllByStaffIdAndActionTypeNotOrderByCreatedAtDescActionIdDesc(staffId,"NONE").stream()
                .map(this::staffNoticeCard)
                .toList();
    }

    private StaffActionNoticeCard staffNoticeCard(StaffIncidentAction action) {
        return new StaffActionNoticeCard(action.getActionId(),action.getActionType(),action.getStaffNotice(),
                action.getCreatedAt(),action.getDecidedAt(),action.getAcknowledgedAt(),action.getStatus(),action.getReason(),
                action.getStaffResponse(),action.getResponseDeadline(),action.getStaffResponseExpiredAt(),action.getDecisionReason(),action.getLiabilityAmount(),
                action.getAppealText(),action.getFinalizedAt(),actionEvidence.findAllByActionIdOrderByUploadedAtAscEvidenceIdAsc(action.getActionId()).stream()
                        .map(StaffIncidentActionEvidence::getEvidenceId).toList());
    }

    @Override @Transactional
    public void respondToStaffAction(Long actionId, Long staffId, String response, boolean appeal) {
        StaffIncidentAction action=actions.findByIdForUpdate(actionId)
                .filter(item -> item.getStaffId()!=null && item.getStaffId().equals(staffId))
                .orElseThrow(()->new AccessDeniedException("Đề xuất không tồn tại hoặc không thuộc tài khoản của bạn."));
        String text=cleanMessage(response);
        if(text==null || text.length()>4000) throw new IllegalArgumentException("Hãy nhập nội dung giải trình/phản đối (tối đa 4.000 ký tự).");
        LocalDateTime now=LocalDateTime.now(BUSINESS_ZONE).withNano(0);
        if(appeal) {
            if(!"WAITING_STAFF_APPEAL".equals(action.getStatus()) || action.getResponseDeadline()==null || now.isAfter(action.getResponseDeadline()))
                throw new IllegalStateException("Thời hạn phản đối 48 giờ đã hết hoặc hồ sơ chưa có kết luận.");
            if(action.getAppealedAt()!=null) throw new IllegalStateException("Mỗi quyết định chỉ được phản đối một lần.");
            action.setAppealText(text); action.setAppealedAt(now); action.setStatus("WAITING_MANAGER_APPEAL");
        } else {
            if(!"AWAITING_STAFF".equals(action.getStatus()) || action.getResponseDeadline()==null || now.isAfter(action.getResponseDeadline()))
                throw new IllegalStateException("Đã hết hạn giải trình hoặc hồ sơ không còn chờ Staff.");
            action.setStaffResponse(text); action.setStaffRespondedAt(now); action.setStatus("WAITING_MANAGER");
        }
        actions.save(action);
        cases.findByIdForUpdate(action.getCaseId()).ifPresent(incident->{ incident.setStaffActionStatus(action.getStatus()); cases.save(incident); });
    }

    @Override @Transactional
    public void recordStaffRecoveryInstallment(Long actionId, Long adminId,
            com.housekeeping.management.dto.request.PortalForms.StaffRecoveryInstallmentForm form, String receiptPath) {
        authorization.requireActiveAdmin(adminId);
        if(authorization.isManager(adminId)) throw new AccessDeniedException("Admin ghi nhận khoản Staff thực trả.");
        StaffIncidentAction action=actions.findByIdForUpdate(actionId).orElseThrow(()->new IllegalArgumentException("Khoản thu hồi không tồn tại."));
        if(!"RECOVERY_DUE".equals(action.getStatus()) || action.getLiabilityAmount()==null)
            throw new IllegalStateException("Chỉ ghi nhận khi Manager đã chốt trách nhiệm Staff.");
        if(form==null || form.amount()==null || form.amount().signum()<=0 || form.transactionReference()==null || form.transactionReference().isBlank()
                || !Set.of("CASH","BANK_TRANSFER","OTHER").contains(form.paymentMethod()))
            throw new IllegalArgumentException("Nhập số tiền, phương thức và mã tham chiếu hợp lệ.");
        if(receiptPath==null || receiptPath.isBlank()) throw new IllegalArgumentException("Cần lưu chứng từ cho khoản Staff thực trả.");
        java.math.BigDecimal already=recoveryInstallments.sumByActionId(actionId);
        if(already.add(form.amount()).compareTo(action.getLiabilityAmount())>0)
            throw new IllegalArgumentException("Khoản thực nhận vượt quá số tiền Manager đã chốt.");
        StaffRecoveryInstallment installment=new StaffRecoveryInstallment(); installment.setActionId(actionId);
        installment.setAmount(form.amount()); installment.setPaymentMethod(form.paymentMethod());
        installment.setTransactionReference(form.transactionReference().trim()); installment.setReceiptPath(receiptPath);
        installment.setReceivedBy(adminId); installment.setReceivedAt(LocalDateTime.now(BUSINESS_ZONE).withNano(0));
        recoveryInstallments.save(installment);
        if(already.add(form.amount()).compareTo(action.getLiabilityAmount())==0) action.setStatus("RECOVERY_SETTLED");
        actions.save(action);
    }

    @Override @Transactional(readOnly=true)
    public List<com.housekeeping.management.dto.response.PortalViews.StaffRecoveryPaymentCard> staffRecoveryPayments(Long actionId, Long adminId) {
        authorization.requireActiveAdmin(adminId);
        return recoveryInstallments.findAllByActionIdOrderByReceivedAtAscInstallmentIdAsc(actionId).stream()
                .map(item->new com.housekeeping.management.dto.response.PortalViews.StaffRecoveryPaymentCard(item.getInstallmentId(),item.getActionId(),item.getAmount(),item.getPaymentMethod(),item.getTransactionReference(),item.getReceivedAt(),item.getReceiptPath()!=null)).toList();
    }

    @Override @Transactional(readOnly=true)
    public java.math.BigDecimal totalStaffRecoveryCollected() { return recoveryInstallments.sumAllReceived(); }

    @Override @Transactional(readOnly=true)
    public java.nio.file.Path staffRecoveryReceiptPath(Long installmentId, Long adminId) {
        authorization.requireActiveAdmin(adminId);
        String path=recoveryInstallments.findById(installmentId).map(StaffRecoveryInstallment::getReceiptPath).orElse(null);
        if(path==null) throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND);
        return java.nio.file.Path.of(path);
    }

    @Override @Transactional
    public boolean staffInvestigationFinalized(Long caseId) {
        finalizeExpiredStaffResponses();
        finalizeExpiredStaffAppeals();
        return actions.findFirstByCaseIdAndStatusInOrderByActionIdDesc(caseId,Set.of("PENDING","AWAITING_STAFF","WAITING_MANAGER","WAITING_STAFF_APPEAL","WAITING_MANAGER_APPEAL"))
                .isEmpty();
    }

    private void finalizeExpiredStaffAppeals() {
        LocalDateTime now=LocalDateTime.now(BUSINESS_ZONE).withNano(0);
        List<StaffIncidentAction> expired=actions.findAllByStatusAndResponseDeadlineLessThanEqual("WAITING_STAFF_APPEAL",now);
        for(StaffIncidentAction candidate:expired) {
            StaffIncidentAction action=actions.findByIdForUpdate(candidate.getActionId()).orElse(null);
            if(action==null || !"WAITING_STAFF_APPEAL".equals(action.getStatus()) || action.getResponseDeadline()==null || action.getResponseDeadline().isAfter(now)) continue;
            String finalStatus=finalStaffStatus(action.getDecisionOutcome(),action.getLiabilityAmount());
            action.setStatus(finalStatus); action.setFinalizedAt(now); actions.save(action);
            cases.findByIdForUpdate(action.getCaseId()).ifPresent(incident->{ incident.setStaffActionStatus(finalStatus); cases.save(incident); });
        }
    }

    private void finalizeExpiredStaffResponses() {
        LocalDateTime now=LocalDateTime.now(BUSINESS_ZONE).withNano(0);
        List<StaffIncidentAction> expired=actions.findAllByStatusAndResponseDeadlineLessThanEqual("AWAITING_STAFF",now);
        for(StaffIncidentAction candidate:expired) {
            StaffIncidentAction action=actions.findByIdForUpdate(candidate.getActionId()).orElse(null);
            if(action==null || !"AWAITING_STAFF".equals(action.getStatus()) || action.getResponseDeadline()==null || action.getResponseDeadline().isAfter(now)) continue;
            action.setStaffResponseExpiredAt(action.getResponseDeadline());
            action.setStatus("WAITING_MANAGER");
            actions.save(action);
            cases.findByIdForUpdate(action.getCaseId()).ifPresent(incident->{ incident.setStaffActionStatus("WAITING_MANAGER"); cases.save(incident); });
        }
    }

    @Override @Transactional(readOnly=true)
    public boolean staffOwnsAction(Long actionId, Long staffId) { return actions.findByActionIdAndStaffId(actionId,staffId).isPresent(); }

    @Override @Transactional(readOnly=true)
    public boolean adminCanViewStaffAction(Long actionId, Long adminId) { authorization.requireActiveAdmin(adminId); return actions.existsById(actionId); }

    @Override @Transactional
    public void addStaffActionEvidence(Long actionId, Long staffId, Long staffUserId, String stage, List<StaffIncidentActionEvidence> uploads) {
        StaffIncidentAction action=actions.findByActionIdAndStaffId(actionId,staffId).orElseThrow(()->new AccessDeniedException("Không có quyền gửi bằng chứng cho hồ sơ Staff này."));
        LocalDateTime now=LocalDateTime.now(BUSINESS_ZONE).withNano(0);
        boolean valid="RESPONSE".equals(stage)?"AWAITING_STAFF".equals(action.getStatus()) && action.getResponseDeadline()!=null && !now.isAfter(action.getResponseDeadline()):
                "APPEAL".equals(stage)&&"WAITING_STAFF_APPEAL".equals(action.getStatus()) && action.getResponseDeadline()!=null && !now.isAfter(action.getResponseDeadline());
        if(!valid || uploads==null || uploads.isEmpty()) throw new IllegalStateException("Bằng chứng không thuộc bước hiện tại hoặc chưa có tệp.");
        if(actionEvidence.countByActionIdAndEvidenceStage(actionId,stage)+uploads.size()>5)
            throw new IllegalArgumentException("Mỗi lần giải trình tối đa 5 tệp.");
        Long expectedUserId=staffManagement.findUserIdByStaffId(staffId).orElse(null);
        if(staffUserId==null || !staffUserId.equals(expectedUserId)
                || uploads.stream().anyMatch(upload->!staffUserId.equals(upload.getUploadedBy())))
            throw new AccessDeniedException("Người tải bằng chứng không khớp tài khoản Staff.");
        actionEvidence.saveAll(uploads);
    }

    @Override @Transactional(readOnly=true)
    public java.nio.file.Path staffActionEvidencePath(Long evidenceId, Long actorId, boolean admin) {
        if(admin) authorization.requireActiveAdmin(actorId);
        StaffIncidentActionEvidence item=actionEvidence.findById(evidenceId).orElseThrow(()->new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND));
        if(!admin && !staffOwnsAction(item.getActionId(),actorId)) throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND);
        return java.nio.file.Path.of(item.getStoragePath());
    }

    @Override
    @Transactional
    public void resolveAfterCustomerConfirmation(Long caseId, Long customerId) {
        if(!cases.customerOwnsCase(caseId,customerId)) throw new AccessDeniedException("Hồ sơ không thuộc booking của bạn.");
        FeedbackCase incident=cases.findByIdForUpdate(caseId).orElseThrow(() -> new IllegalArgumentException("Hồ sơ không tồn tại."));
        if (!Set.of("WAITING_CUSTOMER", "WAITING_CUSTOMER_CONFIRMATION").contains(incident.getCaseStatus()))
            throw new IllegalStateException("Hồ sơ chưa chờ khách xác nhận kết quả.");
        int updated=cases.resolveCase(caseId);
        if(updated!=1) throw new IllegalStateException("Hồ sơ còn khoản tiền hoặc xử lý nội bộ chưa hoàn tất.");
    }

    @Override
    @Transactional
    public java.util.List<com.housekeeping.management.dto.response.PortalViews.StaffActionNoticeCard> approvedActionsForStaff(Long staffId) {
        return ownStaffActions(staffId);
    }

    @Override
    @Transactional
    public void acknowledgeStaffAction(Long actionId, Long staffId) {
        if (actions.acknowledge(actionId, staffId,
                java.time.LocalDateTime.now(java.time.ZoneOffset.ofHours(7)).withNano(0)) != 1) {
            throw new IllegalStateException("Thông báo không tồn tại, không thuộc tài khoản của bạn hoặc đã được xác nhận.");
        }
    }

    @Override
    @Transactional(readOnly = true)
    public boolean belongsToBooking(Long caseId, Long bookingId) {
        return cases.belongsToBooking(caseId, bookingId);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean authorizesStaffRecovery(Long caseId, Long bookingId) {
        return cases.authorizesStaffRecovery(caseId, bookingId);
    }

    @Override
    @Transactional(readOnly = true)
    public Long initialCustomerMessageId(Long caseId) {
        return cases.initialCustomerMessageId(caseId);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean authorizesMessageEvidence(Long caseId, Long messageId, Long actorId, boolean admin, Long bookingId) {
        return cases.authorizesMessageEvidence(caseId, messageId, actorId, admin ? "ADMIN" : "CUSTOMER", admin, bookingId);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean customerOwnsEvidence(Long evidenceId, Long customerId) {
        return evidence.customerOwnsEvidence(evidenceId, customerId);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean customerOwnsCase(Long caseId, Long customerId) {
        return cases.customerOwnsCase(caseId, customerId);
    }
}
