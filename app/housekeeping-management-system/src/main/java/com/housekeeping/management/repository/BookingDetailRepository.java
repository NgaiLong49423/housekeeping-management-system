package com.housekeeping.management.repository;


import com.housekeeping.management.entity.BookingDetail;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BookingDetailRepository extends JpaRepository<BookingDetail, Long> {
    java.util.List<BookingDetail> findAllByBookingIdOrderByBookingDetailId(Long bookingId);
}
