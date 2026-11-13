package com.housekeeping.management.dto.entity;

import com.housekeeping.management.entity.BookingDelayProposal;
import java.time.LocalDateTime;

public record BookingDelayProposalDTO(Long proposalId, Long bookingId, Long staffId,
        LocalDateTime expectedArrivalAt, String reason, LocalDateTime proposedAt,
        String status, LocalDateTime respondedAt) {
    public static BookingDelayProposalDTO convertEntityToDTO(BookingDelayProposal entity) {
        if (entity == null) return null;
        return new BookingDelayProposalDTO(entity.getProposalId(), entity.getBookingId(), entity.getStaffId(),
                entity.getExpectedArrivalAt(), entity.getReason(), entity.getProposedAt(), entity.getStatus(), entity.getRespondedAt());
    }
    public BookingDelayProposal convertDTOToEntity() {
        BookingDelayProposal entity = new BookingDelayProposal();
        entity.setProposalId(proposalId); entity.setBookingId(bookingId); entity.setStaffId(staffId);
        entity.setExpectedArrivalAt(expectedArrivalAt); entity.setReason(reason); entity.setProposedAt(proposedAt);
        entity.setStatus(status); entity.setRespondedAt(respondedAt);
        return entity;
    }
}
