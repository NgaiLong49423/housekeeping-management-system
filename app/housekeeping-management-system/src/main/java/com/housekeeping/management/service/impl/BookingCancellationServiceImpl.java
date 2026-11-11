package com.housekeeping.management.service.impl;

import com.housekeeping.management.service.BookingCancellationService;

import com.housekeeping.management.repository.BookingPersistenceRepository;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BookingCancellationServiceImpl implements BookingCancellationService {
    private static final ZoneOffset BUSINESS_OFFSET = ZoneOffset.ofHours(7);
    private final BookingPersistenceRepository bookings;

    public BookingCancellationServiceImpl(BookingPersistenceRepository bookings) { this.bookings = bookings; }

    @Transactional
    public void cancelByCustomer(Long bookingId, Long customerId) {
        var booking = bookings.lockBookingWorkflow(bookingId)
                .orElseThrow(() -> new IllegalArgumentException("Lịch hẹn không tồn tại."));
        if (!booking.customerId().equals(customerId)) {
            throw new AccessDeniedException("Lịch hẹn không thuộc tài khoản của bạn.");
        }
        if (booking.workStartedAt() != null) {
            throw new IllegalStateException("Công việc đã bắt đầu nên không thể hủy lịch.");
        }
        var now = OffsetDateTime.now(BUSINESS_OFFSET).withNano(0).toLocalDateTime();
        boolean reportedStaffDelayCanCancel = booking.incidentReportedAt() != null && booking.workStartedAt() == null;
        boolean unreportedDelayCanCancel = BookingCancellationService.canCancelUnverifiedDelay(
                booking.status(), booking.startTime(), now, booking.customerArrivedAt(),
                booking.arrivalVerifiedAt(), booking.incidentReportedAt(), booking.workStartedAt());
        if (!reportedStaffDelayCanCancel && !unreportedDelayCanCancel && !BookingCancellationService.canCustomerCancel(
                booking.status(), booking.startTime(), now, booking.workStartedAt())) {
            throw new IllegalStateException("Chỉ có thể hủy lịch đã xác nhận trước giờ bắt đầu ít nhất 2 giờ.");
        }
        String reason = reportedStaffDelayCanCancel || unreportedDelayCanCancel
                ? "Khách hủy miễn phí do Staff đến trễ/chưa xác minh giờ đến."
                : "Khách hàng yêu cầu hủy lịch.";
        if (bookings.cancelByCustomer(bookingId, customerId, reason, now) != 1) {
            throw new IllegalStateException("Trạng thái lịch hẹn vừa thay đổi. Hãy tải lại trang.");
        }
    }

}
