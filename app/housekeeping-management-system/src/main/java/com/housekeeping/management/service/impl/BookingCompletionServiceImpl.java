package com.housekeeping.management.service.impl;

import com.housekeeping.management.service.BookingCompletionService;

import com.housekeeping.management.repository.BookingImageRepository;
import com.housekeeping.management.repository.BookingPersistenceRepository;
import com.housekeeping.management.domain.BookingDelayCompensation;
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
        if (booking.staffArrivedAt() == null && booking.arrivalVerifiedAt() == null) {
            throw new IllegalStateException("Hãy xác nhận đã đến trước khi bắt đầu công việc.");
        }
        if (booking.customerDelayReportedAt() != null && booking.arrivalVerifiedAt() == null) {
            throw new IllegalStateException("Customer đã báo Staff chưa đến. Cần Customer xác nhận giờ có mặt hoặc Admin xác minh trước khi bắt đầu.");
        }
        java.time.LocalDateTime effectiveArrival = booking.arrivalVerifiedAt() == null
                ? booking.staffArrivedAt() : booking.arrivalVerifiedAt();
        boolean lateBeyondGrace = effectiveArrival.isAfter(booking.startTime());
        if (lateBeyondGrace && booking.incidentReportedAt() == null && booking.arrivalVerifiedAt() == null) {
            throw new IllegalStateException("Staff đến trễ cần gửi ETA cụ thể và chờ Customer đồng ý trước khi bắt đầu.");
        }
        if (booking.incidentReportedAt() != null && !"ACCEPTED".equals(booking.incidentCustomerResponse())) {
            throw new IllegalStateException("Cần Customer đồng ý giờ đến trễ trước khi bắt đầu công việc.");
        }
        if ("ACCEPTED".equals(booking.incidentCustomerResponse()) && booking.incidentExpectedArrivalAt() != null
                && effectiveArrival.isAfter(booking.incidentExpectedArrivalAt())) {
            throw new IllegalStateException("Staff đã lỡ ETA được đồng ý. Cần gửi ETA mới và Customer đồng ý lại.");
        }
        if (bookings.startWork(bookingId, staffId, now, lateBeyondGrace) != 1) {
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
        if (booking.customerDelayReportedAt() != null && booking.arrivalVerifiedAt() == null) {
            throw new IllegalStateException("Customer đã báo Staff chưa đến. Cần Customer xác nhận hoặc Admin xác minh trước khi hoàn thành.");
        }
        if (!bookingImages.existsByBookingId(bookingId)) {
            throw new IllegalStateException("Cần tải ít nhất một ảnh minh chứng trước khi xác nhận hoàn thành.");
        }
        var completedAt = java.time.LocalDateTime.now(java.time.ZoneOffset.ofHours(7)).withNano(0);
        if (bookings.completeWork(bookingId, staffId, completedAt) != 1) {
            throw new IllegalStateException("Trạng thái lịch hẹn vừa thay đổi. Hãy tải lại trang.");
        }
    }
}
