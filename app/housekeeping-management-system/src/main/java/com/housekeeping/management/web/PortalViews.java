package com.housekeeping.management.web;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;

/** View models deliberately omit emails, password hashes, and storage paths. */
public final class PortalViews {
    private PortalViews() { }
    public static String time(OffsetDateTime value) {
        return value.withOffsetSameInstant(ZoneOffset.ofHours(7)).format(DateTimeFormatter.ofPattern("HH:mm · dd/MM/yyyy"));
    }
    public record ServiceCard(Long id, String name, String description, int duration, BigDecimal price) { }
    public record StaffCard(Long id, String name, String phone, boolean favorite) { }
    public record ScheduleCard(Long id, Long staffId, String staffName, OffsetDateTime start, OffsetDateTime end) {
        public String getStartLabel() { return time(start); }
        public String getEndLabel() { return time(end); }
        public String getStartInput() { return start.withOffsetSameInstant(ZoneOffset.ofHours(7)).toLocalDateTime().toString(); }
        public String getEndInput() { return end.withOffsetSameInstant(ZoneOffset.ofHours(7)).toLocalDateTime().toString(); }
    }
    public record BookingCard(Long id, Long customerId, Long staffId, String customerName, String staffName,
            OffsetDateTime start, OffsetDateTime end, String status, BigDecimal amount,
            String paymentStatus, String method, Integer rating) {
        public String getStartLabel() { return time(start); }
        public String getEndLabel() { return time(end); }
        public String getStatusLabel() {
            return switch (status) { case "CONFIRMED" -> "Đã xác nhận"; case "COMPLETED" -> "Đã hoàn thành"; default -> "Đã hủy"; };
        }
        public String getStatusClass() {
            return switch (status) { case "CONFIRMED" -> "confirmed"; case "COMPLETED" -> "completed"; default -> "cancelled"; };
        }
        public boolean isCancellable() { return status.equals("CONFIRMED") && !OffsetDateTime.now().isAfter(start.minusHours(2)); }
        public boolean isStarted() { return !OffsetDateTime.now().isBefore(start); }
    }
    public record EvidenceCard(Long id, String uploadedAt) { }
    public record DetailCard(Long id, String name, int sequence, int duration, BigDecimal price, List<EvidenceCard> evidence) { }
}
