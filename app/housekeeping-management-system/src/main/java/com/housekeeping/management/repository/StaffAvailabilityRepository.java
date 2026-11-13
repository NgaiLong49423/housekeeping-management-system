package com.housekeeping.management.repository;

import com.housekeeping.management.entity.StaffAvailability;
import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StaffAvailabilityRepository extends JpaRepository<StaffAvailability, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<StaffAvailability> findAllByStaffIdAndWorkDateAndActiveTrue(Long staffId, LocalDate workDate);
    List<StaffAvailability> findByStaffIdAndWorkDateAndActiveTrue(Long staffId, LocalDate workDate);
    List<StaffAvailability> findAllByActiveTrueAndWorkDateGreaterThanEqual(LocalDate from);
    List<StaffAvailability> findAllByActiveTrueAndWorkDateGreaterThanEqualAndWorkDateLessThan(
            LocalDate from, LocalDate until);

    boolean existsByAvailabilityIdAndStaffIdAndActiveTrue(Long availabilityId, Long staffId);
    boolean existsByAvailabilityIdAndStaffIdAndWorkDateBefore(Long availabilityId, Long staffId, LocalDate date);

    default boolean existsByAvailabilityIdAndStaffIdAndWorkDateAndStartTimeAndEndTime(
            Long availabilityId, Long staffId, LocalDate workDate, LocalTime startTime, LocalTime endTime) {
        return findById(availabilityId)
                .filter(slot -> slot.getStaffId().equals(staffId)
                        && slot.getWorkDate().equals(workDate)
                        && slot.getStartTime().equals(startTime)
                        && slot.getEndTime().equals(endTime))
                .isPresent();
    }

    default boolean existsByStaffIdAndWorkDateAndActiveTrueAndStartTimeLessThanEqualAndEndTimeGreaterThanEqual(
            Long staffId, LocalDate workDate, LocalTime startTime, LocalTime endTime) {
        return findByStaffIdAndWorkDateAndActiveTrue(staffId, workDate).stream()
                .anyMatch(slot -> !slot.getStartTime().isAfter(startTime) && !slot.getEndTime().isBefore(endTime));
    }

    default boolean existsByStaffIdAndWorkDateAndActiveTrueAndStartTimeLessThanEqualAndEndTimeGreaterThan(
            Long staffId, LocalDate workDate, LocalTime startTime, LocalTime endTime) {
        return findByStaffIdAndWorkDateAndActiveTrue(staffId, workDate).stream()
                .anyMatch(slot -> !slot.getStartTime().isAfter(startTime) && slot.getEndTime().isAfter(endTime));
    }

    default boolean overlapsAnotherInterval(Long staffId, LocalDate workDate,
            LocalTime startTime, LocalTime endTime, Long exceptId) {
        return findByStaffIdAndWorkDateAndActiveTrue(staffId, workDate).stream()
                .anyMatch(slot -> (exceptId == null || !slot.getAvailabilityId().equals(exceptId))
                        && slot.getStartTime().isBefore(endTime)
                        && slot.getEndTime().isAfter(startTime));
    }

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update StaffAvailability availability set availability.active=false where "
            + "availability.availabilityId=:id and availability.staffId=:staffId and availability.active=true")
    int deactivate(@Param("id") Long id, @Param("staffId") Long staffId);

    default boolean unchanged(Long id, Long staffId, LocalDate date, LocalTime start, LocalTime end) {
        return existsByAvailabilityIdAndStaffIdAndWorkDateAndStartTimeAndEndTime(id, staffId, date, start, end);
    }

    default boolean isActive(Long id, Long staffId) {
        return existsByAvailabilityIdAndStaffIdAndActiveTrue(id, staffId);
    }

    default boolean isForPastDate(Long id, Long staffId, LocalDate today) {
        return existsByAvailabilityIdAndStaffIdAndWorkDateBefore(id, staffId, today);
    }

    default int insert(Long staffId, LocalDate date, LocalTime start, LocalTime end) {
        StaffAvailability availability = new StaffAvailability();
        availability.setStaffId(staffId);
        availability.setWorkDate(date);
        availability.setStartTime(start);
        availability.setEndTime(end);
        availability.setCreatedAt(java.time.LocalDateTime.now(java.time.ZoneOffset.ofHours(7)).withNano(0));
        availability.setActive(true);
        saveAndFlush(availability);
        return 1;
    }
}
