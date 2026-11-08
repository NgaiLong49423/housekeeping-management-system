package com.housekeeping.management.repository;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class BookingAvailabilityRepository {
    private final JdbcTemplate jdbc;

    public BookingAvailabilityRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public List<StaffOption> activeStaff() {
        return jdbc.query("""
                SELECT s.staff_id,u.full_name,s.staff_type
                FROM dbo.Staff s JOIN dbo.Users u ON u.user_id=s.user_id
                WHERE u.status='ACTIVE' ORDER BY u.full_name,s.staff_id
                """, (rs,row) -> new StaffOption(rs.getLong("staff_id"),rs.getString("full_name"),rs.getString("staff_type")));
    }

    public List<Interval> activeAvailability(LocalDate date) {
        return jdbc.query("""
                SELECT staff_id,start_time,end_time
                FROM dbo.Staff_Availability
                WHERE work_date=? AND is_active=1 ORDER BY staff_id,start_time
                """, (rs,row) -> new Interval(rs.getLong("staff_id"),rs.getTime("start_time").toLocalTime(),rs.getTime("end_time").toLocalTime()),
                java.sql.Date.valueOf(date));
    }

    public List<Long> staffOnApprovedLeave(LocalDate date) {
        return jdbc.query("SELECT DISTINCT staff_id FROM dbo.Staff_Leaves WHERE status='APPROVED' AND start_date<=? AND end_date>=?",
                (rs,row) -> rs.getLong(1), java.sql.Date.valueOf(date), java.sql.Date.valueOf(date));
    }

    public List<Interval> bookedIntervals(LocalDate date) {
        return jdbc.query("""
                SELECT staff_id,CAST(start_time AS time) start_clock,CAST(end_time AS time) end_clock
                FROM dbo.Bookings
                WHERE CONVERT(date,start_time)=? AND status IN ('CONFIRMED','AWAITING_PAYMENT','COMPLETED')
                """, (rs,row) -> new Interval(rs.getLong("staff_id"),rs.getTime("start_clock").toLocalTime(),rs.getTime("end_clock").toLocalTime()),
                java.sql.Date.valueOf(date));
    }

    public List<DatedInterval> activeAvailability(LocalDate from, LocalDate through) {
        return jdbc.query("""
                SELECT work_date,staff_id,start_time,end_time FROM dbo.Staff_Availability
                WHERE work_date>=? AND work_date<=? AND is_active=1 ORDER BY work_date,staff_id,start_time
                """, (rs,row) -> new DatedInterval(rs.getDate("work_date").toLocalDate(),rs.getLong("staff_id"),
                rs.getTime("start_time").toLocalTime(),rs.getTime("end_time").toLocalTime()),
                java.sql.Date.valueOf(from),java.sql.Date.valueOf(through));
    }

    public List<LeaveRange> approvedLeaves(LocalDate from, LocalDate through) {
        return jdbc.query("""
                SELECT staff_id,start_date,end_date FROM dbo.Staff_Leaves
                WHERE status='APPROVED' AND start_date<=? AND end_date>=?
                """, (rs,row) -> new LeaveRange(rs.getLong("staff_id"),rs.getDate("start_date").toLocalDate(),
                rs.getDate("end_date").toLocalDate()),java.sql.Date.valueOf(through),java.sql.Date.valueOf(from));
    }

    public List<DatedInterval> bookedIntervals(LocalDate from, LocalDate through) {
        LocalDateTime rangeStart=from.atStartOfDay();
        LocalDateTime rangeEnd=through.plusDays(1).atStartOfDay();
        return jdbc.query("""
                SELECT CONVERT(date,start_time) work_date,staff_id,CAST(start_time AS time) start_clock,CAST(end_time AS time) end_clock
                FROM dbo.Bookings WHERE start_time>=? AND start_time<? AND status IN ('CONFIRMED','AWAITING_PAYMENT','COMPLETED')
                """, (rs,row) -> new DatedInterval(rs.getDate("work_date").toLocalDate(),rs.getLong("staff_id"),
                rs.getTime("start_clock").toLocalTime(),rs.getTime("end_clock").toLocalTime()),
                java.sql.Timestamp.valueOf(rangeStart),java.sql.Timestamp.valueOf(rangeEnd));
    }

    public record StaffOption(Long id, String name, String staffType) { }
    public record Interval(Long staffId, LocalTime start, LocalTime end) { }
    public record DatedInterval(LocalDate date, Long staffId, LocalTime start, LocalTime end) { }
    public record LeaveRange(Long staffId, LocalDate start, LocalDate end) { }
}
