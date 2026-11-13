package com.housekeeping.management.dto.entity;

import com.housekeeping.management.entity.BookingDetail;
import java.math.BigDecimal;

public record BookingDetailDTO(Long bookingDetailId, Long bookingId, Long serviceId, String serviceNameSnapshot,
        BigDecimal unitPrice, BigDecimal additionalStaffPriceSnapshot, Integer staffCountSnapshot, Integer durationMinutes) {
    public BookingDetailDTO(Long bookingDetailId, Long bookingId, Long serviceId, String serviceNameSnapshot,
            BigDecimal unitPrice, Integer durationMinutes) {
        this(bookingDetailId, bookingId, serviceId, serviceNameSnapshot, unitPrice, BigDecimal.ZERO, 1, durationMinutes);
    }

    public static BookingDetailDTO convertEntityToDTO(BookingDetail entity) {
        if (entity == null) return null;
        return new BookingDetailDTO(entity.getBookingDetailId(), entity.getBookingId(), entity.getServiceId(),
                entity.getServiceNameSnapshot(), entity.getUnitPrice(), entity.getAdditionalStaffPriceSnapshot(),
                entity.getStaffCountSnapshot(), entity.getDurationMinutes());
    }

    public BookingDetail convertDTOToEntity() {
        BookingDetail entity = new BookingDetail();
        entity.setBookingDetailId(bookingDetailId);
        entity.setBookingId(bookingId);
        entity.setServiceId(serviceId);
        entity.setServiceNameSnapshot(serviceNameSnapshot);
        entity.setUnitPrice(unitPrice);
        entity.setAdditionalStaffPriceSnapshot(additionalStaffPriceSnapshot == null ? BigDecimal.ZERO : additionalStaffPriceSnapshot);
        entity.setStaffCountSnapshot(staffCountSnapshot == null ? 1 : staffCountSnapshot);
        entity.setDurationMinutes(durationMinutes);
        return entity;
    }
}
