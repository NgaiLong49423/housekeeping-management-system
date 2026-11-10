package com.housekeeping.management.service.impl;

import com.housekeeping.management.service.AccountAuthorizationService;
import com.housekeeping.management.service.FeedbackService;
import com.housekeeping.management.service.BookingReservationService;

import com.housekeeping.management.repository.FeedbackRepository;
import com.housekeeping.management.domain.FeedbackTopic;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FeedbackServiceImpl implements FeedbackService {
    private final BookingReservationService bookings;
    private final FeedbackRepository feedback;
    private final AccountAuthorizationService authorization;

    public FeedbackServiceImpl(BookingReservationService bookings, FeedbackRepository feedback,
            AccountAuthorizationService authorization) {
        this.bookings = bookings;
        this.feedback = feedback;
        this.authorization = authorization;
    }

    @Transactional
    public void submit(Long bookingId, Long customerId, Integer rating, List<String> topics, String content) {
        validate(rating, topics, content);
        var booking = bookings.lockBookingForFeedback(bookingId)
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
        feedback.create(bookingId, customerId, rating, topics == null ? List.of() : topics, clean(content));
    }

    @Transactional
    public void update(Long bookingId, Long customerId, Integer rating, List<String> topics, String content) {
        validate(rating, topics, content);
        var booking = bookings.lockBookingForFeedback(bookingId)
                .orElseThrow(() -> new IllegalArgumentException("Lịch hẹn không tồn tại."));
        if (!booking.customerId().equals(customerId)) {
            throw new org.springframework.security.access.AccessDeniedException("Lịch hẹn không thuộc tài khoản của bạn.");
        }
        if (!"COMPLETED".equals(booking.status())) {
            throw new IllegalStateException("Chỉ có thể cập nhật phản hồi sau khi lịch hẹn hoàn tất.");
        }
        if (feedback.update(bookingId, customerId, rating, topics == null ? List.of() : topics, clean(content)) != 1) {
            throw new IllegalStateException("Phản hồi đã được Admin trả lời hoặc không thể chỉnh sửa.");
        }
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

    @Override
    public List<com.housekeeping.management.dto.response.PortalViews.FeedbackCard> findAll() {
        return feedback.findAll();
    }

    private static void validate(Integer rating, List<String> topics, String content) {
        if (rating == null || rating < 1 || rating > 5) throw new IllegalArgumentException("Điểm đánh giá phải từ 1 đến 5 sao.");
        List<String> selected = topics == null ? List.of() : topics;
        if (selected.size() > 6 || selected.stream().distinct().count() != selected.size()) {
            throw new IllegalArgumentException("Danh sách chủ đề không hợp lệ.");
        }
        selected.forEach(FeedbackTopic::fromCode);
        if (rating <= 3 && (content == null || content.isBlank())) {
            throw new IllegalArgumentException("Vui lòng nhập lời nhắn cho đánh giá từ 1 đến 3 sao.");
        }
        if (content != null && content.length() > 4000) throw new IllegalArgumentException("Lời nhắn tối đa 4000 ký tự.");
    }

    private static String clean(String content) {
        return content == null || content.isBlank() ? null : content.trim();
    }

}
