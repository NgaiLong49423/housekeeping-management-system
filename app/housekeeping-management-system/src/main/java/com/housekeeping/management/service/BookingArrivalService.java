package com.housekeeping.management.service;

import com.housekeeping.management.domain.BookingDelayCompensation;
import com.housekeeping.management.repository.BookingPersistenceRepository;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BookingArrivalService {
    private final BookingPersistenceRepository bookings;
    private final int earlyConfirmationMinutes;

    public BookingArrivalService(BookingPersistenceRepository bookings,
            @Value("${app.booking.arrival-early-confirmation-minutes:0}") int earlyConfirmationMinutes) {
        this.bookings = bookings;
        if (earlyConfirmationMinutes < 0) throw new IllegalArgumentException("Arrival confirmation window cannot be negative.");
        this.earlyConfirmationMinutes = earlyConfirmationMinutes;
    }

    @Transactional
    public void confirm(Long bookingId, Long customerId) {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.ofHours(7)).withNano(0);
        if (bookings.confirmCustomerArrival(bookingId, customerId, now, now.plusMinutes(earlyConfirmationMinutes)) != 1)
            throw new IllegalStateException("Chỉ Customer của Booking CONFIRMED mới ghi nhận Staff đã đến trong thời gian cho phép và mỗi Booking một lần.");
    }

    @Transactional
    public void confirmStaffArrival(Long bookingId, Long staffId) {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.ofHours(7)).withNano(0);
        var booking = bookings.lockBookingWorkflow(bookingId)
                .orElseThrow(() -> new IllegalStateException("Lịch hẹn không tồn tại."));
        BookingDelayCompensation compensation = BookingDelayCompensation.calculate(
                booking.startTime(), now, booking.totalAmount());
        if (bookings.confirmStaffArrival(bookingId, staffId, now, now.plusMinutes(earlyConfirmationMinutes),
                compensation.delayedMinutes(), java.math.BigDecimal.valueOf(compensation.discountPercent()),
                compensation.discountAmount()) != 1)
            throw new IllegalStateException("Chỉ nhân viên được phân công mới xác nhận đã đến cho lịch CONFIRMED trong thời gian cho phép và mỗi Booking một lần.");
    }
}
