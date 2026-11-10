package com.housekeeping.management.service.impl;

import com.housekeeping.management.service.PortalQueryService;
import com.housekeeping.management.service.FeedbackService;

import static com.housekeeping.management.dto.response.PortalViews.*;
import java.time.OffsetDateTime;
import java.time.LocalDateTime;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.time.ZoneId;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import com.housekeeping.management.domain.FeedbackTopic;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import com.housekeeping.management.web.PortalUser;

@Service
@Transactional(readOnly = true)
public class PortalQueryServiceImpl implements PortalQueryService {
    private final JdbcTemplate jdbc;
    private final FeedbackService feedback;
    public PortalQueryServiceImpl(JdbcTemplate jdbc, FeedbackService feedback) { this.jdbc = jdbc; this.feedback = feedback; }

    public List<FeedbackCard> feedback() { return feedback.findAll(); }

    public List<ServiceCard> services() {
        return services(null);
    }

    public List<ServiceCard> services(PortalUser user) {
        boolean includeInactive = user != null && user.isAdmin();
        return jdbc.query("SELECT * FROM dbo.Services WHERE (?=1 OR status='ACTIVE') ORDER BY service_name", (rs, row) -> new ServiceCard(
                rs.getLong("service_id"), rs.getString("service_name"), rs.getString("description"),
                rs.getInt("duration_minutes"), rs.getBigDecimal("price"), rs.getString("image_url"),
                "ACTIVE".equals(rs.getString("status"))),
                includeInactive ? 1 : 0);
    }

    public List<StaffCard> staff(PortalUser user) {
        Long customerId = user != null && user.getRole().equals("CUSTOMER") ? user.getUserId() : -1L;
        boolean includeInactive = user != null && user.isAdmin();
        return jdbc.query("""
                SELECT s.staff_id, u.full_name, u.phone,
                       CASE WHEN f.staff_id IS NULL THEN 0 ELSE 1 END AS favorite,
                       s.staff_type, u.status, p.avatar_url, p.bio
                FROM dbo.Staff s JOIN dbo.Users u ON u.user_id=s.user_id
                LEFT JOIN dbo.Staff_Follows f ON f.staff_id=s.staff_id AND f.customer_id=?
                LEFT JOIN dbo.Staff_Profiles p ON p.staff_id=s.staff_id
                WHERE (? = 1 OR u.status='ACTIVE')
                ORDER BY u.full_name
                """, (rs, row) -> new StaffCard(rs.getLong(1), rs.getString(2), rs.getString(3),
                        rs.getBoolean(4), rs.getString(5), "ACTIVE".equals(rs.getString(6)), rs.getString(7), rs.getString(8)),
                customerId, includeInactive ? 1 : 0);
    }

    public List<ScheduleCard> schedules(PortalUser user) {
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh"));
        LocalTime now = LocalTime.now(ZoneId.of("Asia/Ho_Chi_Minh"));
        String condition = switch (user.getRole()) {
            case "STAFF" -> " WHERE a.staff_id=?";
            case "CUSTOMER" -> " WHERE u.status='ACTIVE' AND a.is_active=1 AND a.work_date>=? AND a.work_date<=? AND (a.work_date>? OR a.start_time>?)";
            case "ADMIN" -> "";
            case "MANAGER" -> "";
            default -> throw new AccessDeniedException("Vai trò không hợp lệ.");
        };
        Object[] params = switch (user.getRole()) {
            case "STAFF" -> new Object[]{user.getAccountId()};
            case "CUSTOMER" -> new Object[]{java.sql.Date.valueOf(today), java.sql.Date.valueOf(today.plusDays(30)),
                    java.sql.Date.valueOf(today), java.sql.Time.valueOf(now.withSecond(0).withNano(0))};
            default -> new Object[]{};
        };
        return jdbc.query("""
                SELECT a.availability_id, a.staff_id, u.full_name, a.work_date, a.start_time, a.end_time, a.is_active
                FROM dbo.Staff_Availability a
                JOIN dbo.Staff s ON s.staff_id=a.staff_id
                JOIN dbo.Users u ON u.user_id=s.user_id
                """ + condition + " ORDER BY a.work_date, a.start_time", (rs, row) -> new ScheduleCard(
                    rs.getLong(1), rs.getLong(2), rs.getString(3),
                    LocalDateTime.of(rs.getDate("work_date").toLocalDate(), rs.getTime("start_time").toLocalTime())
                            .atOffset(ZoneOffset.ofHours(7)),
                    LocalDateTime.of(rs.getDate("work_date").toLocalDate(), rs.getTime("end_time").toLocalTime())
                            .atOffset(ZoneOffset.ofHours(7)), rs.getBoolean("is_active")), params);
    }

