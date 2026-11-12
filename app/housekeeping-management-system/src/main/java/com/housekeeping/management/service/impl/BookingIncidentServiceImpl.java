package com.housekeeping.management.service.impl;

import com.housekeeping.management.service.BookingIncidentService;
import com.housekeeping.management.repository.BookingPersistenceRepository;
import com.housekeeping.management.repository.BookingDelayProposalRepository;
import com.housekeeping.management.entity.BookingDelayProposal;
import com.housekeeping.management.entity.CancellationLog;
import com.housekeeping.management.repository.CancellationLogRepository;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BookingIncidentServiceImpl implements BookingIncidentService {
    private static final int MAX_NOTE_LENGTH = 500;
    private final BookingPersistenceRepository bookings;
    private final BookingDelayProposalRepository proposals;
    private final CancellationLogRepository cancellationLogs;

    public BookingIncidentServiceImpl(BookingPersistenceRepository bookings, BookingDelayProposalRepository proposals,
            CancellationLogRepository cancellationLogs) {
        this.bookings = bookings;
        this.proposals = proposals;
        this.cancellationLogs = cancellationLogs;
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
        if (!"CONFIRMED".equals(booking.status()) || booking.workStartedAt() != null || booking.serviceCompletedAt() != null) {
            throw new IllegalStateException("Chỉ báo sự cố khi lịch còn CONFIRMED và công việc chưa bắt đầu.");
        }
        if (!now.isBefore(booking.startTime())) {
            throw new IllegalStateException("Đề nghị ETA trễ chỉ được gửi trước giờ bắt đầu.");
        }
        if (expectedArrivalAt == null || !expectedArrivalAt.isAfter(booking.startTime()) || expectedArrivalAt.isBefore(now)) {
            throw new IllegalArgumentException("ETA phải cụ thể, sau giờ hẹn và không trước thời điểm hiện tại.");
        }
        var previous = proposals.findFirstByBookingIdOrderByProposalIdDesc(bookingId).orElse(null);
        boolean expiredPendingProposal = false;
        if (previous != null && "PENDING".equals(previous.getStatus()) && now.isAfter(previous.getExpectedArrivalAt())) {
            proposals.respond(previous.getProposalId(), "EXPIRED", now);
            bookings.expireBookingEta(bookingId, now);
            expiredPendingProposal = true;
        }
        if (previous != null && "ACCEPTED".equals(previous.getStatus())) {
            if (!now.isAfter(previous.getExpectedArrivalAt())) throw new IllegalStateException("ETA đã được Customer đồng ý và chưa hết hạn.");
            proposals.expireAccepted(previous.getProposalId(), now);
            bookings.expireBookingEta(bookingId, now);
        } else if (!expiredPendingProposal && booking.incidentReportedAt() != null && !"DECLINED".equals(booking.incidentCustomerResponse())
                && !"EXPIRED".equals(booking.incidentCustomerResponse())) {
            throw new IllegalStateException("Đề xuất ETA trước vẫn đang chờ phản hồi hoặc chưa hết hạn.");
        }
        if (bookings.reportStaffIncident(bookingId, staffId, normalizedNote, expectedArrivalAt, now) != 1) {
            throw new IllegalStateException("Lịch vừa thay đổi hoặc đã có báo cáo/khách phản hồi. Hãy tải lại trang.");
        }
        BookingDelayProposal proposal = new BookingDelayProposal();
        proposal.setBookingId(bookingId); proposal.setStaffId(staffId); proposal.setExpectedArrivalAt(expectedArrivalAt);
        proposal.setReason(normalizedNote); proposal.setProposedAt(now); proposal.setStatus("PENDING");
        proposals.save(proposal);
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
        var proposal = proposals.findFirstByBookingIdOrderByProposalIdDesc(bookingId)
                .orElseThrow(() -> new IllegalStateException("Không tìm thấy đề xuất ETA cần phản hồi."));
        if (!"PENDING".equals(proposal.getStatus())) throw new IllegalStateException("Đề xuất ETA này không còn chờ phản hồi.");
        if (now.isAfter(proposal.getExpectedArrivalAt())) {
            if ("DECLINED".equals(response)) {
                if (proposals.respond(proposal.getProposalId(), "DECLINED", now) != 1
                        || bookings.respondToIncident(bookingId, customerId, "DECLINED", now) != 1) {
                    throw new IllegalStateException("Đề xuất ETA vừa thay đổi. Hãy tải lại trang.");
                }
                recordCancellation(booking, customerId, now);
                return;
            }
            proposals.respond(proposal.getProposalId(), "EXPIRED", now);
            bookings.expireBookingEta(bookingId, now);
            return;
        }
        if (!"ACCEPTED".equals(response) && !"DECLINED".equals(response)) {
            throw new IllegalArgumentException("Phản hồi ETA không hợp lệ.");
        }
        if (proposals.respond(proposal.getProposalId(), response, now) != 1) {
            throw new IllegalStateException("Đề xuất ETA vừa được phản hồi. Hãy tải lại trang.");
        }
        if (bookings.respondToIncident(bookingId, customerId, response, now) != 1) {
            throw new IllegalStateException("Lịch vừa thay đổi. Hãy tải lại trang trước khi phản hồi.");
        }
        if ("DECLINED".equals(response)) recordCancellation(booking, customerId, now);
    }

    private void recordCancellation(BookingPersistenceRepository.BookingWorkflowState booking, Long customerId,
            LocalDateTime now) {
        String reason = "Khách hủy vì không chấp nhận giờ đến trễ của nhân viên.";
        cancellationLogs.save(CancellationLog.builder()
                .bookingId(booking.id()).customerId(customerId).workerId(booking.staffId())
                .cancelledByType("CUSTOMER").reasonCode("STAFF_DELAY").reasonNote(reason)
                .bookingTotalAmount(booking.totalAmount()).cancellationFee(BigDecimal.ZERO)
                .workerCompensation(BigDecimal.ZERO).platformFee(BigDecimal.ZERO).refundAmount(BigDecimal.ZERO)
                .deltaMinutesToStart(Duration.between(now, booking.startTime()).toMinutes())
                .isWorkerFault(true).createdAt(now).build());
    }
}
