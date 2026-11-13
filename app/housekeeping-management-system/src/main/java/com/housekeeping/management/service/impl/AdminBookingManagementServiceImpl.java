package com.housekeeping.management.service.impl;

import com.housekeeping.management.enums.TravelStatus;
import com.housekeeping.management.enums.BookingStaffAssignmentStatus;
import com.housekeeping.management.repository.BookingStaffAssignmentRepository;
import com.housekeeping.management.entity.BookingStaffAssignment;
import com.housekeeping.management.enums.BookingStaffRole;
import com.housekeeping.management.service.AccountAuthorizationService;
import com.housekeeping.management.service.AdminBookingManagementService;
import com.housekeeping.management.service.BookingReservationService;
import com.housekeeping.management.service.HouseService;
import com.housekeeping.management.service.StaffManagementService;
import com.housekeeping.management.service.CompanySettingsService;
import com.housekeeping.management.service.BookingDelayCompensationSettingsService;
import com.housekeeping.management.dto.BookingDelayCompensation;
import com.housekeeping.management.entity.CancellationLog;

import com.housekeeping.management.enums.StaffType;
import com.housekeeping.management.entity.Staff;
import com.housekeeping.management.repository.BookingPersistenceRepository;
import com.housekeeping.management.repository.CancellationLogRepository;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminBookingManagementServiceImpl implements AdminBookingManagementService {
    private static final ZoneOffset BUSINESS_OFFSET = ZoneOffset.ofHours(7);
    private final BookingPersistenceRepository bookings;
    private final StaffManagementService staffManagement;
    private final AccountAuthorizationService authorization;
    private final BookingReservationService reservations;
    private final HouseService houses;
    private final CompanySettingsService companySettings;
    private final BookingStaffAssignmentRepository assignments;
    private final BookingDelayCompensationSettingsService compensationSettings;
    private final CancellationLogRepository cancellationLogs;

    public AdminBookingManagementServiceImpl(BookingPersistenceRepository bookings,
            StaffManagementService staffManagement, AccountAuthorizationService authorization,
            BookingReservationService reservations, HouseService houses, CompanySettingsService companySettings,
            BookingDelayCompensationSettingsService compensationSettings, CancellationLogRepository cancellationLogs,
            BookingStaffAssignmentRepository assignments) {
        this.bookings = bookings;
        this.staffManagement = staffManagement;
        this.authorization = authorization;
        this.reservations = reservations;
        this.houses = houses;
        this.companySettings = companySettings;
        this.assignments = assignments;
        this.compensationSettings = compensationSettings;
        this.cancellationLogs = cancellationLogs;
    }

    @Transactional
    public void verifyDisputedArrival(Long bookingId, Long adminId, LocalDateTime actualArrivalAt, String note) {
        authorization.requireActiveAdmin(adminId);
        var booking = bookings.lockBookingWorkflow(bookingId)
                .orElseThrow(() -> new IllegalArgumentException("Lịch hẹn không tồn tại."));
        if (!"CONFIRMED".equals(booking.status()) || booking.customerDelayReportedAt() == null
                || (booking.arrivalVerifiedAt() != null && !"CUSTOMER".equals(booking.arrivalVerificationSource()))
                || booking.workStartedAt() != null || booking.serviceCompletedAt() != null) {
            throw new IllegalStateException("Booking không còn chờ xác minh giờ đến.");
        }
        LocalDateTime now = now();
        String normalizedNote = note == null ? "" : note.trim();
        if (actualArrivalAt == null || actualArrivalAt.isAfter(now)) {
            throw new IllegalArgumentException("Giờ đến thực tế không được ở tương lai.");
        }
        if (normalizedNote.isBlank() || normalizedNote.length() > 500) {
            throw new IllegalArgumentException("Ghi chú xác minh bắt buộc và tối đa 500 ký tự.");
        }
        BookingDelayCompensation compensation = BookingDelayCompensation.calculate(
                booking.startTime(), actualArrivalAt, booking.totalAmount(), compensationSettings.get());
        if (bookings.verifyDisputedArrival(bookingId, adminId, actualArrivalAt, now, normalizedNote,
                compensation.delayedMinutes(), java.math.BigDecimal.valueOf(compensation.discountPercent()),
                compensation.discountAmount()) != 1) {
            throw new IllegalStateException("Booking vừa được xử lý. Hãy tải lại trang.");
        }
    }

    @Transactional
    public void verifyStaffNotArrived(Long bookingId, Long adminId, String note) {
        authorization.requireActiveAdmin(adminId);
        var booking = bookings.lockEntity(bookingId)
                .orElseThrow(() -> new IllegalArgumentException("Lịch hẹn không tồn tại."));
        String normalizedNote = note == null ? "" : note.trim();
        if (booking.getStatus() != com.housekeeping.management.enums.BookingStatus.CONFIRMED
                || booking.getCustomerDelayReportedAt() == null
                || (booking.getArrivalVerifiedAt() != null && !"CUSTOMER".equals(booking.getArrivalVerificationSource()))
                || booking.getAdminNoShowVerifiedAt() != null || booking.getWorkStartedAt() != null
                || booking.getServiceCompletedAt() != null) {
            throw new IllegalStateException("Booking không còn chờ Admin xác minh nhân viên chưa tới.");
        }
        if (normalizedNote.isBlank() || normalizedNote.length() > 500) {
            throw new IllegalArgumentException("Ghi chú xác minh bắt buộc và tối đa 500 ký tự.");
        }
        LocalDateTime now = now();
        if (bookings.verifyStaffNotArrived(bookingId, adminId, now, normalizedNote) != 1) {
            throw new IllegalStateException("Booking vừa được xử lý. Hãy tải lại trang.");
        }
    }

    @Transactional
    public void cancelConfirmedBooking(Long bookingId, Long adminId, String reason) {
        authorization.requireActiveAdmin(adminId);
        var booking = bookings.lockBookingWorkflow(bookingId)
                .orElseThrow(() -> new IllegalArgumentException("Lịch hẹn không tồn tại."));
        if (!"CONFIRMED".equals(booking.status())) {
            throw new IllegalStateException("Chỉ lịch đang CONFIRMED mới được Admin hủy.");
        }
        LocalDateTime cancelledAt = now();
        String normalizedReason = reason == null ? "" : reason.trim();
        if (normalizedReason.isBlank() || normalizedReason.length() > 500) {
            throw new IllegalArgumentException("Lý do hủy bắt buộc và tối đa 500 ký tự.");
        }
        if (bookings.cancel(bookingId, adminId, normalizedReason, cancelledAt) != 1) {
            throw new IllegalStateException("Trạng thái lịch hẹn vừa thay đổi. Hãy tải lại trang.");
        }
        cancellationLogs.save(CancellationLog.builder()
                .bookingId(booking.id()).customerId(booking.customerId()).workerId(booking.staffId())
                .cancelledByType("ADMIN").reasonCode("ADMIN_CANCELLED").reasonNote(normalizedReason)
                .bookingTotalAmount(booking.totalAmount()).cancellationFee(BigDecimal.ZERO)
                .workerCompensation(BigDecimal.ZERO).platformFee(BigDecimal.ZERO).refundAmount(BigDecimal.ZERO)
                .deltaMinutesToStart(Duration.between(cancelledAt, booking.startTime()).toMinutes())
                .isWorkerFault(false).createdAt(cancelledAt).build());
    }

    @Transactional
    public void reassignConfirmedBooking(Long bookingId, Long adminId, Long replacementStaffId) {
        var observed = bookings.findBooking(bookingId)
                .orElseThrow(() -> new IllegalArgumentException("Lịch hẹn không tồn tại."));
        replaceAssignedStaff(bookingId, adminId, observed.staffId(), replacementStaffId);
    }

    @Override
    @Transactional
    public void replaceAssignedStaff(Long bookingId, Long adminId, Long currentStaffId, Long replacementStaffId) {
        authorization.requireActiveAdmin(adminId);
        var observed = bookings.findBooking(bookingId)
                .orElseThrow(() -> new IllegalArgumentException("Lịch hẹn không tồn tại."));
        if (!"CONFIRMED".equals(observed.status())) {
            throw new IllegalStateException("Chỉ có thể đổi nhân viên cho lịch đang CONFIRMED.");
        }
        // Match booking creation's Staff -> Bookings lock order to avoid a cycle with overlap queries.
        Staff replacement = staffManagement.findActiveForBookingUpdate(replacementStaffId);
        var booking = bookings.lockEntity(bookingId)
                .orElseThrow(() -> new IllegalArgumentException("Lịch hẹn không tồn tại."));
        if (!"CONFIRMED".equals(booking.getStatus().name())) {
            throw new IllegalStateException("Chỉ có thể đổi nhân viên cho lịch đang CONFIRMED.");
        }
        if (booking.getWorkStartedAt() != null) {
            throw new IllegalStateException("Công việc đã bắt đầu nên không thể thay đổi thành viên nhóm.");
        }
        if (currentStaffId.equals(replacementStaffId)) return;
        BookingStaffAssignment current = assignments.findAllByBookingIdAndStatusOrderByRoleAscBookingStaffIdAsc(
                        bookingId, BookingStaffAssignmentStatus.ASSIGNED).stream()
                .filter(item -> item.getStaffId().equals(currentStaffId)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Nhân viên cần thay thế không còn trong nhóm lịch này."));
        if (current.getArrivedAt() != null) {
            throw new IllegalStateException("Nhân viên đã xác nhận có mặt nên không thể thay khỏi nhóm lịch này.");
        }
        if (assignments.existsByBookingIdAndStaffIdAndStatus(bookingId, replacementStaffId,
                BookingStaffAssignmentStatus.ASSIGNED)) {
            throw new IllegalArgumentException("Nhân viên thay thế đã có trong nhóm lịch này.");
        }
        companySettings.lockSettingsForScheduling();
        var start = booking.getStartTime();
        if (!companySettings.acceptsInterval(start.toLocalDate(), start.toLocalTime(), booking.getEndTime().toLocalTime())
                || booking.getEndTime().toLocalDate().isAfter(start.toLocalDate())) {
            throw new IllegalStateException("Lịch không nằm trọn trong cùng một khung giờ hoạt động hiện được cấu hình.");
        }
        if (staffManagement.isOnApprovedLeave(replacementStaffId, start.toLocalDate())) {
            throw new IllegalArgumentException("Nhân viên thay thế đã được duyệt nghỉ trong ngày này.");
        }
        if (replacement.getStaffType() == StaffType.PART_TIME
                && !staffManagement.acceptsAvailabilityInterval(replacementStaffId, start.toLocalDate(), start.toLocalTime(), booking.getEndTime().toLocalTime())) {
            throw new IllegalArgumentException("Toàn bộ lịch phải nằm trong khoảng sẵn sàng của nhân viên bán thời gian.");
        }
        if (booking.getLatitudeSnapshot() == null || booking.getLongitudeSnapshot() == null) throw new IllegalArgumentException("Không thể xác định vị trí địa chỉ Booking để tính tuyến.");
        BookingReservationService.TravelAssessment travel=reservations.assessTravel(replacementStaffId,start,booking.getEndTime(),
                booking.getLatitudeSnapshot(),booking.getLongitudeSnapshot(),bookingId);
        if(!travel.feasible()) {
            if(travel.status()==TravelStatus.ROUTE_UNAVAILABLE)throw new IllegalStateException(travel.message());
            throw new IllegalArgumentException(travel.message());
        }
        if (BookingStaffRole.LEAD == current.getRole()
                && bookings.assignStaff(bookingId, booking.getStaffId(), replacementStaffId) != 1) {
            throw new IllegalStateException("Lịch hẹn vừa thay đổi. Hãy tải lại trang.");
        }
        current.setStatus(BookingStaffAssignmentStatus.REPLACED);
        assignments.saveAndFlush(current);
        BookingStaffAssignment assignment = new BookingStaffAssignment();
        assignment.setBookingId(bookingId);
        assignment.setStaffId(replacementStaffId);
        assignment.setRole(current.getRole());
        assignment.setStatus(BookingStaffAssignmentStatus.ASSIGNED);
        assignment.setAssignedAt(now());
        assignments.save(assignment);
    }

    private static LocalDateTime now() { return LocalDateTime.now(BUSINESS_OFFSET).withNano(0); }
}
