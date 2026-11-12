package com.housekeeping.management.dto.entity;

import com.housekeeping.management.entity.BookingDetail;
import java.math.BigDecimal;

public record BookingDetailDTO(Long bookingDetailId, Long bookingId, Long serviceId, String serviceNameSnapshot,
        BigDecimal unitPrice, Integer durationMinutes) {
    public static BookingDetailDTO convertEntityToDTO(BookingDetail entity) {
        if (entity == null) return null;
        return new BookingDetailDTO(entity.getBookingDetailId(), entity.getBookingId(), entity.getServiceId(),
                entity.getServiceNameSnapshot(), entity.getUnitPrice(), entity.getDurationMinutes());
    }

    public BookingDetail convertDTOToEntity() {
        BookingDetail entity = new BookingDetail();
        entity.setBookingDetailId(bookingDetailId);
        entity.setBookingId(bookingId);
        entity.setServiceId(serviceId);
        entity.setServiceNameSnapshot(serviceNameSnapshot);
        entity.setUnitPrice(unitPrice);
        entity.setDurationMinutes(durationMinutes);
        return entity;
    }
}
