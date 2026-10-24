package com.housekeeping.management.repository;

import java.time.LocalDate;
import java.time.LocalTime;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class StaffAvailabilityRepository {
    private final JdbcTemplate jdbc;

    public StaffAvailabilityRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public boolean overlapsAnotherInterval(Long staffId, LocalDate date, LocalTime start, LocalTime end, Long exceptId) {
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM dbo.Staff_Availability WITH (UPDLOCK, HOLDLOCK)
                WHERE staff_id=? AND work_date=? AND (? IS NULL OR availability_id<>?)
                  AND start_time < ? AND end_time > ?
                """, Integer.class, staffId, date, exceptId, exceptId, end, start);
        return count != null && count > 0;
    }

    public boolean containsExistingBookingStart(Long id, Long staffId) {
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM dbo.Bookings b
                JOIN dbo.Staff_Availability a ON a.staff_id=b.staff_id
                  AND a.work_date=CONVERT(date,b.start_time)
                  AND a.start_time<=CAST(b.start_time AS time) AND a.end_time>CAST(b.start_time AS time)
                WHERE a.availability_id=? AND a.staff_id=?
                  AND b.status IN ('CONFIRMED','AWAITING_PAYMENT','COMPLETED')
                """, Integer.class, id, staffId);
        return count != null && count > 0;
    }

    public boolean unchanged(Long id, Long staffId, LocalDate date, LocalTime start, LocalTime end) {
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM dbo.Staff_Availability
                WHERE availability_id=? AND staff_id=? AND work_date=? AND start_time=? AND end_time=?
                """, Integer.class, id, staffId, date, start, end);
        return count != null && count == 1;
    }

    public int insert(Long staffId, LocalDate date, LocalTime start, LocalTime end) {
        return jdbc.update("""
                INSERT INTO dbo.Staff_Availability (staff_id, work_date, start_time, end_time)
                VALUES (?, ?, ?, ?)
                """, staffId, date, start, end);
    }

    public int update(Long id, Long staffId, LocalDate date, LocalTime start, LocalTime end) {
        return jdbc.update("""
                UPDATE dbo.Staff_Availability SET work_date=?, start_time=?, end_time=?
                WHERE availability_id=? AND staff_id=?
                """, date, start, end, id, staffId);
    }

    public int delete(Long id, Long staffId) {
        return jdbc.update("DELETE FROM dbo.Staff_Availability WHERE availability_id=? AND staff_id=?", id, staffId);
    }
}
