package com.housekeeping.management.service.impl;

import com.housekeeping.management.service.BookingArrivalService;
import com.housekeeping.management.service.BookingDelayCompensationSettingsService;

import com.housekeeping.management.domain.BookingDelayCompensation;
import com.housekeeping.management.repository.BookingPersistenceRepository;
import com.housekeeping.management.domain.BookingDelayCompensation.Tier;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BookingArrivalServiceImpl implements BookingArrivalService {
    private final BookingPersistenceRepository bookings;
    private final BookingDelayCompensationSettingsService compensationSettings;

    public BookingArrivalServiceImpl(BookingPersistenceRepository bookings,
            BookingDelayCompensationSettingsService compensationSettings) {
        this.bookings = bookings;
        this.compensationSettings = compensationSettings;
    }

    @Transactional
    public void confirm(Long bookingId, Long customerId) {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.ofHours(7)).withNano(0);
        var booking = bookings.lockBookingWorkflow(bookingId)
                .orElseThrow(() -> new IllegalStateException("Lịch hẹn không tồn tại."));
        if (!booking.customerId().equals(customerId)) throw new org.springframework.security.access.AccessDeniedException("Lịch hẹn không thuộc tài khoản của bạn.");
        if (!"CONFIRMED".equals(booking.status())) throw new IllegalStateException("Chỉ Booking CONFIRMED mới xác nhận giờ đến.");
        if (booking.customerDelayReportedAt() != null && booking.arrivalVerifiedAt() != null) return;
        if (booking.workStartedAt() != null && booking.customerDelayReportedAt() == null) {
            throw new IllegalStateException("Customer có thể xác nhận giờ đến trước khi dịch vụ bắt đầu.");
        }
        if (booking.customerArrivedAt() != null && booking.customerDelayReportedAt() == null) return;
        BookingDelayCompensation compensation = BookingDelayCompensation.calculate(
                booking.startTime(), now, booking.totalAmount(), compensationSettings.get());
        if (bookings.confirmCustomerArrival(bookingId, customerId, now, compensation.delayedMinutes(),
                java.math.BigDecimal.valueOf(compensation.discountPercent()), compensation.discountAmount()) != 1)
            throw new IllegalStateException("Chỉ Customer của Booking CONFIRMED mới ghi nhận Staff đã đến và mỗi Booking một lần.");
    }

    @Transactional
    public void confirmStaffArrival(Long bookingId, Long staffId) {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.ofHours(7)).withNano(0);
        var booking = bookings.lockBookingWorkflow(bookingId)
                .orElseThrow(() -> new IllegalStateException("Lịch hẹn không tồn tại."));
        if (bookings.confirmStaffArrival(bookingId, staffId, now, 0, java.math.BigDecimal.ZERO,
                java.math.BigDecimal.ZERO) != 1)
            throw new IllegalStateException("Chỉ nhân viên được phân công mới xác nhận đã đến cho lịch CONFIRMED và mỗi Booking một lần.");
    }
}
