package com.housekeeping.management.repository;

import com.housekeeping.management.enums.BookingStatus;
import com.housekeeping.management.enums.LeaveStatus;
import com.housekeeping.management.entity.Booking;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BookingAvailabilityRepository extends JpaRepository<Booking, Long> {
    interface StaffRow { Long getStaffId(); String getFullName(); com.housekeeping.management.enums.StaffType getStaffType(); }
    interface IntervalRow { Long getStaffId(); LocalTime getStartTime(); LocalTime getEndTime(); }
    interface DatedIntervalRow { LocalDate getWorkDate(); Long getStaffId(); LocalTime getStartTime(); LocalTime getEndTime(); }
    interface BookingIntervalRow { Long getStaffId(); LocalDateTime getStartTime(); LocalDateTime getEndTime(); }
    interface DatedBookingIntervalRow { LocalDateTime getStartAt(); Long getStaffId(); LocalDateTime getStartTime(); LocalDateTime getEndTime(); }
    interface LeaveRangeRow { Long getStaffId(); LocalDate getStartDate(); LocalDate getEndDate(); }

    @Query("select staff.staffId as staffId, user.fullName as fullName, staff.staffType as staffType "
            + "from Staff staff join staff.user user where user.status=com.housekeeping.management.enums.UserStatus.ACTIVE "
            + "order by user.fullName, staff.staffId")
    List<StaffRow> findActiveStaffRows();

    @Query("select availability.staffId as staffId, availability.startTime as startTime, availability.endTime as endTime "
            + "from StaffAvailability availability where availability.workDate=:date and availability.active=true "
            + "order by availability.staffId, availability.startTime")
    List<IntervalRow> findActiveAvailabilityRows(@Param("date") LocalDate date);

    @Query("select distinct leave.staff.staffId from StaffLeave leave where leave.status=:status "
            + "and leave.startDate<=:date and leave.endDate>=:date")
    List<Long> findStaffOnLeave(@Param("date") LocalDate date, @Param("status") LeaveStatus status);

    @Query("select booking.staffId as staffId, booking.startTime as startTime, booking.endTime as endTime "
            + "from Booking booking where booking.startTime>=:from and booking.startTime<:until "
            + "and booking.status in :statuses order by booking.staffId, booking.startTime")
    List<BookingIntervalRow> findBookedIntervalRows(@Param("from") LocalDateTime from, @Param("until") LocalDateTime until,
            @Param("statuses") List<BookingStatus> statuses);

    @Query("select availability.workDate as workDate, availability.staffId as staffId, "
            + "availability.startTime as startTime, availability.endTime as endTime from StaffAvailability availability "
            + "where availability.workDate>=:from and availability.workDate<=:through and availability.active=true "
            + "order by availability.workDate, availability.staffId, availability.startTime")
    List<DatedIntervalRow> findDatedAvailabilityRows(@Param("from") LocalDate from, @Param("through") LocalDate through);

    @Query("select leave.staff.staffId as staffId, leave.startDate as startDate, leave.endDate as endDate "
            + "from StaffLeave leave where leave.status=:status and leave.startDate<=:through and leave.endDate>=:from")
    List<LeaveRangeRow> findApprovedLeaveRows(@Param("from") LocalDate from, @Param("through") LocalDate through,
            @Param("status") LeaveStatus status);

    @Query("select booking.startTime as startAt, booking.staffId as staffId, booking.startTime as startTime, "
            + "booking.endTime as endTime from Booking booking where booking.startTime>=:from and booking.startTime<:until "
            + "and booking.status in :statuses order by booking.startTime")
    List<DatedBookingIntervalRow> findDatedBookedIntervalRows(@Param("from") LocalDateTime from, @Param("until") LocalDateTime until,
            @Param("statuses") List<BookingStatus> statuses);

    default List<StaffOption> activeStaff() {
        return findActiveStaffRows().stream().map(row -> new StaffOption(row.getStaffId(), row.getFullName(),
                row.getStaffType().name())).toList();
    }
    default List<Interval> activeAvailability(LocalDate date) {
        return findActiveAvailabilityRows(date).stream()
                .map(row -> new Interval(row.getStaffId(), row.getStartTime(), row.getEndTime())).toList();
    }
    default List<Long> staffOnApprovedLeave(LocalDate date) { return findStaffOnLeave(date, LeaveStatus.APPROVED); }
    default List<Interval> bookedIntervals(LocalDate date) {
        return findBookedIntervalRows(date.atStartOfDay(), date.plusDays(1).atStartOfDay(), activeBookingStatuses()).stream()
                .map(row -> new Interval(row.getStaffId(), row.getStartTime().toLocalTime(), row.getEndTime().toLocalTime())).toList();
    }
    default List<DatedInterval> activeAvailability(LocalDate from, LocalDate through) {
        return findDatedAvailabilityRows(from, through).stream().map(row -> new DatedInterval(row.getWorkDate(),
                row.getStaffId(), row.getStartTime(), row.getEndTime())).toList();
    }
    default List<LeaveRange> approvedLeaves(LocalDate from, LocalDate through) {
        return findApprovedLeaveRows(from, through, LeaveStatus.APPROVED).stream()
                .map(row -> new LeaveRange(row.getStaffId(), row.getStartDate(), row.getEndDate())).toList();
    }
    default List<DatedInterval> bookedIntervals(LocalDate from, LocalDate through) {
        return findDatedBookedIntervalRows(from.atStartOfDay(), through.plusDays(1).atStartOfDay(), activeBookingStatuses())
                .stream().map(row -> new DatedInterval(row.getStartAt().toLocalDate(), row.getStaffId(),
                        row.getStartTime().toLocalTime(), row.getEndTime().toLocalTime())).toList();
    }
    private static List<BookingStatus> activeBookingStatuses() {
        return List.of(BookingStatus.CONFIRMED, BookingStatus.AWAITING_PAYMENT, BookingStatus.COMPLETED);
    }

    record StaffOption(Long id, String name, String staffType) { }
    record Interval(Long staffId, LocalTime start, LocalTime end) { }
    record DatedInterval(LocalDate date, Long staffId, LocalTime start, LocalTime end) { }
    record LeaveRange(Long staffId, LocalDate start, LocalDate end) { }
}
