package com.housekeeping.management.service.impl;

import com.housekeeping.management.service.BookingCompletionService;

import com.housekeeping.management.repository.BookingImageRepository;
import com.housekeeping.management.repository.BookingPersistenceRepository;
import com.housekeeping.management.dto.BookingDelayCompensation;
import com.housekeeping.management.enums.BookingStaffAssignmentStatus;
import com.housekeeping.management.repository.BookingStaffAssignmentRepository;
import com.housekeeping.management.repository.BookingTeamChangeRequestRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BookingCompletionServiceImpl implements BookingCompletionService {
    private final BookingPersistenceRepository bookings;
    private final BookingImageRepository bookingImages;
    private final BookingStaffAssignmentRepository assignments;
    private final BookingTeamChangeRequestRepository teamChangeRequests;

    public BookingCompletionServiceImpl(BookingPersistenceRepository bookings, BookingImageRepository bookingImages,
            BookingStaffAssignmentRepository assignments, BookingTeamChangeRequestRepository teamChangeRequests) {
        this.bookings = bookings;
        this.bookingImages = bookingImages;
        this.assignments = assignments;
        this.teamChangeRequests = teamChangeRequests;
    }

    @Transactional
    public void startBooking(Long bookingId, Long staffId) {
        var booking = bookings.lockBookingWorkflow(bookingId)
                .orElseThrow(() -> new IllegalArgumentException("Lịch hẹn không tồn tại."));
        if (!booking.staffId().equals(staffId)) {
            throw new IllegalArgumentException("Chỉ nhân viên được phân công mới có thể bắt đầu công việc.");
        }
        if (!"CONFIRMED".equals(booking.status()) || booking.workStartedAt() != null) {
            throw new IllegalStateException("Lịch không còn ở trạng thái có thể bắt đầu.");
        }
        if (teamChangeRequests.existsByBookingIdAndRequestStatus(bookingId, "PENDING")) {
            throw new IllegalStateException("Booking đang chờ khách chọn cách xử lý thiếu nhân viên.");
        }
        var team = assignments.findAllByBookingIdAndStatusOrderByRoleAscBookingStaffIdAsc(
                bookingId, BookingStaffAssignmentStatus.ASSIGNED);
        if (team.isEmpty() || team.stream().anyMatch(member -> member.getArrivedAt() == null)) {
            throw new IllegalStateException("Chỉ trưởng nhóm được bắt đầu và cần toàn bộ nhân viên đang được phân công xác nhận đã đến trước.");
        }
        var now = java.time.LocalDateTime.now(java.time.ZoneOffset.ofHours(7)).withNano(0);
        if (booking.staffArrivedAt() == null) {
            throw new IllegalStateException("Hãy xác nhận đã đến trước khi bắt đầu công việc.");
        }
        boolean customerVerifiedArrival = booking.customerArrivedAt() != null || booking.arrivalVerifiedAt() != null;
        boolean customerDelayReportOpen = booking.customerDelayReportedAt() != null
                && booking.customerArrivedAt() == null && booking.arrivalVerifiedAt() == null;
        if (customerDelayReportOpen) {
            throw new IllegalStateException("Khách đã báo giờ đến chưa đúng. Cần khách xác nhận nhân viên đã có mặt hoặc Admin xác minh trước khi bắt đầu.");
        }
        java.time.LocalDateTime effectiveArrival = "ADMIN".equals(booking.arrivalVerificationSource())
                && booking.arrivalVerifiedAt() != null ? booking.arrivalVerifiedAt() : booking.staffArrivedAt();
        boolean lateBeyondGrace = effectiveArrival.isAfter(booking.startTime());
        if (lateBeyondGrace && booking.incidentReportedAt() == null && !customerVerifiedArrival) {
            throw new IllegalStateException("Nhân viên đến trễ cần gửi giờ dự kiến và chờ khách đồng ý, hoặc khách xác nhận nhân viên đã có mặt.");
        }
        if (booking.incidentReportedAt() != null && !customerVerifiedArrival
                && !"ACCEPTED".equals(booking.incidentCustomerResponse())) {
            throw new IllegalStateException("Cần khách đồng ý giờ đến trễ hoặc xác nhận nhân viên đã có mặt trước khi bắt đầu.");
        }
        if (!customerVerifiedArrival && "ACCEPTED".equals(booking.incidentCustomerResponse()) && booking.incidentExpectedArrivalAt() != null
                && effectiveArrival.isAfter(booking.incidentExpectedArrivalAt())) {
            throw new IllegalStateException("Nhân viên đã lỡ giờ đến được đồng ý. Cần gửi giờ mới hoặc khách xác nhận nhân viên đã có mặt.");
        }
        if (bookings.startWork(bookingId, staffId, now, lateBeyondGrace && !customerVerifiedArrival) != 1) {
            throw new IllegalStateException("Lịch vừa thay đổi hoặc chưa đủ điều kiện bắt đầu. Hãy tải lại trang.");
        }
    }

    @Transactional
    public void completeBooking(Long bookingId, Long staffId) {
        BookingPersistenceRepository.BookingWorkflowState booking = bookings.lockBookingWorkflow(bookingId)
                .orElseThrow(() -> new IllegalArgumentException("Lịch hẹn không tồn tại."));
        if (!booking.staffId().equals(staffId)) {
            throw new IllegalArgumentException("Chỉ nhân viên được phân công mới có thể xác nhận hoàn thành.");
        }
        if (!"CONFIRMED".equals(booking.status())) {
            throw new IllegalStateException("Chỉ lịch đã xác nhận mới chuyển sang chờ thanh toán.");
        }
        if (booking.workStartedAt() == null) {
            throw new IllegalStateException("Cần xác nhận bắt đầu công việc trước khi hoàn thành.");
        }
        if (!bookingImages.existsByBookingId(bookingId)) {
            throw new IllegalStateException("Cần tải ít nhất một ảnh minh chứng trước khi xác nhận hoàn thành.");
        }
        var completedAt = java.time.LocalDateTime.now(java.time.ZoneOffset.ofHours(7)).withNano(0);
        if (bookings.completeWork(bookingId, staffId, completedAt) != 1) {
            throw new IllegalStateException("Trạng thái lịch hẹn vừa thay đổi. Hãy tải lại trang.");
        }
    }
}
