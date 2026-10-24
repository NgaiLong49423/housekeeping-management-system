package com.housekeeping.management.service;

import static com.housekeeping.management.dto.response.PortalViews.*;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import com.housekeeping.management.web.PortalUser;

@Service
@Transactional(readOnly = true)
public class PortalQueryService {
    private final JdbcTemplate jdbc;
    public PortalQueryService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public List<ServiceCard> services() {
        return services(null);
    }

    public List<ServiceCard> services(PortalUser user) {
        boolean includeInactive = user != null && user.getRole().equals("ADMIN");
        return jdbc.query("SELECT * FROM dbo.Services WHERE (?=1 OR status='ACTIVE') ORDER BY service_name", (rs, row) -> new ServiceCard(
                rs.getLong("service_id"), rs.getString("service_name"), rs.getString("description"),
                rs.getInt("duration_minutes"), rs.getBigDecimal("price"), "ACTIVE".equals(rs.getString("status"))),
                includeInactive ? 1 : 0);
    }

    public List<StaffCard> staff(PortalUser user) {
        Long customerId = user != null && user.getRole().equals("CUSTOMER") ? user.getUserId() : -1L;
        boolean includeInactive = user != null && user.getRole().equals("ADMIN");
        return jdbc.query("""
                SELECT s.staff_id, u.full_name, u.phone,
                       CASE WHEN f.staff_id IS NULL THEN 0 ELSE 1 END AS favorite,
                       s.staff_type, u.status
                FROM dbo.Staff s JOIN dbo.Users u ON u.user_id=s.user_id
                LEFT JOIN dbo.Staff_Follows f ON f.staff_id=s.staff_id AND f.customer_id=?
                WHERE (? = 1 OR u.status='ACTIVE')
                ORDER BY u.full_name
                """, (rs, row) -> new StaffCard(rs.getLong(1), rs.getString(2), rs.getString(3),
                        rs.getBoolean(4), rs.getString(5), "ACTIVE".equals(rs.getString(6))), customerId, includeInactive ? 1 : 0);
    }

    public List<ScheduleCard> schedules(PortalUser user) {
        String condition = user.getRole().equals("STAFF") ? " WHERE w.staff_id=?" : "";
        Object[] params = user.getRole().equals("STAFF") ? new Object[]{user.getAccountId()} : new Object[]{};
        return jdbc.query("""
                SELECT w.work_schedule_id, w.staff_id, s.display_name, w.available_start, w.available_end
                FROM Work_Schedule w JOIN Staff s ON s.staff_id=w.staff_id
                """ + condition + " ORDER BY w.available_start", (rs, row) -> new ScheduleCard(
                    rs.getLong(1), rs.getLong(2), rs.getString(3), rs.getObject(4, OffsetDateTime.class),
                    rs.getObject(5, OffsetDateTime.class)), params);
    }

    public record BusyInterval(Long staffId, OffsetDateTime start, OffsetDateTime end) {
        public String getLabel() { return time(start) + " → " + time(end); }
    }

    public List<BusyInterval> busyIntervals() {
        return jdbc.query("""
                SELECT DISTINCT w.staff_id, b.start_at, b.end_at FROM Booking b
                JOIN Booking_Detail d ON d.booking_id=b.booking_id
                JOIN Work_Schedule w ON w.work_schedule_id=d.work_schedule_id
                WHERE b.booking_status='CONFIRMED' AND b.end_at>SYSDATETIMEOFFSET()
                ORDER BY b.start_at
                """, (rs, row) -> new BusyInterval(rs.getLong(1), rs.getObject(2, OffsetDateTime.class), rs.getObject(3, OffsetDateTime.class)));
    }

    public List<BookingCard> bookings(PortalUser user) {
        String condition = switch (user.getRole()) {
            case "CUSTOMER" -> " WHERE b.customer_id=?";
            case "STAFF" -> " WHERE EXISTS (SELECT 1 FROM Booking_Detail d JOIN Work_Schedule w ON w.work_schedule_id=d.work_schedule_id WHERE d.booking_id=b.booking_id AND w.staff_id=?)";
            case "ADMIN" -> "";
            default -> throw new AccessDeniedException("Vai trò không hợp lệ.");
        };
        return bookingQuery(condition, user.getRole().equals("ADMIN") ? new Object[]{} : new Object[]{user.getAccountId()});
    }

