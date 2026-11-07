package com.housekeeping.management.repository;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class BookingPersistenceRepository {
    private final JdbcTemplate jdbc;

    public BookingPersistenceRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<BookingState> lockBooking(Long bookingId) {
        return findBooking(bookingId, true);
    }

    public void lockCompanySettings() {
        List<Long> companyIds = jdbc.query("SELECT company_id FROM dbo.Company_Settings WITH (HOLDLOCK, INDEX(0)) ORDER BY company_id", (rs,row) -> rs.getLong(1));
        if (companyIds.size() != 1) throw new IllegalStateException("Cần cấu hình chính xác một vị trí và giờ hoạt động của công ty.");
    }

    public void validateStillOpen(LocalDateTime requestedStart, LocalDateTime currentTime) {
        lockCompanySettings();
        if (requestedStart.isBefore(currentTime.plusMinutes(120)) || requestedStart.isAfter(currentTime.plusDays(30))) {
            throw new IllegalArgumentException("Giờ đặt không còn nằm trong khoảng cho phép (tối thiểu 120 phút, tối đa 30 ngày). Hãy tải lại giờ.");
        }
    }

    public Optional<BookingState> findBooking(Long bookingId) {
        return findBooking(bookingId, false);
    }

    private Optional<BookingState> findBooking(Long bookingId, boolean lock) {
        String lockHint = lock ? " WITH (UPDLOCK, HOLDLOCK)" : "";
        List<BookingState> rows = jdbc.query("""
                SELECT booking_id, customer_id, staff_id, house_id, start_time, end_time, status, latitude_snapshot, longitude_snapshot
                FROM dbo.Bookings""" + lockHint + " WHERE booking_id=?", (rs, row) -> new BookingState(rs.getLong("booking_id"), rs.getLong("customer_id"),
                    rs.getLong("staff_id"), rs.getLong("house_id"), rs.getObject("start_time", LocalDateTime.class),
                    rs.getObject("end_time", LocalDateTime.class), rs.getString("status"), rs.getBigDecimal("latitude_snapshot"), rs.getBigDecimal("longitude_snapshot")), bookingId);
        return rows.stream().findFirst();
    }

    public int changeStatus(Long bookingId, String expectedStatus, String newStatus) {
        return jdbc.update("UPDATE dbo.Bookings SET status=? WHERE booking_id=? AND status=?",
                newStatus, bookingId, expectedStatus);
    }

    public boolean hasUnfinishedBookingForStaff(Long staffId) {
        Integer matches = jdbc.queryForObject("""
                SELECT COUNT(*) FROM dbo.Bookings
                WHERE staff_id=? AND status IN ('CONFIRMED', 'AWAITING_PAYMENT')
                """, Integer.class, staffId);
        return matches != null && matches > 0;
    }

    public BigDecimal bookingAmount(Long bookingId) {
        return jdbc.queryForObject("SELECT total_amount FROM dbo.Bookings WHERE booking_id=?", BigDecimal.class, bookingId);
    }

    public int cancel(Long bookingId, Long cancelledBy, String reason, LocalDateTime cancelledAt) {
        return jdbc.update("""
                UPDATE dbo.Bookings SET status='CANCELLED', cancelled_at=?, cancelled_by=?, cancellation_reason=?
                WHERE booking_id=? AND status='CONFIRMED'
                """, cancelledAt, cancelledBy, reason, bookingId);
    }

    public int assignStaff(Long bookingId, Long expectedStaffId, Long replacementStaffId) {
        return jdbc.update("""
                UPDATE dbo.Bookings SET staff_id=?
                WHERE booking_id=? AND staff_id=? AND status='CONFIRMED'
                """, replacementStaffId, bookingId, expectedStaffId);
    }

    public boolean companyAcceptsStart(LocalDate date, LocalTime startTime) {
        int dayOfWeek = date.getDayOfWeek().getValue();
        Integer matches = jdbc.queryForObject("""
                SELECT COUNT(*) FROM dbo.Company_Operating_Hours h
                JOIN dbo.Company_Settings c ON c.company_id=h.company_id
                WHERE h.effective_from=(SELECT MAX(v.effective_from) FROM dbo.Company_Operating_Hours v WHERE v.company_id=h.company_id AND v.effective_from<=?)
                  AND h.day_of_week=? AND h.is_closed=0
                  AND CAST(? AS TIME(0)) >= h.opening_time AND CAST(? AS TIME(0)) < h.closing_time
                """, Integer.class, date, dayOfWeek, startTime, startTime);
        return matches != null && matches == 1;
    }

    public boolean companyAcceptsInterval(LocalDate date, LocalTime startTime, LocalTime endTime) {
        Integer matches = jdbc.queryForObject("""
                SELECT COUNT(*) FROM dbo.Company_Operating_Hours h
                JOIN dbo.Company_Settings c ON c.company_id=h.company_id
                WHERE h.effective_from=(SELECT MAX(v.effective_from) FROM dbo.Company_Operating_Hours v WHERE v.company_id=h.company_id AND v.effective_from<=?)
                  AND h.day_of_week=? AND h.is_closed=0
                  AND CAST(? AS TIME(0)) >= h.opening_time AND CAST(? AS TIME(0)) <= h.closing_time
                """, Integer.class, date, date.getDayOfWeek().getValue(), startTime, endTime);
        return matches != null && matches == 1;
    }

    public boolean partTimeAcceptsInterval(Long staffId, LocalDate date, LocalTime startTime, LocalTime endTime) {
        Integer matches = jdbc.queryForObject("""
                SELECT COUNT(*) FROM dbo.Staff_Availability
                WHERE staff_id=? AND work_date=? AND is_active=1
                  AND start_time<=CAST(? AS TIME(0)) AND end_time>=CAST(? AS TIME(0))
                """, Integer.class, staffId, date, startTime, endTime);
        return matches != null && matches > 0;
    }

    public boolean hasPartTimeAvailability(Long staffId, LocalDate date, LocalTime startTime) {
        Integer matches = jdbc.queryForObject("""
                SELECT COUNT(*) FROM dbo.Staff_Availability
                WHERE staff_id=? AND work_date=? AND is_active=1
                  AND start_time<=CAST(? AS TIME(0)) AND end_time>CAST(? AS TIME(0))
                """, Integer.class, staffId, date, startTime, startTime);
        return matches != null && matches > 0;
    }

    public boolean isOnApprovedLeave(Long staffId, LocalDate date) {
        Integer matches = jdbc.queryForObject("""
                SELECT COUNT(*) FROM dbo.Staff_Leaves
                WHERE staff_id=? AND status='APPROVED' AND start_date<=? AND end_date>=?
                """, Integer.class, staffId, date, date);
        return matches != null && matches > 0;
    }

    public boolean hasConflictingBooking(Long staffId, LocalDateTime start, LocalDateTime end, int gapMinutes) {
        Integer matches = jdbc.queryForObject("""
                SELECT COUNT(*) FROM dbo.Bookings
                WHERE staff_id=? AND status IN ('CONFIRMED', 'AWAITING_PAYMENT', 'COMPLETED')
                  AND start_time < DATEADD(MINUTE, ?, ?)
                  AND end_time > DATEADD(MINUTE, -?, ?)
                """, Integer.class, staffId, gapMinutes, end, gapMinutes, start);
        return matches != null && matches > 0;
    }

    public boolean hasConflictingBookingExcept(Long staffId, LocalDateTime start, LocalDateTime end, Long excludedBookingId, int gapMinutes) {
        Integer matches = jdbc.queryForObject("""
                SELECT COUNT(*) FROM dbo.Bookings
                WHERE staff_id=? AND booking_id<>? AND status IN ('CONFIRMED', 'AWAITING_PAYMENT', 'COMPLETED')
                  AND start_time < DATEADD(MINUTE, ?, ?)
                  AND end_time > DATEADD(MINUTE, -?, ?)
                """, Integer.class, staffId, excludedBookingId, gapMinutes, end, gapMinutes, start);
        return matches != null && matches > 0;
    }

    public List<AppointmentPoint> adjacentAppointments(Long staffId, LocalDateTime start, LocalDateTime end, Long exceptId) {
        List<AppointmentPoint> previous = jdbc.query("""
                SELECT TOP (1) booking_id, start_time, end_time, latitude_snapshot, longitude_snapshot
                FROM dbo.Bookings WHERE staff_id=? AND booking_id<>? AND status IN ('CONFIRMED','AWAITING_PAYMENT','COMPLETED')
                  AND end_time<=? ORDER BY end_time DESC
                """, (rs, row) -> new AppointmentPoint(rs.getLong(1), rs.getObject(2, LocalDateTime.class),
                rs.getObject(3, LocalDateTime.class), rs.getBigDecimal(4), rs.getBigDecimal(5)),
                staffId, exceptId == null ? -1L : exceptId, start);
        List<AppointmentPoint> next = jdbc.query("""
                SELECT TOP (1) booking_id, start_time, end_time, latitude_snapshot, longitude_snapshot
                FROM dbo.Bookings WHERE staff_id=? AND booking_id<>? AND status IN ('CONFIRMED','AWAITING_PAYMENT','COMPLETED')
                  AND start_time>=? ORDER BY start_time ASC
                """, (rs, row) -> new AppointmentPoint(rs.getLong(1), rs.getObject(2, LocalDateTime.class),
                rs.getObject(3, LocalDateTime.class), rs.getBigDecimal(4), rs.getBigDecimal(5)),
                staffId, exceptId == null ? -1L : exceptId, end);
        java.util.ArrayList<AppointmentPoint> result = new java.util.ArrayList<>(previous);
        result.addAll(next);
        return List.copyOf(result);
    }

    public record AppointmentPoint(Long bookingId, LocalDateTime start, LocalDateTime end, BigDecimal latitude, BigDecimal longitude) { }

    public int confirmCustomerArrival(Long bookingId, Long customerId, LocalDateTime now) {
        return jdbc.update("""
                UPDATE dbo.Bookings SET customer_arrived_at=?
                WHERE booking_id=? AND customer_id=? AND status='CONFIRMED' AND start_time<=? AND customer_arrived_at IS NULL
                """, now, bookingId, customerId, now);
    }

    public Long insertBooking(Long customerId, Long staffId, Long houseId, String address,
            BigDecimal latitude, BigDecimal longitude, String addressNote, LocalDateTime start,
            LocalDateTime end, int duration, BigDecimal amount, String areaNote, LocalDateTime createdAt) {
        KeyHolder keys = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO dbo.Bookings (customer_id, staff_id, original_staff_id, house_id,
                        address_snapshot, latitude_snapshot, longitude_snapshot, address_note_snapshot,
                        start_time, end_time, total_duration_minutes, total_amount, area_note, status, created_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'CONFIRMED', ?)
                    """, Statement.RETURN_GENERATED_KEYS);
            statement.setLong(1, customerId);
            statement.setLong(2, staffId);
            statement.setLong(3, staffId);
            statement.setLong(4, houseId);
            statement.setString(5, address);
            statement.setBigDecimal(6, latitude);
            statement.setBigDecimal(7, longitude);
            statement.setString(8, addressNote);
            statement.setObject(9, start);
            statement.setObject(10, end);
            statement.setInt(11, duration);
            statement.setBigDecimal(12, amount);
            statement.setString(13, areaNote);
            statement.setObject(14, createdAt);
            return statement;
        }, keys);
        Number key = keys.getKey();
        if (key == null) {
            throw new IllegalStateException("SQL Server did not return the new booking ID.");
        }
        return key.longValue();
    }

    public void insertDetails(Long bookingId, List<ServiceSnapshot> services) {
        jdbc.batchUpdate("""
                INSERT INTO dbo.Booking_Details
                    (booking_id, service_id, service_name_snapshot, unit_price, duration_minutes)
                VALUES (?, ?, ?, ?, ?)
                """, services, services.size(), (statement, service) -> {
                    statement.setLong(1, bookingId);
                    statement.setLong(2, service.id());
                    statement.setString(3, service.name());
                    statement.setBigDecimal(4, service.price());
                    statement.setInt(5, service.durationMinutes());
                });
    }

    public record ServiceSnapshot(Long id, String name, BigDecimal price, int durationMinutes) { }
    public record BookingState(Long id, Long customerId, Long staffId, Long houseId,
            LocalDateTime startTime, LocalDateTime endTime, String status, BigDecimal latitude, BigDecimal longitude) {
        public BookingState(Long id, Long customerId, Long staffId, Long houseId, LocalDateTime startTime, LocalDateTime endTime, String status) {
            this(id,customerId,staffId,houseId,startTime,endTime,status,null,null);
        }
    }
}
