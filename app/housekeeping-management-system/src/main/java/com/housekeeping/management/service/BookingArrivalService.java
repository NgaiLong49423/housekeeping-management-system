package com.housekeeping.management.service;

import com.housekeeping.management.repository.BookingPersistenceRepository;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BookingArrivalService {
    private final BookingPersistenceRepository bookings;
    public BookingArrivalService(BookingPersistenceRepository bookings) { this.bookings = bookings; }

    @Transactional
    public void confirm(Long bookingId, Long customerId) {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.ofHours(7)).withNano(0);
        if (bookings.confirmCustomerArrival(bookingId, customerId, now) != 1)
            throw new IllegalStateException("Chỉ Customer của Booking CONFIRMED mới ghi nhận Staff đã đến, kể từ giờ bắt đầu và mỗi Booking một lần.");
    }
}
