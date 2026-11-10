package com.housekeeping.management.service.impl;

import com.housekeeping.management.service.CustomerDelayReportService;

import com.housekeeping.management.repository.BookingPersistenceRepository;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CustomerDelayReportServiceImpl implements CustomerDelayReportService {
    private final BookingPersistenceRepository bookings;

    public CustomerDelayReportServiceImpl(BookingPersistenceRepository bookings) {
        this.bookings = bookings;
    }

    @Transactional
    public void reportStaffNotArrived(Long bookingId, Long customerId) {
        LocalDateTime reportedAt = LocalDateTime.now(ZoneOffset.ofHours(7)).withNano(0);
        if (bookings.reportCustomerDelay(bookingId, customerId, reportedAt) != 1) {
            throw new IllegalStateException("Chỉ Customer của lịch CONFIRMED mới báo Staff chưa đến sau giờ hẹn và trước khi Staff xác nhận đến.");
        }
    }
}
