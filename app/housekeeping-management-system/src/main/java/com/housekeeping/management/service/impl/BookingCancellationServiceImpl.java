package com.housekeeping.management.service.impl;

import com.housekeeping.management.entity.Booking;
import com.housekeeping.management.entity.CancellationLog;
import com.housekeeping.management.enums.BookingStatus;
import com.housekeeping.management.repository.BookingPersistenceRepository;
import com.housekeeping.management.repository.CancellationLogRepository;
import com.housekeeping.management.service.BookingCancellationService;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BookingCancellationServiceImpl implements BookingCancellationService {
    private static final ZoneOffset BUSINESS_OFFSET = ZoneOffset.ofHours(7);
    private static final BigDecimal ZERO = BigDecimal.ZERO;

    private final BookingPersistenceRepository bookings;
    private final CancellationLogRepository cancellationLogs;

    public BookingCancellationServiceImpl(BookingPersistenceRepository bookings,
            CancellationLogRepository cancellationLogs) {
        this.bookings = bookings;
        this.cancellationLogs = cancellationLogs;
    }

    @Override
    @Transactional
    public void cancelByCustomer(Long bookingId, Long customerId) {
        Booking booking = bookings.lockEntity(bookingId)
                .orElseThrow(() -> new IllegalArgumentException("Lịch hẹn không tồn tại."));
        if (!booking.getCustomerId().equals(customerId)) {
            throw new AccessDeniedException("Lịch hẹn không thuộc tài khoản của bạn.");
        }
        if (booking.getStatus() != BookingStatus.CONFIRMED) {
            throw new IllegalStateException("Chỉ có thể hủy lịch đang được xác nhận.");
        }
        if (booking.getWorkStartedAt() != null) {
            throw new IllegalStateException("Công việc đã bắt đầu nên không thể hủy lịch.");
        }

        LocalDateTime now = LocalDateTime.now(BUSINESS_OFFSET).withNano(0);
        boolean hasStaffDelayReport = booking.getIncidentReportedAt() != null
                && booking.getIncidentExpectedArrivalAt() != null
                && booking.getIncidentExpectedArrivalAt().isAfter(booking.getStartTime());
        boolean adminVerifiedNoShow = booking.getAdminNoShowVerifiedAt() != null;
        boolean adminVerifiedLateArrival = booking.getCustomerDelayReportedAt() != null
                && "ADMIN".equals(booking.getArrivalVerificationSource())
                && booking.getArrivalVerifiedAt() != null
                && booking.getArrivalVerifiedAt().isAfter(booking.getStartTime());
        boolean staffDelayAllowsCancellation = hasStaffDelayReport || adminVerifiedNoShow || adminVerifiedLateArrival;

        if (!staffDelayAllowsCancellation && !BookingCancellationService.canCustomerCancel(
                booking.getStatus().name(), booking.getStartTime(), now, booking.getWorkStartedAt())) {
            throw new IllegalStateException("Lịch thường chỉ được hủy khi còn ít nhất 2 giờ trước giờ bắt đầu.");
        }

        String reasonCode;
        String reason;
        boolean workerFault;
        if (adminVerifiedNoShow) {
            reasonCode = "STAFF_NO_SHOW";
            reason = "Khách hủy vì Admin đã xác minh nhân viên chưa tới.";
            workerFault = true;
        } else if (hasStaffDelayReport || adminVerifiedLateArrival) {
            reasonCode = "STAFF_DELAY";
            reason = "Khách hủy do nhân viên đến trễ.";
            workerFault = true;
        } else {
            reasonCode = "CUSTOMER_REQUEST";
            reason = "Khách hàng yêu cầu hủy lịch.";
            workerFault = false;
        }

        booking.setStatus(BookingStatus.CANCELLED);
        booking.setCancellationFee(ZERO);
        booking.setRefundAmount(ZERO);
        booking.setDelayMinutes(0);
        booking.setDelayDiscountPercent(ZERO);
        booking.setDelayDiscountAmount(ZERO);
        booking.setCancelledAt(now);
        booking.setCancelledBy(customerId);
        booking.setCancellationReason(reason);
        bookings.save(booking);

        cancellationLogs.save(CancellationLog.builder()
                .bookingId(booking.getBookingId())
                .customerId(booking.getCustomerId())
                .workerId(booking.getStaffId())
                .cancelledByType("CUSTOMER")
                .reasonCode(reasonCode)
                .reasonNote(reason)
                .bookingTotalAmount(booking.getTotalAmount())
                .cancellationFee(ZERO)
                .workerCompensation(ZERO)
                .platformFee(ZERO)
                .refundAmount(ZERO)
                .deltaMinutesToStart(Duration.between(now, booking.getStartTime()).toMinutes())
                .isWorkerFault(workerFault)
                .createdAt(now)
                .build());
    }
}
