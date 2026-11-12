package com.housekeeping.management.service.impl;

import com.housekeeping.management.service.CustomerDelayReportService;
import com.housekeeping.management.service.BookingCancellationService;

import com.housekeeping.management.repository.BookingPersistenceRepository;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CustomerDelayReportServiceImpl implements CustomerDelayReportService {
    private final BookingPersistenceRepository bookings;
    private final BookingCancellationService cancellation;

    public CustomerDelayReportServiceImpl(BookingPersistenceRepository bookings, BookingCancellationService cancellation) {
        this.bookings = bookings;
        this.cancellation = cancellation;
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
                || booking.workStartedAt() != null || booking.serviceCompletedAt() != null
                || "ADMIN".equals(booking.arrivalVerificationSource())
                || booking.customerDelayReportedAt() != null) {
            throw new IllegalStateException("Chỉ báo Staff chưa đến từ giờ hẹn đến trước khi công việc bắt đầu.");
        }
        if (bookings.reportCustomerDelay(bookingId, customerId, reportedAt) != 1) {
            throw new IllegalStateException("Lịch vừa thay đổi hoặc đã có báo cáo. Hãy tải lại trang.");
        }
    }

    @Transactional
    public void respondAfterAdminReview(Long bookingId, Long customerId, String response) {
        if (!"WAIT".equals(response) && !"CANCEL".equals(response)) {
            throw new IllegalArgumentException("Phản hồi không hợp lệ.");
        }
        var booking = bookings.lockEntity(bookingId)
                .orElseThrow(() -> new IllegalArgumentException("Lịch hẹn không tồn tại."));
        if (!booking.getCustomerId().equals(customerId)) {
            throw new org.springframework.security.access.AccessDeniedException("Lịch hẹn không thuộc tài khoản của bạn.");
        }
        if (booking.getStatus() != com.housekeeping.management.enums.BookingStatus.CONFIRMED
                || booking.getCustomerDelayReportedAt() == null || booking.getWorkStartedAt() != null
                || booking.getServiceCompletedAt() != null) {
            throw new IllegalStateException("Lịch không còn chờ bạn chọn đợi hoặc hủy.");
        }
        boolean adminVerifiedNoShow = booking.getAdminNoShowVerifiedAt() != null;
        boolean adminVerifiedLateArrival = "ADMIN".equals(booking.getArrivalVerificationSource())
                && booking.getArrivalVerifiedAt() != null
                && booking.getArrivalVerifiedAt().isAfter(booking.getStartTime());
        if (!adminVerifiedNoShow && !adminVerifiedLateArrival) {
            throw new IllegalStateException("Admin cần xác minh nhân viên chưa tới hoặc xác nhận giờ đến trễ trước khi bạn chọn.");
        }

        if ("CANCEL".equals(response)) {
            cancellation.cancelByCustomer(bookingId, customerId);
            return;
        }

        LocalDateTime now = LocalDateTime.now(ZoneOffset.ofHours(7)).withNano(0);
        if (bookings.recordCustomerWaitAfterDelayReview(bookingId, customerId, now) != 1) {
            throw new IllegalStateException("Lịch vừa thay đổi hoặc bạn đã chọn trước đó. Hãy tải lại trang.");
        }
    }
}
