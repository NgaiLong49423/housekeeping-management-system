package com.housekeeping.management.service;

import com.housekeeping.management.repository.BookingPersistenceRepository;
import com.housekeeping.management.repository.FeedbackRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FeedbackService {
    private final BookingPersistenceRepository bookings;
    private final FeedbackRepository feedback;
    private final AccountAuthorizationService authorization;

    public FeedbackService(BookingPersistenceRepository bookings, FeedbackRepository feedback,
            AccountAuthorizationService authorization) {
        this.bookings = bookings;
        this.feedback = feedback;
        this.authorization = authorization;
    }

    @Transactional
    public void submit(Long bookingId, Long customerId, String content) {
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("Nội dung phản hồi không được để trống.");
        }
        var booking = bookings.lockBooking(bookingId)
                .orElseThrow(() -> new IllegalArgumentException("Lịch hẹn không tồn tại."));
        if (!booking.customerId().equals(customerId)) {
            throw new org.springframework.security.access.AccessDeniedException("Lịch hẹn không thuộc tài khoản của bạn.");
        }
        if (!"COMPLETED".equals(booking.status())) {
            throw new IllegalStateException("Chỉ có thể gửi phản hồi sau khi thanh toán thành công.");
        }
        if (feedback.existsForBooking(bookingId)) {
            throw new IllegalStateException("Lịch hẹn này đã có phản hồi.");
        }
        feedback.create(bookingId, customerId, content.trim());
    }

    @Transactional
    public void reply(Long feedbackId, Long adminId, String reply) {
        authorization.requireActiveAdmin(adminId);
        if (reply == null || reply.isBlank()) {
            throw new IllegalArgumentException("Nội dung trả lời không được để trống.");
        }
        if (feedback.reply(feedbackId, reply.trim()) != 1) {
            throw new IllegalArgumentException("Phản hồi không tồn tại.");
        }
    }

    @Transactional
    public void resolve(Long feedbackId, Long adminId) {
        authorization.requireActiveAdmin(adminId);
        if (feedback.resolve(feedbackId) != 1) {
            throw new IllegalStateException("Phản hồi không tồn tại hoặc đã được giải quyết.");
        }
    }

}
