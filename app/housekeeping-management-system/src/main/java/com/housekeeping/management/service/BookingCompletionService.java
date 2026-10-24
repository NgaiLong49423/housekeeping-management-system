package com.housekeeping.management.service;

import com.housekeeping.management.repository.BookingPersistenceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BookingCompletionService {
    private final BookingPersistenceRepository bookings;

    public BookingCompletionService(BookingPersistenceRepository bookings) {
        this.bookings = bookings;
    }

    @Transactional
    public void completeBooking(Long bookingId, Long staffId) {
        BookingPersistenceRepository.BookingState booking = bookings.lockBooking(bookingId)
                .orElseThrow(() -> new IllegalArgumentException("Lịch hẹn không tồn tại."));
        if (!booking.staffId().equals(staffId)) {
            throw new IllegalArgumentException("Chỉ nhân viên được phân công mới có thể xác nhận hoàn thành.");
        }
        if (!"CONFIRMED".equals(booking.status())) {
            throw new IllegalStateException("Chỉ lịch đã xác nhận mới chuyển sang chờ thanh toán.");
        }
        if (bookings.changeStatus(bookingId, "CONFIRMED", "AWAITING_PAYMENT") != 1) {
            throw new IllegalStateException("Trạng thái lịch hẹn vừa thay đổi. Hãy tải lại trang.");
        }
    }
}