    public boolean isPartTime(Long staffId) {
        String type = jdbc.queryForObject("SELECT staff_type FROM dbo.Staff WHERE staff_id=?", String.class, staffId);
        return "PART_TIME".equals(type);
    }

    public List<BusyInterval> busyIntervals() {
        return jdbc.query("""
                SELECT b.staff_id, b.start_time, b.end_time FROM dbo.Bookings b
                WHERE b.status IN ('CONFIRMED', 'AWAITING_PAYMENT') AND b.end_time>SYSDATETIME()
                ORDER BY b.start_time
                """, (rs, row) -> new BusyInterval(rs.getLong(1),
                    rs.getObject(2, LocalDateTime.class).atOffset(ZoneOffset.ofHours(7)),
                    rs.getObject(3, LocalDateTime.class).atOffset(ZoneOffset.ofHours(7))));
    }

    public List<BookingCard> bookings(PortalUser user) {
        String condition = switch (user.getRole()) {
            case "CUSTOMER" -> " WHERE b.customer_id=?";
            case "STAFF" -> " WHERE b.staff_id=?";
            case "ADMIN" -> "";
            case "MANAGER" -> "";
            default -> throw new AccessDeniedException("Vai trò không hợp lệ.");
        };
        return bookingQuery(condition, user.isAdmin() ? new Object[]{} : new Object[]{user.getAccountId()},
                !user.getRole().equals("STAFF"));
    }

    public BookingCard booking(Long id, PortalUser user) {
        BookingCard card = bookingQuery(" WHERE b.booking_id=?", new Object[]{id}, !"STAFF".equals(user.getRole())).stream().findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        authorize(card, user);
        return card;
    }

    public void authorize(BookingCard card, PortalUser user) {
        boolean allowed = switch (user.getRole()) {
            case "ADMIN", "MANAGER" -> true;
            case "CUSTOMER" -> user.getAccountId().equals(card.customerId());
            case "STAFF" -> user.getAccountId().equals(card.staffId());
            default -> false;
        };
        if (!allowed) { throw new AccessDeniedException("Bạn không có quyền truy cập lịch hẹn này."); }
    }

    public List<DetailCard> details(Long bookingId, PortalUser user) {
        booking(bookingId, user);
        return jdbc.query("""
                SELECT d.booking_detail_id, d.service_name_snapshot,
                       ROW_NUMBER() OVER (ORDER BY d.booking_detail_id) AS display_order,
                       d.duration_minutes, d.unit_price
                FROM dbo.Booking_Details d
                WHERE d.booking_id=? ORDER BY d.booking_detail_id
                """, (rs, row) -> new DetailCard(rs.getLong(1), rs.getString(2), rs.getInt(3), rs.getInt(4), rs.getBigDecimal(5), List.of()), bookingId);
    }

    public List<EvidenceCard> bookingImages(Long bookingId, PortalUser user) {
        booking(bookingId, user);
        return jdbc.query("""
                SELECT image_id, uploaded_at FROM dbo.Booking_Images
                WHERE booking_id=? ORDER BY uploaded_at, image_id
                """, (rs, row) -> new EvidenceCard(rs.getLong(1),
                    time(rs.getObject(2, LocalDateTime.class).atOffset(ZoneOffset.ofHours(7)))), bookingId);
    }

    public Long bookingIdForDetail(Long detailId, PortalUser user) {
        List<Long> ids = jdbc.query("SELECT booking_id FROM dbo.Booking_Details WHERE booking_detail_id=?", (rs, row) -> rs.getLong(1), detailId);
        if (ids.isEmpty()) { throw new ResponseStatusException(HttpStatus.NOT_FOUND); }
        booking(ids.getFirst(), user);
        return ids.getFirst();
    }

    public String evidencePath(Long evidenceId, PortalUser user) {
        List<Long> ids = jdbc.query("SELECT booking_id FROM dbo.Booking_Images WHERE image_id=?", (rs, row) -> rs.getLong(1), evidenceId);
        if (ids.isEmpty()) { throw new ResponseStatusException(HttpStatus.NOT_FOUND); }
        booking(ids.getFirst(), user);
        return jdbc.queryForObject("SELECT image_url FROM dbo.Booking_Images WHERE image_id=?", String.class, evidenceId);
    }

