package com.housekeeping.management.dto.entity;

import com.housekeeping.management.entity.BookingDelayCompensationTier;

public record BookingDelayCompensationTierDTO(Integer tierId, Integer minDelayMinutes,
        Integer maxDelayMinutes, Integer discountPercent) {
    public static BookingDelayCompensationTierDTO convertEntityToDTO(BookingDelayCompensationTier entity) {
        if (entity == null) return null;
        return new BookingDelayCompensationTierDTO(entity.getTierId(), entity.getMinDelayMinutes(),
                entity.getMaxDelayMinutes(), entity.getDiscountPercent());
    }

    public BookingDelayCompensationTier convertDTOToEntity() {
        BookingDelayCompensationTier entity = new BookingDelayCompensationTier();
        entity.setTierId(tierId);
        entity.setMinDelayMinutes(minDelayMinutes);
        entity.setMaxDelayMinutes(maxDelayMinutes);
        entity.setDiscountPercent(discountPercent);
        return entity;
    }
}
