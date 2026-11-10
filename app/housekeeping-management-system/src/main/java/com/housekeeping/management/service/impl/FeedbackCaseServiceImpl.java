package com.housekeeping.management.service.impl;

import com.housekeeping.management.service.AccountAuthorizationService;
import com.housekeeping.management.service.FeedbackCaseService;

import com.housekeeping.management.dto.request.PortalForms.IncidentReviewForm;
import com.housekeeping.management.dto.response.PortalViews.IncidentCard;
import com.housekeeping.management.dto.response.PortalViews.FeedbackCaseMessageCard;
import com.housekeeping.management.repository.FeedbackCaseRepository;
import java.util.Set;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FeedbackCaseServiceImpl implements FeedbackCaseService {
    private static final Set<String> TYPES = Set.of("CLEANLINESS", "PROPERTY_DAMAGE", "MISSING_PROPERTY", "OTHER");
    private static final Set<String> REMEDIES = Set.of("RE_CLEAN", "REPAIR", "REPLACE", "PRICE_REDUCTION", "REFUND", "COMPENSATION", "OTHER");
    private static final Set<String> ACTIONS = Set.of("NONE", "COACHING", "PROPOSE_WARNING", "PROPOSE_FINAL_WARNING", "PROPOSE_RECOVERY", "OTHER");
    private final JdbcTemplate jdbc;
    private final FeedbackCaseRepository cases;
    private final AccountAuthorizationService authorization;

    public FeedbackCaseServiceImpl(JdbcTemplate jdbc, FeedbackCaseRepository cases, AccountAuthorizationService authorization) {
        this.jdbc = jdbc; this.cases = cases; this.authorization = authorization;
    }

    @Transactional
    public Long open(Long bookingId, Long customerId, java.util.List<String> types, String description) {
        if (types==null || types.isEmpty() || types.size()>4 || types.stream().anyMatch(java.util.Objects::isNull) || types.stream().distinct().count()!=types.size()
                || !TYPES.containsAll(types) || description == null || description.isBlank() || description.length() > 4000)
            throw new IllegalArgumentException("Loại sự cố hoặc nội dung mô tả không hợp lệ.");
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM dbo.Feedbacks f JOIN dbo.Bookings b ON b.booking_id=f.booking_id
                WHERE f.booking_id=? AND f.customer_id=? AND b.customer_id=? AND b.status='COMPLETED'
                  AND f.status<>'RESOLVED'
                """, Integer.class, bookingId, customerId, customerId);
        if (count == null || count != 1) throw new AccessDeniedException("Không có feedback đang mở thuộc booking của bạn.");
        Long feedbackId = jdbc.queryForObject("SELECT feedback_id FROM dbo.Feedbacks WHERE booking_id=? AND customer_id=?", Long.class, bookingId, customerId);
        if (cases.findForFeedback(feedbackId).isPresent()) throw new IllegalStateException("Booking này đã có hồ sơ sự cố.");
        Long caseId=cases.create(feedbackId, types, description.trim());
        cases.createInitialMessage(caseId,customerId,description.trim());
        return caseId;
    }

    public IncidentCard customerCase(Long bookingId, Long customerId) {
        return jdbc.query("SELECT feedback_id FROM dbo.Feedbacks WHERE booking_id=? AND customer_id=?",
                (rs, row) -> rs.getLong(1), bookingId, customerId).stream().findFirst()
                .flatMap(cases::findForFeedback).orElse(null);
    }

    public IncidentCard adminCase(Long feedbackId, Long adminId) {
        authorization.requireActiveAdmin(adminId);
        return cases.findForFeedback(feedbackId).orElse(null);
    }

    @Override
    public java.util.List<FeedbackCaseMessageCard> customerMessages(Long caseId, Long customerId) {
        if(!cases.customerOwnsCase(caseId,customerId))
            throw new AccessDeniedException("Hồ sơ sự cố không thuộc booking của bạn.");
        return cases.messages(caseId).stream().map(message -> withEvidence(message,caseId)).toList();
    }

    @Override
    public java.util.List<FeedbackCaseMessageCard> adminMessages(Long caseId, Long adminId) {
        authorization.requireActiveAdmin(adminId);
        return cases.messages(caseId).stream().map(message -> withEvidence(message,caseId)).toList();
    }

    @Override
    @Transactional
    public Long addCustomerMessage(Long caseId, Long bookingId, Long customerId, String text, boolean hasAttachments) {
        String clean=cleanMessage(text);
        validateMessage(clean,hasAttachments);
        Long messageId=cases.createCustomerMessage(caseId,bookingId,customerId,clean);
        if(messageId==null) throw new AccessDeniedException("Hồ sơ không thuộc booking của bạn hoặc đã được giải quyết.");
        return messageId;
    }

    @Override
    @Transactional
    public Long addAdminMessage(Long caseId, Long adminId, String text, boolean hasAttachments) {
        authorization.requireActiveAdmin(adminId);
        String clean=cleanMessage(text);
        validateMessage(clean,hasAttachments);
        Long messageId=cases.createAdminMessage(caseId,adminId,clean);
        if(messageId==null) throw new IllegalStateException("Hồ sơ sự cố không tồn tại hoặc đã được giải quyết.");
        if(clean!=null && !hasAttachments) cases.markFeedbackResponded(caseId,clean);
        return messageId;
    }

    @Override
    @Transactional
    public void confirmAdminMessage(Long caseId, Long messageId, Long adminId) {
        authorization.requireActiveAdmin(adminId);
        String text=cases.adminMessageText(caseId,messageId,adminId);
        if(text!=null && !text.isBlank()) cases.markFeedbackResponded(caseId,text);
    }

    @Override
    @Transactional
    public void discardUnsentMessage(Long caseId, Long messageId, Long actorId, boolean admin) {
        if(admin) authorization.requireActiveAdmin(actorId);
        else if(!cases.customerOwnsCase(caseId,actorId)) throw new AccessDeniedException("Hồ sơ không thuộc booking của bạn.");
        cases.discardUnsentMessage(caseId,messageId,actorId,admin?"ADMIN":"CUSTOMER");
    }

    private FeedbackCaseMessageCard withEvidence(FeedbackCaseMessageCard message, Long caseId) {
        return new FeedbackCaseMessageCard(message.id(),caseId,message.senderId(),message.senderRole(),message.senderName(),
                message.text(),message.sentAt(),message.legacyImported(),message.legacyTimestampUnknown(),cases.messageEvidenceIds(message.id()));
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

    public java.util.List<com.housekeeping.management.dto.response.PortalViews.StaffIncidentActionCard> actions(Long caseId, Long adminId) {
        authorization.requireActiveAdmin(adminId);
        return cases.actions(caseId);
    }

    @Transactional
    public void review(Long caseId, Long adminId, IncidentReviewForm form) {
        authorization.requireActiveAdmin(adminId);
        if (form==null || form.caseStatus()==null || !Set.of("INVESTIGATING", "VERIFIED", "NOT_SUBSTANTIATED").contains(form.caseStatus()))
            throw new IllegalArgumentException("Trạng thái xác minh không hợp lệ.");
        if (form.remedyType() != null && !form.remedyType().isBlank() && !REMEDIES.contains(form.remedyType()))
            throw new IllegalArgumentException("Biện pháp khắc phục không hợp lệ.");
        if (Set.of("VERIFIED","NOT_SUBSTANTIATED").contains(form.caseStatus()) && (form.customerOutcome() == null || form.customerOutcome().isBlank()))
            throw new IllegalArgumentException("Hãy ghi kết quả xác minh hoặc phương án phản hồi khách.");
        if (form.remedyCompleted() && !Set.of("VERIFIED","NOT_SUBSTANTIATED").contains(form.caseStatus()))
            throw new IllegalArgumentException("Chỉ ghi nhận hoàn tất biện pháp sau khi xác minh xong.");
        String previousOutcome=cases.customerOutcome(caseId);
        if (cases.updateReview(caseId, form.caseStatus(), form.remedyType(), form.customerOutcome(), form.internalFinding(), form.remedyCompleted(), adminId) != 1)
            throw new IllegalStateException("Hồ sơ không tồn tại hoặc đã giải quyết.");
        String outcome=cleanMessage(form.customerOutcome());
        if(outcome!=null && !outcome.equals(previousOutcome)) {
            String update=outcome;
            if(cases.createAdminMessage(caseId,adminId,update)==null) throw new IllegalStateException("Hồ sơ đã được giải quyết.");
            cases.markFeedbackResponded(caseId,update);
        }
        if (form.staffAction() != null && !form.staffAction().isBlank()) {
            if (!ACTIONS.contains(form.staffAction())) throw new IllegalArgumentException("Mức xử lý nội bộ không hợp lệ.");
            if (!"NONE".equals(form.staffAction()) && (form.staffNotice()==null || form.staffNotice().isBlank()))
                throw new IllegalArgumentException("Cần ghi nội dung hướng dẫn/cảnh cáo sẽ thông báo cho Staff.");
            if (cases.proposeStaffAction(caseId, form.staffAction(), form.staffNotice(), adminId) != 1)
                throw new IllegalStateException("Chỉ được đề xuất xử lý Staff sau khi xác minh và trước khi có quyết định cuối.");
        }
    }

    @Transactional
    public void decideStaffAction(Long caseId, Long managerId, boolean approve) {
        authorization.requireManager(managerId);
        if (cases.decideStaffAction(caseId, approve, managerId) != 1)
            throw new IllegalStateException("Không thể duyệt: quyết định chưa được đề xuất hoặc người duyệt trùng người đề xuất.");
    }

    @Transactional
    public void resolve(Long caseId, Long adminId) {
        authorization.requireActiveAdmin(adminId);
        int updated = jdbc.update("""
                UPDATE dbo.Feedback_Cases SET case_status='RESOLVED', resolved_at=SYSDATETIME()
                WHERE case_id=? AND case_status IN ('VERIFIED','NOT_SUBSTANTIATED')
                  AND EXISTS (SELECT 1 FROM dbo.Feedbacks f WHERE f.feedback_id=dbo.Feedback_Cases.feedback_id
                    AND f.admin_reply IS NOT NULL AND LEN(LTRIM(RTRIM(f.admin_reply)))>0)
                  AND (remedy_type IS NULL OR remedy_completed=1)
                  AND (remedy_type NOT IN ('REFUND','PRICE_REDUCTION','COMPENSATION') OR EXISTS (
                    SELECT 1 FROM dbo.Booking_Financial_Entries e WHERE e.case_id=dbo.Feedback_Cases.case_id
                      AND e.entry_type=CASE remedy_type WHEN 'REFUND' THEN 'REFUND' WHEN 'PRICE_REDUCTION' THEN 'PRICE_REDUCTION' ELSE 'COMPENSATION' END
                      AND e.status IN ('PAID','SIMULATED')))
                  AND staff_action_status<>'PENDING'
                  AND NOT EXISTS (SELECT 1 FROM dbo.Booking_Financial_Entries e WHERE e.case_id=dbo.Feedback_Cases.case_id
                    AND e.status IN ('PROPOSED','PENDING_APPROVAL','APPROVED'))
                """, caseId);
        if (updated != 1) throw new IllegalStateException("Chỉ giải quyết sau khi xác minh xong và mọi khoản tiền đã được xử lý hoặc hủy.");
        jdbc.update("UPDATE dbo.Feedbacks SET status='RESOLVED' WHERE feedback_id=(SELECT feedback_id FROM dbo.Feedback_Cases WHERE case_id=?) AND status='RESPONDED'", caseId);
    }

    @Override
    public java.util.List<com.housekeeping.management.dto.response.PortalViews.StaffActionNoticeCard> approvedActionsForStaff(Long staffId) {
        return cases.approvedActionsForStaff(staffId);
    }

    @Override
    @Transactional
    public void acknowledgeStaffAction(Long actionId, Long staffId) {
        if (cases.acknowledgeForStaff(actionId, staffId) != 1) {
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
}
