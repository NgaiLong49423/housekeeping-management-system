package com.housekeeping.management.repository;

import java.util.List;
import com.housekeeping.management.domain.FeedbackTopic;
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

    public void create(Long bookingId, Long customerId, Integer rating, List<String> topics, String content) {
        jdbc.update("INSERT INTO dbo.Feedbacks (booking_id, customer_id, rating, topics, content) VALUES (?, ?, ?, ?, ?)",
                bookingId, customerId, rating, String.join(",", topics), content);
    }

    public int update(Long bookingId, Long customerId, Integer rating, List<String> topics, String content) {
        return jdbc.update("""
                UPDATE dbo.Feedbacks SET rating=?, topics=?, content=?
                WHERE booking_id=? AND customer_id=? AND status='OPEN' AND admin_reply IS NULL
                """, rating, String.join(",", topics), content, bookingId, customerId);
    }

    public int reply(Long feedbackId, String reply) {
        return jdbc.update("""
                UPDATE dbo.Feedbacks SET admin_reply=?, status='RESPONDED'
                WHERE feedback_id=? AND status IN ('OPEN','RESPONDED')
                """, reply, feedbackId);
    }

    public int resolve(Long feedbackId) {
        return jdbc.update("""
                UPDATE dbo.Feedbacks SET status='RESOLVED' WHERE feedback_id=? AND status='RESPONDED'
                  AND NOT EXISTS (SELECT 1 FROM dbo.Feedback_Cases c WHERE c.feedback_id=dbo.Feedbacks.feedback_id AND c.case_status<>'RESOLVED')
                """, feedbackId);
    }

    public List<com.housekeeping.management.dto.response.PortalViews.FeedbackCard> findAll() {
        return jdbc.query("""
                SELECT f.feedback_id, f.booking_id, u.full_name, f.rating, f.topics, f.content, f.admin_reply, f.status,
                       b.status AS booking_status, b.start_time, b.total_amount, staff_user.full_name AS staff_name
                FROM dbo.Feedbacks f
                JOIN dbo.Users u ON u.user_id=f.customer_id
                JOIN dbo.Bookings b ON b.booking_id=f.booking_id
                JOIN dbo.Staff s ON s.staff_id=b.staff_id
                JOIN dbo.Users staff_user ON staff_user.user_id=s.user_id
                ORDER BY CASE f.status WHEN 'OPEN' THEN 0 WHEN 'RESPONDED' THEN 1 ELSE 2 END, f.feedback_id DESC
                """, (rs, row) -> {
                    String value = rs.getString("topics");
                    List<String> codes = value == null || value.isBlank() ? List.of() : List.of(value.split(","));
                    return new com.housekeeping.management.dto.response.PortalViews.FeedbackCard(
                        rs.getLong("feedback_id"), rs.getLong("booking_id"), rs.getString("full_name"),
                        nullableInteger(rs.getObject("rating")), FeedbackTopic.labels(codes), rs.getString("content"),
                        rs.getString("admin_reply"), rs.getString("status"), rs.getString("booking_status"),
                        rs.getObject("start_time", java.time.LocalDateTime.class), rs.getBigDecimal("total_amount"), rs.getString("staff_name"));
                });
    }

    private static Integer nullableInteger(Object value) {
        return value == null ? null : ((Number) value).intValue();
    }
}
