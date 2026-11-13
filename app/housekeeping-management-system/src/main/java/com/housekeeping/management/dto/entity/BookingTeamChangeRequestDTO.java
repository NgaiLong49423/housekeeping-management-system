package com.housekeeping.management.dto.entity;

import com.housekeeping.management.entity.BookingTeamChangeRequest;
import java.time.LocalDateTime;

public record BookingTeamChangeRequestDTO(Long requestId, Long bookingId, Long requestedBy, Long affectedStaffId,
        Integer proposedStaffCount, String reason, String requestStatus, String resolution, LocalDateTime createdAt,
        LocalDateTime resolvedAt, Long resolvedBy) {
    public static BookingTeamChangeRequestDTO convertEntityToDTO(BookingTeamChangeRequest entity) {
        if (entity == null) return null;
        return new BookingTeamChangeRequestDTO(entity.getRequestId(), entity.getBookingId(), entity.getRequestedBy(),
                entity.getAffectedStaffId(), entity.getProposedStaffCount(), entity.getReason(), entity.getRequestStatus(),
                entity.getResolution(), entity.getCreatedAt(), entity.getResolvedAt(), entity.getResolvedBy());
    }

    public BookingTeamChangeRequest convertDTOToEntity() {
        BookingTeamChangeRequest entity = new BookingTeamChangeRequest();
        entity.setRequestId(requestId); entity.setBookingId(bookingId); entity.setRequestedBy(requestedBy);
        entity.setAffectedStaffId(affectedStaffId); entity.setProposedStaffCount(proposedStaffCount);
        entity.setReason(reason); entity.setRequestStatus(requestStatus); entity.setResolution(resolution);
        entity.setCreatedAt(createdAt); entity.setResolvedAt(resolvedAt); entity.setResolvedBy(resolvedBy);
        return entity;
    }
}
