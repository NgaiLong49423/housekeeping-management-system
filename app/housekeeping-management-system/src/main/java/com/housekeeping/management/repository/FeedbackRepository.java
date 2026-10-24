package com.housekeeping.management.repository;

import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class FeedbackRepository {
    private final JdbcTemplate jdbc;

    public FeedbackRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public boolean existsForBooking(Long bookingId) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM dbo.Feedbacks WHERE booking_id=?", Integer.class, bookingId);
        return count != null && count > 0;
    }

    public void create(Long bookingId, Long customerId, String content) {
        jdbc.update("INSERT INTO dbo.Feedbacks (booking_id, customer_id, content) VALUES (?, ?, ?)",
                bookingId, customerId, content);
    }

    public int reply(Long feedbackId, String reply) {
        return jdbc.update("UPDATE dbo.Feedbacks SET admin_reply=? WHERE feedback_id=?", reply, feedbackId);
    }

    public int resolve(Long feedbackId) {
        return jdbc.update("UPDATE dbo.Feedbacks SET status='RESOLVED' WHERE feedback_id=? AND status='OPEN'", feedbackId);
    }

    public List<com.housekeeping.management.dto.response.PortalViews.FeedbackCard> findAll() {
        return jdbc.query("""
                SELECT f.feedback_id, f.booking_id, u.full_name, f.content, f.admin_reply, f.status
                FROM dbo.Feedbacks f
                JOIN dbo.Users u ON u.user_id=f.customer_id
                ORDER BY CASE WHEN f.status='OPEN' THEN 0 ELSE 1 END, f.feedback_id DESC
                """, (rs, row) -> new com.housekeeping.management.dto.response.PortalViews.FeedbackCard(
                rs.getLong("feedback_id"), rs.getLong("booking_id"), rs.getString("full_name"),
                rs.getString("content"), rs.getString("admin_reply"), rs.getString("status")));
    }
}
