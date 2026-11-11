package com.housekeeping.management.repository;

import com.housekeeping.management.entity.BookingImage;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BookingImageRepository extends JpaRepository<BookingImage, Long> {
    boolean existsByBookingId(Long bookingId);
    java.util.List<BookingImage> findAllByBookingIdOrderByUploadedAtAscImageIdAsc(Long bookingId);
}
