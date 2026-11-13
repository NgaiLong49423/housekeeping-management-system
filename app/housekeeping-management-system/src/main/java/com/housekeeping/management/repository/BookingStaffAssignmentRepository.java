package com.housekeeping.management.repository;

import com.housekeeping.management.entity.BookingStaffAssignment;
import com.housekeeping.management.enums.BookingStaffAssignmentStatus;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BookingStaffAssignmentRepository extends JpaRepository<BookingStaffAssignment, Long> {
    List<BookingStaffAssignment> findAllByBookingIdAndStatusOrderByRoleAscBookingStaffIdAsc(
            Long bookingId, BookingStaffAssignmentStatus status);

    List<BookingStaffAssignment> findAllByBookingIdInAndStatus(List<Long> bookingIds,
            BookingStaffAssignmentStatus status);

    List<BookingStaffAssignment> findAllByStaffIdAndStatus(Long staffId, BookingStaffAssignmentStatus status);

    boolean existsByBookingIdAndStaffIdAndStatus(Long bookingId, Long staffId,
            BookingStaffAssignmentStatus status);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update BookingStaffAssignment assignment set assignment.arrivedAt=:at "
            + "where assignment.bookingId=:booking and assignment.staffId=:staff "
            + "and assignment.status=com.housekeeping.management.enums.BookingStaffAssignmentStatus.ASSIGNED "
            + "and assignment.arrivedAt is null")
    int recordArrival(@Param("booking") Long bookingId, @Param("staff") Long staffId,
            @Param("at") LocalDateTime arrivedAt);
}
