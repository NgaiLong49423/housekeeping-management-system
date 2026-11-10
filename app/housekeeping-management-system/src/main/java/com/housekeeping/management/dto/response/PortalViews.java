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
            String addressSnapshot, String addressNoteSnapshot, String areaNote, String cancellationReason, OffsetDateTime customerArrivedAt,
            String customerPhone, OffsetDateTime staffArrivedAt, OffsetDateTime incidentReportedAt, String incidentNote,
            OffsetDateTime incidentExpectedArrivalAt, String incidentCustomerResponse, OffsetDateTime incidentCustomerRespondedAt,
            OffsetDateTime workStartedAt, Integer delayMinutes, BigDecimal delayDiscountPercent, BigDecimal delayDiscountAmount,
            Integer feedbackRating, List<String> feedbackTopics, List<String> feedbackTopicCodes,
            OffsetDateTime customerDelayReportedAt) {
        public BookingCard(Long id, Long customerId, Long staffId, String customerName, String staffName,
                OffsetDateTime start, OffsetDateTime end, String status, BigDecimal amount,
                String paymentStatus, String method, String feedbackContent, String adminReply, String feedbackStatus,
                String addressSnapshot, String addressNoteSnapshot, String areaNote, String cancellationReason,
                OffsetDateTime customerArrivedAt, String customerPhone, OffsetDateTime staffArrivedAt,
                OffsetDateTime incidentReportedAt, String incidentNote) {
            this(id, customerId, staffId, customerName, staffName, start, end, status, amount, paymentStatus, method,
                    feedbackContent, adminReply, feedbackStatus, addressSnapshot, addressNoteSnapshot, areaNote,
                    cancellationReason, customerArrivedAt, customerPhone, staffArrivedAt, incidentReportedAt,
                    incidentNote, null, null, null, null, null, BigDecimal.ZERO, BigDecimal.ZERO, null, List.of(), List.of(), null);
        }
        public BookingCard(Long id, Long customerId, Long staffId, String customerName, String staffName,
                OffsetDateTime start, OffsetDateTime end, String status, BigDecimal amount,
                String paymentStatus, String method, String feedbackContent, String adminReply, String feedbackStatus) {
            this(id, customerId, staffId, customerName, staffName, start, end, status, amount,
                    paymentStatus, method, feedbackContent, adminReply, feedbackStatus,
                    null, null, null, null, null, null, null, null, null,
                    null, null, null, null, null, BigDecimal.ZERO, BigDecimal.ZERO, null, List.of(), List.of(), null);
        }
        public BookingCard(Long id, Long customerId, Long staffId, String customerName, String staffName,
                OffsetDateTime start, OffsetDateTime end, String status, BigDecimal amount,
                String paymentStatus, String method, String feedbackContent, String adminReply, String feedbackStatus,
                String addressSnapshot, String addressNoteSnapshot, String areaNote, String cancellationReason,
                OffsetDateTime customerArrivedAt, String customerPhone, OffsetDateTime staffArrivedAt,
                OffsetDateTime incidentReportedAt, String incidentNote, OffsetDateTime incidentExpectedArrivalAt,
                String incidentCustomerResponse, OffsetDateTime incidentCustomerRespondedAt, OffsetDateTime workStartedAt,
                Integer delayMinutes, BigDecimal delayDiscountPercent, BigDecimal delayDiscountAmount) {
            this(id, customerId, staffId, customerName, staffName, start, end, status, amount,
                    paymentStatus, method, feedbackContent, adminReply, feedbackStatus, addressSnapshot,
                    addressNoteSnapshot, areaNote, cancellationReason, customerArrivedAt, customerPhone,
                    staffArrivedAt, incidentReportedAt, incidentNote, incidentExpectedArrivalAt,
                    incidentCustomerResponse, incidentCustomerRespondedAt, workStartedAt, delayMinutes,
                    delayDiscountPercent, delayDiscountAmount, null, List.of(), List.of(), null);
        }
        public String getStartLabel() { return time(start); }
        public String getEndLabel() { return time(end); }
        public String getArrivalLabel() { return customerArrivedAt == null ? null : time(customerArrivedAt); }
        public String getStaffArrivalLabel() { return staffArrivedAt == null ? null : time(staffArrivedAt); }
        public String getIncidentReportedLabel() { return incidentReportedAt == null ? null : time(incidentReportedAt); }
        public String getCustomerDelayReportedLabel() { return customerDelayReportedAt == null ? null : time(customerDelayReportedAt); }
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
        public BigDecimal getPayableAmount() { return amount.subtract(delayDiscountAmount == null ? BigDecimal.ZERO : delayDiscountAmount); }
        public BigDecimal getDiscountedPercent() { return delayDiscountPercent == null ? BigDecimal.ZERO : delayDiscountPercent; }
        public boolean isCancellable() {
            return status.equals("CONFIRMED") && workStartedAt == null && (incidentReportedAt != null
                    || !OffsetDateTime.now().isAfter(start.minusHours(2)));
        }
    }
    public record EvidenceCard(Long id, String uploadedAt) { }
    public record FeedbackCard(Long id, Long bookingId, String customerName, Integer rating, List<String> topics,
            String content, String adminReply, String status, String bookingStatus, java.time.LocalDateTime bookingStart,
            BigDecimal bookingAmount, String staffName) {
        public String getBookingStartLabel() {
            return bookingStart == null ? "" : time(bookingStart.atOffset(ZoneOffset.ofHours(7)));
        }
        public String getStatusLabel() {
            return switch (status) {
                case "RESPONDED" -> "Đã phản hồi — chờ xử lý";
                case "RESOLVED" -> "Đã giải quyết";
                default -> "Đã tiếp nhận";
            };
        }
    }
    public record IncidentCard(Long id, Long feedbackId, Long bookingId, Long customerId, Long staffId,
            List<String> types, String description, String status, String customerOutcome, String remedyType,
            String internalFinding, boolean remedyCompleted, String staffAction, String staffActionStatus, Long proposedBy,
            Long approvedBy, Long staffActionId, java.time.LocalDateTime createdAt, java.time.LocalDateTime resolvedAt) {
        public String getStatusLabel() { return switch(status) {
            case "RECEIVED" -> "Đã tiếp nhận"; case "INVESTIGATING" -> "Đang xác minh";
            case "VERIFIED" -> "Đã xác minh"; case "NOT_SUBSTANTIATED" -> "Chưa đủ căn cứ";
            case "RESOLVED" -> "Đã giải quyết"; default -> "Không xác định"; }; }
        public List<String> getTypeLabels() { return types.stream().map(type -> switch(type) {
            case "CLEANLINESS" -> "Vệ sinh chưa đạt"; case "PROPERTY_DAMAGE" -> "Tài sản bị hư hại";
            case "MISSING_PROPERTY" -> "Tài sản bị mất"; case "OTHER" -> "Sự cố khác"; default -> type; }).toList(); }
    }
    public record FeedbackCaseMessageCard(Long id, Long caseId, Long senderId, String senderRole,
            String senderName, String text, java.time.LocalDateTime sentAt, boolean legacyImported,
            boolean legacyTimestampUnknown, List<Long> evidenceIds) { }
    public record FinancialEntryCard(Long id, Long bookingId, Long caseId, String type,
            BigDecimal amount, String reason, String status, String paymentMethod,
            String reference, String receiptPath, boolean simulated, Long createdBy,
            Long approvedBy, Long settledBy, String rejectionReason, String cancellationReason,
            java.time.LocalDateTime createdAt, java.time.LocalDateTime paidAt) {
        public String getTypeLabel() { return switch(type) {
            case "REFUND" -> "Hoàn tiền"; case "PRICE_REDUCTION" -> "Giảm giá";
            case "REMEDIATION_COST" -> "Chi phí khắc phục"; case "COMPENSATION" -> "Bồi thường";
            case "STAFF_RECOVERY" -> "Thu hồi nội bộ"; default -> type;
        }; }
        public String getStatusLabel() { return switch(status) {
            case "PROPOSED", "PENDING_APPROVAL" -> "Chờ Manager duyệt";
            case "APPROVED" -> "Đã duyệt, chờ thực hiện"; case "REJECTED" -> "Đã từ chối";
            case "PAID" -> "Đã chi/hoàn"; case "SIMULATED" -> "Đã mô phỏng (không phải tiền thật)";
            case "CANCELLED" -> "Đã hủy"; default -> status;
        }; }
    }
    public record CustomerFinancialCard(String type, BigDecimal amount, String status, String paymentMethod,
            String reference, java.time.LocalDateTime paidAt) {
        public String getTypeLabel() { return switch(type) { case "REFUND" -> "Hoàn tiền"; case "PRICE_REDUCTION" -> "Giảm giá"; case "COMPENSATION" -> "Bồi thường"; default -> type; }; }
        public String getStatusLabel() { return switch(status) { case "PAID" -> "Đã chi/hoàn"; case "SIMULATED" -> "Đã mô phỏng (không phát sinh tiền thật)"; case "APPROVED" -> "Đã duyệt, chờ thực hiện"; case "PROPOSED", "PENDING_APPROVAL" -> "Chờ duyệt"; case "REJECTED" -> "Không được duyệt"; case "CANCELLED" -> "Đã hủy"; default -> "Đang xử lý"; }; }
    }
    public record FinancialSummary(BigDecimal serviceRevenue, BigDecimal refundsAndReductions,
            BigDecimal remediationAndCompensation, BigDecimal staffRecovery, BigDecimal net, BigDecimal simulated) { }
    public record AdminAccountCard(Long userId, String name, String email, String phone, String status) { }
    public record StaffIncidentActionCard(Long id, String type, String status, String reason,
            String staffNotice, Long proposedBy, Long decidedBy, java.time.LocalDateTime createdAt,
            java.time.LocalDateTime decidedAt, java.time.LocalDateTime acknowledgedAt) { }
    public record StaffActionNoticeCard(Long id, String type, String notice, java.time.LocalDateTime createdAt,
            java.time.LocalDateTime decidedAt, java.time.LocalDateTime acknowledgedAt) {
        public String getTypeLabel() { return switch(type) { case "COACHING" -> "Nhắc nhở/hướng dẫn"; case "PROPOSE_WARNING" -> "Cảnh cáo bằng văn bản"; case "PROPOSE_FINAL_WARNING" -> "Cảnh cáo mức cuối"; case "PROPOSE_RECOVERY" -> "Xem xét trách nhiệm/thu hồi riêng"; case "OTHER" -> "Biện pháp khác"; default -> type; }; }
    }
    public record LeaveCard(Long id, Long staffId, String staffName, LocalDate startDate,
            LocalDate endDate, String reason, String status, String createdAt, List<Long> conflictingBookingIds) {
        public LeaveCard(Long id, Long staffId, String staffName, LocalDate startDate, LocalDate endDate,
                String reason, String status, String createdAt) { this(id,staffId,staffName,startDate,endDate,reason,status,createdAt,List.of()); }
    }
    public record DetailCard(Long id, String name, int sequence, int duration, BigDecimal price, List<EvidenceCard> evidence) { }
}
