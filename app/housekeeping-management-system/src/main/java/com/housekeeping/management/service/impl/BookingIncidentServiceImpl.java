package com.housekeeping.management.service.impl;

import com.housekeeping.management.service.BookingIncidentService;

import com.housekeeping.management.domain.BookingDelayCompensation;
import com.housekeeping.management.repository.BookingPersistenceRepository;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BookingIncidentServiceImpl implements BookingIncidentService {
    private static final int MAX_NOTE_LENGTH = 500;
    private final BookingPersistenceRepository bookings;

    public BookingIncidentServiceImpl(BookingPersistenceRepository bookings) {
        this.bookings = bookings;
    }

    @Transactional
    public void report(Long bookingId, Long staffId, String note, LocalDateTime expectedArrivalAt) {
        String normalizedNote = note == null ? "" : note.strip();
        if (normalizedNote.isEmpty() || normalizedNote.length() > MAX_NOTE_LENGTH) {
            throw new IllegalArgumentException("Nội dung sự cố phải có từ 1 đến 500 ký tự.");
        }
        BookingPersistenceRepository.BookingWorkflowState booking = bookings.lockBookingWorkflow(bookingId)
                .orElseThrow(() -> new IllegalArgumentException("Lịch hẹn không tồn tại."));
        if (!booking.staffId().equals(staffId)) {
            throw new IllegalArgumentException("Chỉ nhân viên được phân công mới có thể báo sự cố.");
        }
        LocalDateTime now = LocalDateTime.now(ZoneOffset.ofHours(7)).withNano(0);
        if (!"CONFIRMED".equals(booking.status()) || booking.workStartedAt() != null) {
            throw new IllegalStateException("Chỉ báo sự cố khi lịch còn CONFIRMED và công việc chưa bắt đầu.");
        }
        if (expectedArrivalAt == null || !expectedArrivalAt.isAfter(booking.startTime())
                || expectedArrivalAt.isBefore(now)
                || (booking.staffArrivedAt() != null && expectedArrivalAt.isBefore(booking.staffArrivedAt()))) {
            throw new IllegalArgumentException("Giờ dự kiến đến phải sau giờ hẹn, hiện tại và thời điểm Staff đã ghi nhận đến.");
        }
        if (bookings.reportStaffIncident(bookingId, staffId, normalizedNote, expectedArrivalAt, now) != 1) {
            throw new IllegalStateException("Lịch vừa thay đổi hoặc đã có báo cáo/khách phản hồi. Hãy tải lại trang.");
        }
    }

    @Transactional
    public void respond(Long bookingId, Long customerId, String response) {
        BookingPersistenceRepository.BookingWorkflowState booking = bookings.lockBookingWorkflow(bookingId)
                .orElseThrow(() -> new IllegalArgumentException("Lịch hẹn không tồn tại."));
        if (!booking.customerId().equals(customerId)) {
            throw new org.springframework.security.access.AccessDeniedException("Lịch hẹn không thuộc tài khoản của bạn.");
        }
        if (!"CONFIRMED".equals(booking.status()) || booking.incidentReportedAt() == null
                || booking.incidentExpectedArrivalAt() == null || booking.workStartedAt() != null
                || booking.incidentCustomerResponse() != null) {
            throw new IllegalStateException("Lịch không còn chờ phản hồi sự cố hoặc công việc đã bắt đầu.");
        }
        if (!"ACCEPTED".equals(response) && !"DECLINED".equals(response)) {
            throw new IllegalArgumentException("Phản hồi sự cố không hợp lệ.");
        }
        LocalDateTime now = LocalDateTime.now(ZoneOffset.ofHours(7)).withNano(0);
        BookingDelayCompensation compensation = booking.staffArrivedAt() == null
                ? new BookingDelayCompensation(0, 0, java.math.BigDecimal.ZERO.setScale(2))
                : new BookingDelayCompensation(booking.delayMinutes(), booking.delayDiscountPercent().intValueExact(),
                        booking.delayDiscountAmount());
        if (bookings.respondToIncident(bookingId, customerId, response, now,
                java.math.BigDecimal.valueOf(compensation.discountPercent()), compensation.discountAmount()) != 1) {
            throw new IllegalStateException("Lịch vừa thay đổi. Hãy tải lại trang trước khi phản hồi.");
        }
    }
}