    private List<BookingCard> bookingQuery(String condition, Object[] params, boolean showFeedback) {
        String feedbackColumns = showFeedback
                ? "f.content AS feedback_content, f.admin_reply, f.status AS feedback_status, f.rating AS feedback_rating, f.topics AS feedback_topics, b.customer_delay_reported_at AS customer_delay_reported_at,"
                : "CAST(NULL AS NVARCHAR(MAX)) AS feedback_content, CAST(NULL AS NVARCHAR(MAX)) AS admin_reply, CAST(NULL AS VARCHAR(20)) AS feedback_status, CAST(NULL AS TINYINT) AS feedback_rating, CAST(NULL AS NVARCHAR(300)) AS feedback_topics, CAST(NULL AS DATETIME2(0)) AS customer_delay_reported_at,";
        return jdbc.query("""
                SELECT b.booking_id, b.customer_id, b.staff_id, customer.full_name AS customer_name,
                       staff_user.full_name AS staff_name, b.start_time, b.end_time, b.status,
                       b.total_amount,
                       payment.status AS payment_status, payment.payment_method, %s
                       b.address_snapshot, b.address_note_snapshot, b.area_note, b.cancellation_reason,
                       b.customer_arrived_at, customer.phone AS customer_phone, b.staff_arrived_at,
                       b.incident_reported_at, b.incident_note, b.incident_expected_arrival_at,
                       b.incident_customer_response, b.incident_customer_responded_at, b.work_started_at,
                       b.delay_minutes, b.delay_discount_percent, b.delay_discount_amount
                FROM dbo.Bookings b
                JOIN dbo.Users customer ON customer.user_id=b.customer_id
                JOIN dbo.Staff selected_staff ON selected_staff.staff_id=b.staff_id
                JOIN dbo.Users staff_user ON staff_user.user_id=selected_staff.user_id
                LEFT JOIN dbo.Feedbacks f ON f.booking_id=b.booking_id
                OUTER APPLY (SELECT TOP (1) p.payment_id, p.status, p.payment_method
                             FROM dbo.Payments p WHERE p.booking_id=b.booking_id
                             ORDER BY p.created_at DESC, p.payment_id DESC) payment
                """.formatted(feedbackColumns) + condition + " ORDER BY b.start_time DESC", (rs, row) -> new BookingCard(
                    rs.getLong("booking_id"), rs.getLong("customer_id"), rs.getLong("staff_id"), rs.getString("customer_name"), rs.getString("staff_name"),
                    rs.getObject("start_time", LocalDateTime.class).atOffset(ZoneOffset.ofHours(7)),
                    rs.getObject("end_time", LocalDateTime.class).atOffset(ZoneOffset.ofHours(7)), rs.getString("status"),
                    rs.getBigDecimal("total_amount"), rs.getString("payment_status"), rs.getString("payment_method"),
                    rs.getString("feedback_content"),
                    rs.getString("admin_reply"), rs.getString("feedback_status"),
                    rs.getString("address_snapshot"), rs.getString("address_note_snapshot"),
                    rs.getString("area_note"), rs.getString("cancellation_reason"),
                    rs.getObject("customer_arrived_at", LocalDateTime.class) == null ? null : rs.getObject("customer_arrived_at", LocalDateTime.class).atOffset(ZoneOffset.ofHours(7)),
                    rs.getString("customer_phone"),
                    rs.getObject("staff_arrived_at", LocalDateTime.class) == null ? null : rs.getObject("staff_arrived_at", LocalDateTime.class).atOffset(ZoneOffset.ofHours(7)),
                    rs.getObject("incident_reported_at", LocalDateTime.class) == null ? null : rs.getObject("incident_reported_at", LocalDateTime.class).atOffset(ZoneOffset.ofHours(7)),
                    rs.getString("incident_note"),
                    rs.getObject("incident_expected_arrival_at", LocalDateTime.class) == null ? null : rs.getObject("incident_expected_arrival_at", LocalDateTime.class).atOffset(ZoneOffset.ofHours(7)),
                    rs.getString("incident_customer_response"),
                    rs.getObject("incident_customer_responded_at", LocalDateTime.class) == null ? null : rs.getObject("incident_customer_responded_at", LocalDateTime.class).atOffset(ZoneOffset.ofHours(7)),
                    rs.getObject("work_started_at", LocalDateTime.class) == null ? null : rs.getObject("work_started_at", LocalDateTime.class).atOffset(ZoneOffset.ofHours(7)),
                    nullableInteger(rs.getObject("delay_minutes")), rs.getBigDecimal("delay_discount_percent"),
                    rs.getBigDecimal("delay_discount_amount"), nullableInteger(rs.getObject("feedback_rating")),
                    feedbackTopics(rs.getString("feedback_topics")), feedbackTopicCodes(rs.getString("feedback_topics")),
                    rs.getObject("customer_delay_reported_at", LocalDateTime.class) == null ? null
                            : rs.getObject("customer_delay_reported_at", LocalDateTime.class).atOffset(ZoneOffset.ofHours(7))), params);
    }

    private static Integer nullableInteger(Object value) {
        return value == null ? null : ((Number) value).intValue();
    }

    private static List<String> feedbackTopics(String value) {
        if (value == null || value.isBlank()) return List.of();
        return FeedbackTopic.labels(List.of(value.split(",")));
    }
    private static List<String> feedbackTopicCodes(String value) {
        if (value == null || value.isBlank()) return List.of();
        return List.of(value.split(","));
    }
}