    public BookingCard booking(Long id, PortalUser user) {
        BookingCard card = bookingQuery(" WHERE b.booking_id=?", new Object[]{id}).stream().findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        authorize(card, user);
        return card;
    }

    public void authorize(BookingCard card, PortalUser user) {
        boolean allowed = switch (user.getRole()) {
            case "ADMIN" -> true;
            case "CUSTOMER" -> user.getAccountId().equals(card.customerId());
            case "STAFF" -> user.getAccountId().equals(card.staffId());
            default -> false;
        };
        if (!allowed) { throw new AccessDeniedException("Bạn không có quyền truy cập lịch hẹn này."); }
    }

    public List<DetailCard> details(Long bookingId, PortalUser user) {
        booking(bookingId, user);
        return jdbc.query("""
                SELECT d.booking_detail_id, s.service_name, d.sequence_no, d.duration_minutes_snapshot, d.unit_price_snapshot
                FROM Booking_Detail d JOIN Services s ON s.service_id=d.service_id
                WHERE d.booking_id=? ORDER BY d.sequence_no
                """, (rs, row) -> new DetailCard(rs.getLong(1), rs.getString(2), rs.getInt(3), rs.getInt(4), rs.getBigDecimal(5),
                    jdbc.query("SELECT evidence_id, uploaded_at FROM Evidences WHERE booking_detail_id=? ORDER BY evidence_id",
                        (ev, index) -> new EvidenceCard(ev.getLong(1), time(ev.getObject(2, OffsetDateTime.class))), rs.getLong(1))), bookingId);
    }

    public Long bookingIdForDetail(Long detailId, PortalUser user) {
        List<Long> ids = jdbc.query("SELECT booking_id FROM Booking_Detail WHERE booking_detail_id=?", (rs, row) -> rs.getLong(1), detailId);
        if (ids.isEmpty()) { throw new ResponseStatusException(HttpStatus.NOT_FOUND); }
        booking(ids.getFirst(), user);
        return ids.getFirst();
    }

    public String evidencePath(Long evidenceId, PortalUser user) {
        List<Long> ids = jdbc.query("""
                SELECT d.booking_id FROM Evidences e JOIN Booking_Detail d ON d.booking_detail_id=e.booking_detail_id WHERE e.evidence_id=?
                """, (rs, row) -> rs.getLong(1), evidenceId);
        if (ids.isEmpty()) { throw new ResponseStatusException(HttpStatus.NOT_FOUND); }
        booking(ids.getFirst(), user);
        return jdbc.queryForObject("SELECT storage_path FROM Evidences WHERE evidence_id=?", String.class, evidenceId);
    }

    private List<BookingCard> bookingQuery(String condition, Object[] params) {
        return jdbc.query("""
                SELECT b.*, c.display_name AS customer_name, x.staff_id, s.display_name AS staff_name,
                       x.total, p.payment_status, p.payment_method, last_attempt.attempt_status, last_attempt.checkout_url, f.rating
                FROM Booking b JOIN Customer c ON c.customer_id=b.customer_id
                JOIN (SELECT d.booking_id, MIN(w.staff_id) AS staff_id, SUM(d.unit_price_snapshot) AS total
                      FROM Booking_Detail d JOIN Work_Schedule w ON w.work_schedule_id=d.work_schedule_id GROUP BY d.booking_id) x ON x.booking_id=b.booking_id
                JOIN Staff s ON s.staff_id=x.staff_id
                LEFT JOIN Payment p ON p.booking_id=b.booking_id LEFT JOIN Feedback f ON f.booking_id=b.booking_id
                OUTER APPLY (SELECT TOP (1) pa.attempt_status, pa.checkout_url FROM Payment_Attempt pa
                             WHERE pa.payment_id=p.payment_id ORDER BY pa.created_at DESC, pa.attempt_id DESC) last_attempt
                """ + condition + " ORDER BY b.start_at DESC", (rs, row) -> new BookingCard(
                    rs.getLong("booking_id"), rs.getLong("customer_id"), rs.getLong("staff_id"), rs.getString("customer_name"), rs.getString("staff_name"),
                    rs.getObject("start_at", OffsetDateTime.class), rs.getObject("end_at", OffsetDateTime.class), rs.getString("booking_status"),
                    rs.getBigDecimal("total"), rs.getString("payment_status"), rs.getString("payment_method"),
                    rs.getString("attempt_status"), rs.getString("checkout_url"), (Integer) rs.getObject("rating", Integer.class)), params);
    }
}
