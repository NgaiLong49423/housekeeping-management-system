package com.housekeeping.management.dto.entity;

import com.housekeeping.management.entity.BookingStaffAssignment;
import com.housekeeping.management.enums.BookingStaffAssignmentStatus;
import com.housekeeping.management.enums.BookingStaffRole;
import java.time.LocalDateTime;

public record BookingStaffAssignmentDTO(Long bookingStaffId, Long bookingId, Long staffId,
        BookingStaffRole role, BookingStaffAssignmentStatus status, LocalDateTime assignedAt, LocalDateTime arrivedAt) {
    public static BookingStaffAssignmentDTO convertEntityToDTO(BookingStaffAssignment entity) {
        if (entity == null) return null;
        return new BookingStaffAssignmentDTO(entity.getBookingStaffId(), entity.getBookingId(), entity.getStaffId(),
                entity.getRole(), entity.getStatus(), entity.getAssignedAt(), entity.getArrivedAt());
    }

    public BookingStaffAssignment convertDTOToEntity() {
        BookingStaffAssignment entity = new BookingStaffAssignment();
        entity.setBookingStaffId(bookingStaffId);
        entity.setBookingId(bookingId);
        entity.setStaffId(staffId);
        entity.setRole(role);
        entity.setStatus(status);
        entity.setAssignedAt(assignedAt);
        entity.setArrivedAt(arrivedAt);
        return entity;
    }
}
