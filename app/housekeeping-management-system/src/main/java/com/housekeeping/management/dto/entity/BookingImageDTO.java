package com.housekeeping.management.dto.entity;

import com.housekeeping.management.entity.BookingImage;
import java.time.LocalDateTime;

public record BookingImageDTO(Long imageId, Long bookingId, String imageUrl, LocalDateTime uploadedAt) {
    public static BookingImageDTO convertEntityToDTO(BookingImage entity) {
        if (entity == null) return null;
        return new BookingImageDTO(entity.getImageId(), entity.getBookingId(), entity.getImageUrl(), entity.getUploadedAt());
    }

    public BookingImage convertDTOToEntity() {
        BookingImage entity = new BookingImage();
        entity.setImageId(imageId);
        entity.setBookingId(bookingId);
        entity.setImageUrl(imageUrl);
        entity.setUploadedAt(uploadedAt);
        return entity;
    }
}
