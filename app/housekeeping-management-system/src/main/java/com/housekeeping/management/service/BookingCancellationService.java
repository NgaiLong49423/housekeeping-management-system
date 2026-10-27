package com.housekeeping.management.service;

import com.housekeeping.management.repository.BookingPersistenceRepository;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BookingCancellationService {
    private static final ZoneOffset BUSINESS_OFFSET = ZoneOffset.ofHours(7);
    private final BookingPersistenceRepository bookings;

    public BookingCancellationService(BookingPersistenceRepository bookings) { this.bookings = bookings; }

    @Transactional
    public void cancelByCustomer(Long bookingId, Long customerId) {
        var booking = bookings.lockBooking(bookingId)
                .orElseThrow(() -> new IllegalArgumentException("Lịch hẹn không tồn tại."));
        if (!booking.customerId().equals(customerId)) {
            throw new AccessDeniedException("Lịch hẹn không thuộc tài khoản của bạn.");
        }
        var now = OffsetDateTime.now(BUSINESS_OFFSET).withNano(0).toLocalDateTime();
        if (!canCustomerCancel(booking.status(), booking.startTime(), now)) {
            throw new IllegalStateException("Chỉ có thể hủy lịch đã xác nhận trước giờ bắt đầu ít nhất 2 giờ.");
        }
        if (bookings.cancel(bookingId, customerId, "Khách hàng yêu cầu hủy lịch.", now) != 1) {
            throw new IllegalStateException("Trạng thái lịch hẹn vừa thay đổi. Hãy tải lại trang.");
        }
    }

    static boolean canCustomerCancel(String status, LocalDateTime startTime, LocalDateTime now) {
        return "CONFIRMED".equals(status) && !now.isAfter(startTime.minusHours(2));
    }
}
