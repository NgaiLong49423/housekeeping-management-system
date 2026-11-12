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
        var booking = bookings.lockBookingWorkflow(bookingId)
                .orElseThrow(() -> new IllegalArgumentException("Lịch hẹn không tồn tại."));
        if (!booking.customerId().equals(customerId)) {
            throw new org.springframework.security.access.AccessDeniedException("Lịch hẹn không thuộc tài khoản của bạn.");
        }
        if (!"CONFIRMED".equals(booking.status()) || booking.startTime().isAfter(reportedAt)
                || booking.serviceCompletedAt() != null
                || booking.customerDelayReportedAt() != null) {
            throw new IllegalStateException("Chỉ báo Staff chưa đến từ giờ hẹn đến trước khi dịch vụ hoàn tất.");
        }
        if (bookings.reportCustomerDelay(bookingId, customerId, reportedAt) != 1) {
            throw new IllegalStateException("Lịch vừa thay đổi hoặc đã có báo cáo. Hãy tải lại trang.");
        }
    }
}
