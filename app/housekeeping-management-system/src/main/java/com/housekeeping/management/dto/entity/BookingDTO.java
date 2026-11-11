package com.housekeeping.management.dto.entity;

import com.housekeeping.management.domain.BookingStatus;
import com.housekeeping.management.entity.Booking;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record BookingDTO(Long bookingId, Long customerId, Long staffId, Long originalStaffId, Long houseId,
        String addressSnapshot, BigDecimal latitudeSnapshot, BigDecimal longitudeSnapshot, String addressNoteSnapshot,
        LocalDateTime startTime, LocalDateTime endTime, Integer totalDurationMinutes, BigDecimal totalAmount,
        String areaNote, BookingStatus status, LocalDateTime cancelledAt, Long cancelledBy, String cancellationReason,
        LocalDateTime createdAt, LocalDateTime customerArrivedAt, LocalDateTime staffArrivedAt,
        LocalDateTime customerDelayReportedAt, LocalDateTime incidentReportedAt, String incidentNote,
        LocalDateTime incidentExpectedArrivalAt, String incidentCustomerResponse,
        LocalDateTime incidentCustomerRespondedAt, LocalDateTime workStartedAt, Integer delayMinutes,
        BigDecimal delayDiscountPercent, BigDecimal delayDiscountAmount, LocalDateTime serviceCompletedAt) {

    public static BookingDTO convertEntityToDTO(Booking entity) {
        if (entity == null) return null;
        return new BookingDTO(entity.getBookingId(), entity.getCustomerId(), entity.getStaffId(),
                entity.getOriginalStaffId(), entity.getHouseId(), entity.getAddressSnapshot(),
                entity.getLatitudeSnapshot(), entity.getLongitudeSnapshot(), entity.getAddressNoteSnapshot(),
                entity.getStartTime(), entity.getEndTime(), entity.getTotalDurationMinutes(), entity.getTotalAmount(),
                entity.getAreaNote(), entity.getStatus(), entity.getCancelledAt(), entity.getCancelledBy(),
                entity.getCancellationReason(), entity.getCreatedAt(), entity.getCustomerArrivedAt(),
                entity.getStaffArrivedAt(), entity.getCustomerDelayReportedAt(), entity.getIncidentReportedAt(),
                entity.getIncidentNote(), entity.getIncidentExpectedArrivalAt(), entity.getIncidentCustomerResponse(),
                entity.getIncidentCustomerRespondedAt(), entity.getWorkStartedAt(), entity.getDelayMinutes(),
                entity.getDelayDiscountPercent(), entity.getDelayDiscountAmount(), entity.getServiceCompletedAt());
    }

    public Booking convertDTOToEntity() {
        Booking entity = new Booking();
        entity.setBookingId(bookingId);
        entity.setCustomerId(customerId);
        entity.setStaffId(staffId);
        entity.setOriginalStaffId(originalStaffId);
        entity.setHouseId(houseId);
        entity.setAddressSnapshot(addressSnapshot);
        entity.setLatitudeSnapshot(latitudeSnapshot);
        entity.setLongitudeSnapshot(longitudeSnapshot);
        entity.setAddressNoteSnapshot(addressNoteSnapshot);
        entity.setStartTime(startTime);
        entity.setEndTime(endTime);
        entity.setTotalDurationMinutes(totalDurationMinutes);
        entity.setTotalAmount(totalAmount);
        entity.setAreaNote(areaNote);
        entity.setStatus(status);
        entity.setCancelledAt(cancelledAt);
        entity.setCancelledBy(cancelledBy);
        entity.setCancellationReason(cancellationReason);
        entity.setCreatedAt(createdAt);
        entity.setCustomerArrivedAt(customerArrivedAt);
        entity.setStaffArrivedAt(staffArrivedAt);
        entity.setCustomerDelayReportedAt(customerDelayReportedAt);
        entity.setIncidentReportedAt(incidentReportedAt);
        entity.setIncidentNote(incidentNote);
        entity.setIncidentExpectedArrivalAt(incidentExpectedArrivalAt);
        entity.setIncidentCustomerResponse(incidentCustomerResponse);
        entity.setIncidentCustomerRespondedAt(incidentCustomerRespondedAt);
        entity.setWorkStartedAt(workStartedAt);
        entity.setDelayMinutes(delayMinutes);
        entity.setDelayDiscountPercent(delayDiscountPercent);
        entity.setDelayDiscountAmount(delayDiscountAmount);
        entity.setServiceCompletedAt(serviceCompletedAt);
        return entity;
    }
}
