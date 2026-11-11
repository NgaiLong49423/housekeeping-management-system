package com.housekeeping.management.service.impl;

import com.housekeeping.management.service.AccountAuthorizationService;
import com.housekeeping.management.service.FeedbackCaseService;
import com.housekeeping.management.service.BookingReservationService;

import com.housekeeping.management.dto.request.PortalForms.IncidentReviewForm;
import com.housekeeping.management.dto.request.PortalForms.IncidentItemDetails;
import com.housekeeping.management.dto.response.PortalViews.IncidentCard;
import com.housekeeping.management.dto.response.PortalViews.FeedbackCaseMessageCard;
import com.housekeeping.management.repository.FeedbackCaseRepository;
import com.housekeeping.management.repository.FeedbackCaseMessageRepository;
import com.housekeeping.management.repository.FeedbackCaseEvidenceRepository;
import com.housekeeping.management.repository.FeedbackCaseVerificationRepository;
import com.housekeeping.management.repository.StaffIncidentActionRepository;
import com.housekeeping.management.repository.UserRepository;
import com.housekeeping.management.entity.FeedbackCase;
import com.housekeeping.management.entity.FeedbackCaseMessage;
import com.housekeeping.management.entity.StaffIncidentAction;
import com.housekeeping.management.entity.FeedbackCaseVerification;
import com.housekeeping.management.dto.entity.FeedbackCaseVerificationDTO;
import com.housekeeping.management.dto.response.PortalViews.StaffIncidentActionCard;
import com.housekeeping.management.dto.response.PortalViews.StaffActionNoticeCard;
import com.housekeeping.management.dto.response.PortalViews.CaseVerificationCard;
import java.util.Set;
import java.util.List;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FeedbackCaseServiceImpl implements FeedbackCaseService {
    private static final Set<String> TYPES = Set.of("CLEANLINESS", "PROPERTY_DAMAGE", "MISSING_PROPERTY", "OTHER");
    private static final Set<String> REMEDIES = Set.of("RE_CLEAN", "REPAIR", "REPLACE", "PRICE_REDUCTION", "REFUND", "COMPENSATION", "OTHER");
    private static final Set<String> ACTIONS = Set.of("NONE", "COACHING", "PROPOSE_WARNING", "PROPOSE_FINAL_WARNING", "PROPOSE_RECOVERY", "OTHER");
    private final FeedbackCaseRepository cases;
    private final BookingReservationService bookings;
    private final FeedbackCaseMessageRepository messages;
    private final FeedbackCaseEvidenceRepository evidence;
    private final FeedbackCaseVerificationRepository verifications;
    private final StaffIncidentActionRepository actions;
    private final UserRepository users;
    private final AccountAuthorizationService authorization;

    public FeedbackCaseServiceImpl(FeedbackCaseRepository cases, BookingReservationService bookings,
            FeedbackCaseMessageRepository messages,
            FeedbackCaseEvidenceRepository evidence, FeedbackCaseVerificationRepository verifications,
            StaffIncidentActionRepository actions, UserRepository users,
            AccountAuthorizationService authorization) {
        this.cases = cases; this.bookings = bookings; this.messages = messages; this.evidence = evidence;
        this.verifications=verifications; this.actions = actions; this.users = users; this.authorization = authorization;
    }

    @Transactional
    public Long open(Long bookingId, Long customerId, java.util.List<String> types, String description,
            IncidentItemDetails itemDetails) {
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
        bookings.lockBookingForFeedback(bookingId)
                .orElseThrow(() -> new AccessDeniedException("Không có quyền gửi báo cáo cho lịch này."));
        feedbackId = cases.eligibleFeedbackId(bookingId, customerId)
                .orElseThrow(() -> new AccessDeniedException("Không có feedback đang mở thuộc booking của bạn."));
        if (cases.findByFeedbackId(feedbackId).isPresent())
            throw new IllegalStateException("Lịch này đã có hồ sơ sự cố. Hãy dùng phần Theo dõi sự cố để gửi bổ sung thông tin.");
        Long caseId=cases.create(feedbackId, types, description.trim(),
                itemDetails==null?null:cleanMessage(itemDetails.description()),
                itemDetails==null?null:itemDetails.estimatedValue(), itemDetails==null?null:itemDetails.ageMonths());
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

    public java.util.List<StaffIncidentActionCard> actions(Long caseId, Long adminId) {
        authorization.requireActiveAdmin(adminId);
        return actions.findAllByCaseIdOrderByCreatedAtAscActionIdAsc(caseId).stream().map(action ->
                new StaffIncidentActionCard(action.getActionId(), action.getActionType(), action.getStatus(),
                        action.getReason(), action.getStaffNotice(), action.getProposedBy(), action.getDecidedBy(),
                        action.getCreatedAt(), action.getDecidedAt(), action.getAcknowledgedAt())).toList();
    }

    @Transactional
    public void review(Long caseId, Long adminId, IncidentReviewForm form) {
        authorization.requireActiveAdmin(adminId);
        if (authorization.isManager(adminId))
            throw new AccessDeniedException("Phần xác minh và phản hồi khách do Admin cập nhật; Manager duyệt khoản tài chính riêng.");
        if (form==null || form.caseStatus()==null || !Set.of("INVESTIGATING", "VERIFIED", "NOT_SUBSTANTIATED").contains(form.caseStatus()))
            throw new IllegalArgumentException("Trạng thái xác minh không hợp lệ.");
        if (form.remedyType() != null && !form.remedyType().isBlank() && !REMEDIES.contains(form.remedyType()))
            throw new IllegalArgumentException("Biện pháp khắc phục không hợp lệ.");
        boolean resolutionWorkflow=cases.findById(caseId).map(FeedbackCase::isResolutionWorkflowRequired).orElse(false);
        if(resolutionWorkflow && (cleanMessage(form.remedyType())!=null || form.remedyCompleted()))
            throw new IllegalArgumentException("Với hồ sơ mới, biện pháp phải nằm trong phương án được khách chọn và Manager duyệt.");
        if (!resolutionWorkflow && Set.of("VERIFIED","NOT_SUBSTANTIATED").contains(form.caseStatus()) && (form.customerOutcome() == null || form.customerOutcome().isBlank()))
            throw new IllegalArgumentException("Hãy ghi kết quả xác minh hoặc phương án phản hồi khách.");
        if (form.remedyCompleted() && !Set.of("VERIFIED","NOT_SUBSTANTIATED").contains(form.caseStatus()))
            throw new IllegalArgumentException("Chỉ ghi nhận hoàn tất biện pháp sau khi xác minh xong.");
        if (form.remedyCompleted() && (form.remedyType()==null || form.remedyType().isBlank()))
            throw new IllegalArgumentException("Hãy chọn biện pháp khắc phục trước khi xác nhận đã thực hiện.");
        if (form.remedyCompleted() && "COMPENSATION".equals(form.remedyType())
                && !cases.hasSettledAcceptedCompensation(caseId))
            throw new IllegalStateException("Chỉ đánh dấu bồi thường hoàn tất sau khi khách xác nhận và khoản tiền đã được quyết toán.");
        FeedbackCase incident = cases.findByIdForUpdate(caseId)
                .orElseThrow(() -> new IllegalStateException("Hồ sơ không tồn tại hoặc đã giải quyết."));
        if ("RESOLVED".equals(incident.getCaseStatus()))
            throw new IllegalStateException("Hồ sơ không tồn tại hoặc đã giải quyết.");
        List<Long> reviewedIds=form.reviewedEvidenceIds()==null?List.of():form.reviewedEvidenceIds().stream().distinct().toList();
        Set<Long> caseEvidenceIds=evidence.findAllByCaseIdOrderByUploadedAtAscEvidenceIdAsc(caseId).stream()
                .map(item->item.getEvidenceId()).collect(java.util.stream.Collectors.toSet());
        if(!caseEvidenceIds.containsAll(reviewedIds)) throw new IllegalArgumentException("Có bằng chứng được chọn không thuộc hồ sơ này.");
        String previousOutcome=incident.getCustomerOutcome();
        incident.setCaseStatus(form.caseStatus()); incident.setRemedyType(cleanMessage(form.remedyType()));
        incident.setCustomerOutcome(cleanMessage(form.customerOutcome()));
        incident.setInternalFinding(cleanMessage(form.internalFinding())); incident.setRemedyCompleted(form.remedyCompleted());
        incident.setReviewedBy(adminId);
        incident.setReviewedAt(java.time.LocalDateTime.now(java.time.ZoneOffset.ofHours(7)).withNano(0));
        cases.save(incident);
        FeedbackCaseVerification verification=new FeedbackCaseVerification();
        verification.setCaseId(caseId); verification.setAdminId(adminId); verification.setCaseStatus(form.caseStatus());
        verification.setReviewedEvidenceIds(reviewedIds.stream().map(String::valueOf).collect(java.util.stream.Collectors.joining(",")));
        verification.setInternalFinding(cleanMessage(form.internalFinding()));
        verification.setCreatedAt(java.time.LocalDateTime.now(java.time.ZoneOffset.ofHours(7)).withNano(0));
        verifications.save(FeedbackCaseVerificationDTO.convertEntityToDTO(verification).convertDTOToEntity());
        String outcome=cleanMessage(form.customerOutcome());
        if(outcome!=null && !outcome.equals(previousOutcome)) {
            String update=outcome;
            if("RESOLVED".equals(incident.getCaseStatus())) throw new IllegalStateException("Hồ sơ đã được giải quyết.");
            messages.save(initialMessage(caseId, adminId, "ADMIN", update));
            cases.markFeedbackResponded(caseId,update);
        }
        if (form.staffAction() != null && !form.staffAction().isBlank()) {
            if (!ACTIONS.contains(form.staffAction())) throw new IllegalArgumentException("Mức xử lý nội bộ không hợp lệ.");
            if (!"NONE".equals(form.staffAction()) && (form.staffNotice()==null || form.staffNotice().isBlank()))
                throw new IllegalArgumentException("Cần ghi nội dung hướng dẫn/cảnh cáo sẽ thông báo cho Staff.");
            if (!"VERIFIED".equals(form.caseStatus()) || actions.existsByCaseIdAndStatus(caseId, "PENDING"))
                throw new IllegalStateException("Chỉ được đề xuất xử lý Staff sau khi xác minh và trước khi có quyết định cuối.");
            StaffIncidentAction action = new StaffIncidentAction();
            action.setCaseId(caseId); action.setStaffId(cases.staffIdForCase(caseId).orElseThrow());
            action.setActionType(form.staffAction()); action.setStatus("PENDING");
            action.setReason(incident.getInternalFinding() == null || incident.getInternalFinding().isBlank()
                    ? "Đề xuất theo kết quả xác minh" : incident.getInternalFinding());
            action.setStaffNotice(cleanMessage(form.staffNotice())); action.setProposedBy(adminId);
            action.setCreatedAt(java.time.LocalDateTime.now(java.time.ZoneOffset.ofHours(7)).withNano(0));
            actions.save(action);
            incident.setStaffAction(form.staffAction()); incident.setStaffActionStatus("PENDING");
            incident.setStaffActionBy(adminId); incident.setStaffActionApprovedBy(null); cases.save(incident);
        }
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

    @Transactional
    public void resolve(Long caseId, Long adminId) {
        authorization.requireActiveAdmin(adminId);
        int updated = cases.resolveCase(caseId);
        if (updated != 1) throw new IllegalStateException("Chỉ giải quyết sau khi xác minh xong và mọi khoản tiền đã được xử lý hoặc hủy.");
    }

    @Override
    @Transactional
    public void resolveAfterCustomerConfirmation(Long caseId, Long customerId) {
        if(!cases.customerOwnsCase(caseId,customerId)) throw new AccessDeniedException("Hồ sơ không thuộc booking của bạn.");
        int updated=cases.resolveCase(caseId);
        if(updated!=1) throw new IllegalStateException("Chưa đáp ứng điều kiện đóng hồ sơ; các khoản tiền, công việc hoặc phản hồi còn dang dở.");
    }

    @Override
    public java.util.List<com.housekeeping.management.dto.response.PortalViews.StaffActionNoticeCard> approvedActionsForStaff(Long staffId) {
        return actions.findAllByStaffIdAndStatusAndActionTypeNotOrderByDecidedAtDescActionIdDesc(staffId,"APPROVED","NONE")
                .stream().map(action -> new StaffActionNoticeCard(action.getActionId(), action.getActionType(),
                        action.getStaffNotice(), action.getCreatedAt(), action.getDecidedAt(), action.getAcknowledgedAt())).toList();
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
