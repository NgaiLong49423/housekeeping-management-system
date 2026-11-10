package com.housekeeping.management.service.impl;

import com.housekeeping.management.service.BookingCompletionService;

import com.housekeeping.management.repository.BookingImageRepository;
import com.housekeeping.management.repository.BookingPersistenceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BookingCompletionServiceImpl implements BookingCompletionService {
    private final BookingPersistenceRepository bookings;
    private final BookingImageRepository bookingImages;

    public BookingCompletionServiceImpl(BookingPersistenceRepository bookings, BookingImageRepository bookingImages) {
        this.bookings = bookings;
        this.bookingImages = bookingImages;
    }

    @Transactional
    public void startBooking(Long bookingId, Long staffId) {
        var booking = bookings.lockBookingWorkflow(bookingId)
                .orElseThrow(() -> new IllegalArgumentException("Lịch hẹn không tồn tại."));
        if (!booking.staffId().equals(staffId)) {
            throw new IllegalArgumentException("Chỉ nhân viên được phân công mới có thể bắt đầu công việc.");
        }
        if (!"CONFIRMED".equals(booking.status()) || booking.workStartedAt() != null) {
            throw new IllegalStateException("Lịch không còn ở trạng thái có thể bắt đầu.");
        }
        var now = java.time.LocalDateTime.now(java.time.ZoneOffset.ofHours(7)).withNano(0);
        if (booking.staffArrivedAt() == null) {
            throw new IllegalStateException("Hãy xác nhận đã đến trước khi bắt đầu công việc.");
        }
        if (booking.incidentReportedAt() != null && !"ACCEPTED".equals(booking.incidentCustomerResponse())) {
            throw new IllegalStateException("Cần Customer đồng ý giờ đến trễ trước khi bắt đầu công việc.");
        }
        if (bookings.startWork(bookingId, staffId, now) != 1) {
            throw new IllegalStateException("Lịch vừa thay đổi hoặc chưa đủ điều kiện bắt đầu. Hãy tải lại trang.");
        }
    }

    @Transactional
    public void completeBooking(Long bookingId, Long staffId) {
        BookingPersistenceRepository.BookingWorkflowState booking = bookings.lockBookingWorkflow(bookingId)
                .orElseThrow(() -> new IllegalArgumentException("Lịch hẹn không tồn tại."));
        if (!booking.staffId().equals(staffId)) {
            throw new IllegalArgumentException("Chỉ nhân viên được phân công mới có thể xác nhận hoàn thành.");
        }
        if (!"CONFIRMED".equals(booking.status())) {
            throw new IllegalStateException("Chỉ lịch đã xác nhận mới chuyển sang chờ thanh toán.");
        }
        if (booking.workStartedAt() == null) {
            throw new IllegalStateException("Cần xác nhận bắt đầu công việc trước khi hoàn thành.");
        }
        if (!bookingImages.hasEvidence(bookingId)) {
            throw new IllegalStateException("Cần tải ít nhất một ảnh minh chứng trước khi xác nhận hoàn thành.");
        }
        if (bookings.changeStatus(bookingId, "CONFIRMED", "AWAITING_PAYMENT") != 1) {
            throw new IllegalStateException("Trạng thái lịch hẹn vừa thay đổi. Hãy tải lại trang.");
        }
    }
}
