package com.housekeeping.management.dto.response;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/** View models deliberately omit emails, password hashes, and storage paths. */
public final class PortalViews {
    private PortalViews() { }
    public static String time(OffsetDateTime value) {
        return value.withOffsetSameInstant(ZoneOffset.ofHours(7)).format(DateTimeFormatter.ofPattern("HH:mm · dd/MM/yyyy"));
    }
    public record ServiceCard(Long id, String name, String description, int duration, BigDecimal price,
            String imageUrl, boolean active) {
        public ServiceCard(Long id, String name, String description, int duration, BigDecimal price, boolean active) {
            this(id, name, description, duration, price, null, active);
        }
    }
    public record StaffCard(Long id, String name, String phone, boolean favorite, String staffType, boolean active,
            String avatarUrl, String bio) { }
    public record ScheduleCard(Long id, Long staffId, String staffName, OffsetDateTime start, OffsetDateTime end, boolean active) {
        public ScheduleCard(Long id, Long staffId, String staffName, OffsetDateTime start, OffsetDateTime end) { this(id, staffId, staffName, start, end, true); }
        public String getStartLabel() {
            return start.withOffsetSameInstant(ZoneOffset.ofHours(7))
                    .format(DateTimeFormatter.ofPattern("EEEE, dd/MM/yyyy · HH:mm", Locale.forLanguageTag("vi-VN")));
        }
        public String getEndLabel() { return time(end); }
        public String getStartInput() { return start.withOffsetSameInstant(ZoneOffset.ofHours(7)).toLocalDateTime().toString(); }
        public String getEndInput() { return end.withOffsetSameInstant(ZoneOffset.ofHours(7)).toLocalDateTime().toString(); }
    }
    public record BookingCard(Long id, Long customerId, Long staffId, String customerName, String staffName,
            OffsetDateTime start, OffsetDateTime end, String status, BigDecimal amount,
            String paymentStatus, String method, String feedbackContent, String adminReply, String feedbackStatus,
            String addressSnapshot, String addressNoteSnapshot, String areaNote, String cancellationReason, OffsetDateTime customerArrivedAt) {
        public BookingCard(Long id, Long customerId, Long staffId, String customerName, String staffName,
                OffsetDateTime start, OffsetDateTime end, String status, BigDecimal amount,
                String paymentStatus, String method, String feedbackContent, String adminReply, String feedbackStatus) {
            this(id, customerId, staffId, customerName, staffName, start, end, status, amount,
                    paymentStatus, method, feedbackContent, adminReply, feedbackStatus,
                    null, null, null, null, null);
        }
        public String getStartLabel() { return time(start); }
        public String getEndLabel() { return time(end); }
        public String getArrivalLabel() { return customerArrivedAt == null ? null : time(customerArrivedAt); }
        public String getStatusLabel() {
            return switch (status) {
                case "CONFIRMED" -> "Đã xác nhận";
                case "AWAITING_PAYMENT" -> "Chờ thanh toán";
                case "COMPLETED" -> "Đã hoàn thành";
                case "CANCELLED" -> "Đã hủy";
                default -> "Không xác định";
            };
        }
        public String getStatusClass() {
            return switch (status) {
                case "CONFIRMED" -> "confirmed";
                case "AWAITING_PAYMENT" -> "unpaid";
                case "COMPLETED" -> "completed";
                case "CANCELLED" -> "cancelled";
                default -> "cancelled";
            };
        }
        public boolean isCancellable() { return status.equals("CONFIRMED") && !OffsetDateTime.now().isAfter(start.minusHours(2)); }
    }
    public record EvidenceCard(Long id, String uploadedAt) { }
    public record FeedbackCard(Long id, Long bookingId, String customerName, String content,
            String adminReply, String status) { }
    public record LeaveCard(Long id, Long staffId, String staffName, LocalDate startDate,
            LocalDate endDate, String reason, String status, String createdAt, List<Long> conflictingBookingIds) {
        public LeaveCard(Long id, Long staffId, String staffName, LocalDate startDate, LocalDate endDate,
                String reason, String status, String createdAt) { this(id,staffId,staffName,startDate,endDate,reason,status,createdAt,List.of()); }
    }
    public record DetailCard(Long id, String name, int sequence, int duration, BigDecimal price, List<EvidenceCard> evidence) { }
}
