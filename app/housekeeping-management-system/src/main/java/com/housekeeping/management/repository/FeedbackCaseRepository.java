package com.housekeeping.management.repository;


import com.housekeeping.management.dto.response.PortalViews.IncidentCard;
import com.housekeeping.management.dto.response.PortalViews.FeedbackCaseMessageCard;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class FeedbackCaseRepository {
    private final JdbcTemplate jdbc;
    public FeedbackCaseRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public Long create(Long feedbackId, java.util.List<String> types, String description) {
        return jdbc.queryForObject("""
                INSERT INTO dbo.Feedback_Cases (feedback_id, incident_types, customer_description)
                OUTPUT INSERTED.case_id VALUES (?, ?, ?)
                """, Long.class, feedbackId, String.join(",",types), description);
    }

    public Long createInitialMessage(Long caseId, Long customerId, String text) {
        return jdbc.queryForObject("""
                INSERT INTO dbo.Feedback_Case_Messages(case_id,sender_user_id,sender_role,message_text)
                OUTPUT INSERTED.message_id VALUES (?,?,'CUSTOMER',?)
                """, Long.class, caseId, customerId, text);
    }

    public Long createCustomerMessage(Long caseId, Long bookingId, Long customerId, String text) {
        return jdbc.query("""
                INSERT INTO dbo.Feedback_Case_Messages(case_id,sender_user_id,sender_role,message_text)
                OUTPUT INSERTED.message_id
                SELECT c.case_id,f.customer_id,'CUSTOMER',?
                FROM dbo.Feedback_Cases c JOIN dbo.Feedbacks f ON f.feedback_id=c.feedback_id
                JOIN dbo.Bookings b ON b.booking_id=f.booking_id
                WHERE c.case_id=? AND b.booking_id=? AND f.customer_id=? AND b.customer_id=? AND c.case_status<>'RESOLVED'
                """, rs -> rs.next() ? rs.getLong(1) : null, text, caseId, bookingId, customerId, customerId);
    }

    public Long createAdminMessage(Long caseId, Long adminId, String text) {
        return jdbc.query("""
                INSERT INTO dbo.Feedback_Case_Messages(case_id,sender_user_id,sender_role,message_text)
                OUTPUT INSERTED.message_id
                SELECT case_id,?,'ADMIN',? FROM dbo.Feedback_Cases
                WHERE case_id=? AND case_status<>'RESOLVED'
                """, rs -> rs.next() ? rs.getLong(1) : null, adminId, text, caseId);
    }

    public List<FeedbackCaseMessageCard> messages(Long caseId) {
        return jdbc.query("""
                SELECT m.message_id,m.case_id,m.sender_user_id,m.sender_role,
                       COALESCE(u.full_name,CASE m.sender_role WHEN 'ADMIN' THEN N'Gọn Nhà' ELSE N'Khách hàng' END) AS sender_name,
                       m.message_text,m.sent_at,m.legacy_imported,m.legacy_timestamp_unknown
                FROM dbo.Feedback_Case_Messages m LEFT JOIN dbo.Users u ON u.user_id=m.sender_user_id
                WHERE m.case_id=? ORDER BY m.sent_at,m.message_id
                """, (rs,row) -> new FeedbackCaseMessageCard(rs.getLong("message_id"),rs.getLong("case_id"),
                rs.getObject("sender_user_id",Long.class),rs.getString("sender_role"),rs.getString("sender_name"),
                rs.getString("message_text"),rs.getObject("sent_at",java.time.LocalDateTime.class),
                rs.getBoolean("legacy_imported"),rs.getBoolean("legacy_timestamp_unknown"),List.of()),caseId);
    }

    public List<Long> messageEvidenceIds(Long messageId) {
        return jdbc.query("SELECT evidence_id FROM dbo.Feedback_Case_Evidence WHERE message_id=? ORDER BY uploaded_at,evidence_id",
                (rs,row)->rs.getLong(1),messageId);
    }

    public int discardUnsentMessage(Long caseId, Long messageId, Long actorId, String senderRole) {
        return jdbc.update("""
                DELETE FROM dbo.Feedback_Case_Messages
                WHERE message_id=? AND case_id=? AND sender_user_id=? AND sender_role=? AND legacy_imported=0
                  AND NOT EXISTS (SELECT 1 FROM dbo.Feedback_Case_Evidence e WHERE e.message_id=dbo.Feedback_Case_Messages.message_id)
                """,messageId,caseId,actorId,senderRole);
    }

    public String customerOutcome(Long caseId) {
        return jdbc.query("SELECT customer_outcome FROM dbo.Feedback_Cases WHERE case_id=?",
                rs -> rs.next() ? rs.getString(1) : null, caseId);
    }

    public String adminMessageText(Long caseId, Long messageId, Long adminId) {
        return jdbc.query("SELECT message_text FROM dbo.Feedback_Case_Messages WHERE case_id=? AND message_id=? AND sender_user_id=? AND sender_role='ADMIN'",
                rs -> rs.next()?rs.getString(1):null,caseId,messageId,adminId);
    }

    public int markFeedbackResponded(Long caseId, String reply) {
        return jdbc.update("""
                UPDATE dbo.Feedbacks SET status='RESPONDED',admin_reply=COALESCE(NULLIF(LTRIM(RTRIM(admin_reply)),''),?)
                WHERE feedback_id=(SELECT feedback_id FROM dbo.Feedback_Cases WHERE case_id=?)
                  AND status IN ('OPEN','RESPONDED')
                """,reply,caseId);
    }

    public Optional<IncidentCard> findForFeedback(Long feedbackId) {
        return jdbc.query("""
                SELECT c.case_id, c.feedback_id, c.incident_types, c.customer_description, c.case_status,
                       c.customer_outcome, c.remedy_type, c.remedy_completed, c.internal_finding, COALESCE(a.action_type,c.staff_action) AS staff_action,
                       COALESCE(a.status,c.staff_action_status) AS staff_action_status,
                       COALESCE(a.proposed_by,c.staff_action_by) AS staff_action_by,
                       COALESCE(a.decided_by,c.staff_action_approved_by) AS staff_action_approved_by,
                       a.action_id, c.created_at, c.resolved_at,
                       f.booking_id, b.customer_id, b.staff_id
                FROM dbo.Feedback_Cases c JOIN dbo.Feedbacks f ON f.feedback_id=c.feedback_id
                JOIN dbo.Bookings b ON b.booking_id=f.booking_id
                OUTER APPLY (SELECT TOP (1) action_id,action_type,status,proposed_by,decided_by FROM dbo.Staff_Incident_Actions WHERE case_id=c.case_id ORDER BY action_id DESC) a
                WHERE c.feedback_id=?
                """, (rs, row) -> new IncidentCard(rs.getLong("case_id"), rs.getLong("feedback_id"),
                rs.getLong("booking_id"), rs.getLong("customer_id"), rs.getLong("staff_id"),
                java.util.List.of(rs.getString("incident_types").split(",")), rs.getString("customer_description"), rs.getString("case_status"),
                rs.getString("customer_outcome"), rs.getString("remedy_type"), rs.getString("internal_finding"),
                rs.getBoolean("remedy_completed"),
                rs.getString("staff_action"), rs.getString("staff_action_status"),
                rs.getObject("staff_action_by", Long.class), rs.getObject("staff_action_approved_by", Long.class),
                rs.getObject("action_id", Long.class),
                rs.getObject("created_at", java.time.LocalDateTime.class), rs.getObject("resolved_at", java.time.LocalDateTime.class)), feedbackId)
                .stream().findFirst();
    }

    public boolean customerOwnsCase(Long caseId, Long customerId) {
        Integer count=jdbc.queryForObject("""
                SELECT COUNT(*) FROM dbo.Feedback_Cases c JOIN dbo.Feedbacks f ON f.feedback_id=c.feedback_id
                JOIN dbo.Bookings b ON b.booking_id=f.booking_id
                WHERE c.case_id=? AND f.customer_id=? AND b.customer_id=?
                """,Integer.class,caseId,customerId,customerId);
        return count!=null && count==1;
    }

    public boolean belongsToBooking(Long caseId, Long bookingId) {
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM dbo.Feedback_Cases c
                JOIN dbo.Feedbacks f ON f.feedback_id=c.feedback_id
                WHERE c.case_id=? AND f.booking_id=?
                """, Integer.class, caseId, bookingId);
        return count != null && count == 1;
    }

    public boolean authorizesStaffRecovery(Long caseId, Long bookingId) {
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM dbo.Feedback_Cases c
                JOIN dbo.Feedbacks f ON f.feedback_id=c.feedback_id
                WHERE c.case_id=? AND f.booking_id=? AND c.case_status='VERIFIED'
                  AND c.staff_action='PROPOSE_RECOVERY' AND c.staff_action_status='APPROVED'
                """, Integer.class, caseId, bookingId);
        return count != null && count == 1;
    }

    public int updateReview(Long caseId, String status, String remedy, String outcome, String finding, boolean remedyCompleted, Long adminId) {
        return jdbc.update("""
                UPDATE dbo.Feedback_Cases SET case_status=?, remedy_type=?, customer_outcome=?, internal_finding=?, remedy_completed=?, reviewed_by=?, reviewed_at=SYSDATETIME()
                WHERE case_id=? AND case_status<>'RESOLVED'
                """, status, blankToNull(remedy), blankToNull(outcome), blankToNull(finding), remedyCompleted, adminId, caseId);
    }

    public int proposeStaffAction(Long caseId, String action, String staffNotice, Long adminId) {
        int inserted=jdbc.update("""
                INSERT INTO dbo.Staff_Incident_Actions(case_id,staff_id,action_type,reason,staff_notice,proposed_by)
                SELECT c.case_id,b.staff_id,?,COALESCE(NULLIF(LTRIM(RTRIM(c.internal_finding)),''),N'Đề xuất theo kết quả xác minh'),?,?
                FROM dbo.Feedback_Cases c JOIN dbo.Feedbacks f ON f.feedback_id=c.feedback_id
                JOIN dbo.Bookings b ON b.booking_id=f.booking_id
                WHERE c.case_id=? AND c.case_status='VERIFIED'
                  AND NOT EXISTS (SELECT 1 FROM dbo.Staff_Incident_Actions a WHERE a.case_id=c.case_id AND a.status='PENDING')
                """,action,staffNotice,adminId,caseId);
        if(inserted==1) jdbc.update("UPDATE dbo.Feedback_Cases SET staff_action=?,staff_action_status='PENDING',staff_action_by=?,staff_action_approved_by=NULL WHERE case_id=?",action,adminId,caseId);
        return inserted;
    }

    public int decideStaffAction(Long caseId, boolean approve, Long managerId) {
        int updated=jdbc.update("""
                UPDATE dbo.Staff_Incident_Actions SET status=?,decided_by=?,decided_at=SYSDATETIME()
                WHERE action_id=(SELECT TOP (1) action_id FROM dbo.Staff_Incident_Actions WHERE case_id=? AND status='PENDING' ORDER BY action_id DESC)
                  AND proposed_by<>?
                """,approve?"APPROVED":"REJECTED",managerId,caseId,managerId);
        if(updated==1) jdbc.update("UPDATE dbo.Feedback_Cases SET staff_action_status=?,staff_action_approved_by=? WHERE case_id=?",approve?"APPROVED":"REJECTED",managerId,caseId);
        return updated;
    }

    public List<IncidentCard> findAll() {
        return jdbc.query("SELECT feedback_id FROM dbo.Feedback_Cases ORDER BY created_at DESC, case_id DESC",
                (rs, row) -> findForFeedback(rs.getLong(1)).orElseThrow());
    }

    public List<com.housekeeping.management.dto.response.PortalViews.StaffIncidentActionCard> actions(Long caseId) {
        return jdbc.query("""
                SELECT action_id,action_type,status,reason,staff_notice,proposed_by,decided_by,created_at,decided_at,acknowledged_at
                FROM dbo.Staff_Incident_Actions WHERE case_id=? ORDER BY created_at,action_id
                """,(rs,row)->new com.housekeeping.management.dto.response.PortalViews.StaffIncidentActionCard(
                rs.getLong("action_id"),rs.getString("action_type"),rs.getString("status"),rs.getString("reason"),rs.getString("staff_notice"),
                rs.getLong("proposed_by"),rs.getObject("decided_by",Long.class),rs.getObject("created_at",java.time.LocalDateTime.class),rs.getObject("decided_at",java.time.LocalDateTime.class),rs.getObject("acknowledged_at",java.time.LocalDateTime.class)),caseId);
    }

    public List<com.housekeeping.management.dto.response.PortalViews.StaffActionNoticeCard> approvedActionsForStaff(Long staffId) {
        return jdbc.query("""
                SELECT action_id,action_type,staff_notice,created_at,decided_at,acknowledged_at
                FROM dbo.Staff_Incident_Actions WHERE staff_id=? AND status='APPROVED' AND action_type<>'NONE'
                ORDER BY decided_at DESC,action_id DESC
                """,(rs,row)->new com.housekeeping.management.dto.response.PortalViews.StaffActionNoticeCard(
                rs.getLong("action_id"),rs.getString("action_type"),rs.getString("staff_notice"),rs.getObject("created_at",java.time.LocalDateTime.class),
                rs.getObject("decided_at",java.time.LocalDateTime.class),rs.getObject("acknowledged_at",java.time.LocalDateTime.class)),staffId);
    }

    public int acknowledgeForStaff(Long actionId, Long staffId) {
        return jdbc.update("UPDATE dbo.Staff_Incident_Actions SET acknowledged_at=SYSDATETIME() WHERE action_id=? AND staff_id=? AND status='APPROVED' AND acknowledged_at IS NULL",actionId,staffId);
    }

    private static String blankToNull(String value) { return value == null || value.isBlank() ? null : value.trim(); }


}
