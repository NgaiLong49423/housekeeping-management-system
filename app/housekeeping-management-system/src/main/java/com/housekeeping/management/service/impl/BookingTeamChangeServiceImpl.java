package com.housekeeping.management.service.impl;

import com.housekeeping.management.dto.entity.BookingTeamChangeRequestDTO;
import com.housekeeping.management.dto.SpecificAddressRules;
import com.housekeeping.management.entity.BookingStaffAssignment;
import com.housekeeping.management.entity.BookingDetail;
import com.housekeeping.management.entity.BookingTeamChangeRequest;
import com.housekeeping.management.entity.CancellationLog;
import com.housekeeping.management.enums.BookingStaffAssignmentStatus;
import com.housekeeping.management.enums.BookingStaffRole;
import com.housekeeping.management.enums.BookingStatus;
import com.housekeeping.management.enums.StaffType;
import com.housekeeping.management.repository.BookingDetailRepository;
import com.housekeeping.management.repository.BookingPersistenceRepository;
import com.housekeeping.management.repository.BookingStaffAssignmentRepository;
import com.housekeeping.management.repository.BookingTeamChangeRequestRepository;
import com.housekeeping.management.repository.CancellationLogRepository;
import com.housekeeping.management.service.AccountAuthorizationService;
import com.housekeeping.management.service.BookingReservationService;
import com.housekeeping.management.service.BookingTeamChangeService;
import com.housekeeping.management.service.CompanySettingsService;
import com.housekeeping.management.service.HouseService;
import com.housekeeping.management.service.StaffManagementService;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.List;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BookingTeamChangeServiceImpl implements BookingTeamChangeService {
    private static final ZoneOffset BUSINESS_OFFSET = ZoneOffset.ofHours(7);
    private static final BigDecimal ZERO = BigDecimal.ZERO;

    private final BookingPersistenceRepository bookings;
    private final BookingStaffAssignmentRepository assignments;
    private final BookingTeamChangeRequestRepository requests;
    private final BookingDetailRepository details;
    private final CancellationLogRepository cancellationLogs;
    private final AccountAuthorizationService authorization;
    private final StaffManagementService staffManagement;
    private final CompanySettingsService companySettings;
    private final BookingReservationService reservations;
    private final HouseService houses;

    public BookingTeamChangeServiceImpl(BookingPersistenceRepository bookings,
            BookingStaffAssignmentRepository assignments, BookingTeamChangeRequestRepository requests,
            BookingDetailRepository details, CancellationLogRepository cancellationLogs,
            AccountAuthorizationService authorization, StaffManagementService staffManagement,
            CompanySettingsService companySettings, BookingReservationService reservations, HouseService houses) {
        this.bookings = bookings;
        this.assignments = assignments;
        this.requests = requests;
        this.details = details;
        this.cancellationLogs = cancellationLogs;
        this.authorization = authorization;
        this.staffManagement = staffManagement;
        this.companySettings = companySettings;
        this.reservations = reservations;
        this.houses = houses;
    }

    @Override
    @Transactional
    public void requestReduction(Long bookingId, Long adminId, Long staffId, String reason) {
        authorization.requireActiveAdmin(adminId);
        var booking = bookings.lockEntity(bookingId)
                .orElseThrow(() -> new IllegalArgumentException("Lịch hẹn không tồn tại."));
        ensureUnstartedConfirmed(booking.getStatus(), booking.getWorkStartedAt());
        String note = reason == null ? "" : reason.trim();
        if (note.isBlank() || note.length() > 1000) {
            throw new IllegalArgumentException("Lý do thiếu nhân viên bắt buộc và tối đa 1.000 ký tự.");
        }
        if (requests.findFirstByBookingIdAndRequestStatusOrderByCreatedAtDesc(bookingId, "PENDING").isPresent()) {
            throw new IllegalStateException("Booking đang chờ khách chọn cách xử lý thiếu nhân viên.");
        }
        List<BookingStaffAssignment> team = activeTeam(bookingId);
        BookingStaffAssignment affected = team.stream().filter(item -> item.getStaffId().equals(staffId)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Nhân viên không còn trong nhóm booking."));
        if (affected.getArrivedAt() != null) throw new IllegalStateException("Nhân viên đã xác nhận có mặt nên không thể bỏ khỏi nhóm booking.");
        if (team.isEmpty()) throw new IllegalStateException("Booking không có nhân viên đang được phân công.");
        if (team.size() > 1 && affected.getRole() == BookingStaffRole.LEAD) {
            BookingStaffAssignment promoted = team.stream()
                    .filter(item -> !item.getStaffId().equals(staffId) && item.getRole() == BookingStaffRole.MEMBER)
                    .findFirst().orElseThrow(() -> new IllegalStateException("Không tìm được thành viên đang hoạt động để làm trưởng nhóm thay thế."));
            affected.setStatus(BookingStaffAssignmentStatus.REMOVED);
            assignments.saveAndFlush(affected);
            promoted.setRole(BookingStaffRole.LEAD);
            promoted.setStatus(BookingStaffAssignmentStatus.ASSIGNED);
            booking.setStaffId(promoted.getStaffId());
            booking.setStaffArrivedAt(promoted.getArrivedAt());
            bookings.save(booking);
            assignments.saveAndFlush(promoted);
        } else {
            affected.setStatus(BookingStaffAssignmentStatus.REMOVED);
            assignments.saveAndFlush(affected);
        }
        BookingTeamChangeRequest request = new BookingTeamChangeRequest();
        request.setBookingId(bookingId);
        request.setRequestedBy(adminId);
        request.setAffectedStaffId(staffId);
        request.setProposedStaffCount(Math.max(0, team.size() - 1));
        request.setReason(note);
        request.setRequestStatus("PENDING");
        request.setCreatedAt(now());
        requests.save(request);
    }

    @Override
    @Transactional(readOnly = true)
    public java.util.Optional<BookingTeamChangeRequestDTO> pendingRequest(Long bookingId, Long customerId) {
        var booking = bookings.findById(bookingId)
                .orElseThrow(() -> new IllegalArgumentException("Lịch hẹn không tồn tại."));
        requireOwner(booking.getCustomerId(), customerId);
        return requests.findFirstByBookingIdAndRequestStatusOrderByCreatedAtDesc(bookingId, "PENDING")
                .map(BookingTeamChangeRequestDTO::convertEntityToDTO);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean hasPendingRequest(Long bookingId) {
        return requests.existsByBookingIdAndRequestStatus(bookingId, "PENDING");
    }

    @Override
    @Transactional(readOnly = true)
    public void assertRescheduleAllowed(Long bookingId, Long customerId) {
        var booking = bookings.findById(bookingId)
                .orElseThrow(() -> new IllegalArgumentException("Lịch hẹn không tồn tại."));
        requireOwner(booking.getCustomerId(), customerId);
        ensureUnstartedConfirmed(booking.getStatus(), booking.getWorkStartedAt());
        requirePendingRequest(bookingId);
    }

    @Override
    @Transactional(readOnly = true)
    public RescheduleInfo rescheduleInfo(Long bookingId, Long customerId) {
        assertRescheduleAllowed(bookingId, customerId);
        var booking = bookings.findById(bookingId).orElseThrow();
        List<BookingStaffAssignment> team = activeTeam(bookingId);
        List<RescheduleServiceLine> serviceLines = details.findAllByBookingIdOrderByBookingDetailId(bookingId).stream()
                .map(item -> new RescheduleServiceLine(item.getServiceId(), item.getServiceNameSnapshot(),
                        item.getUnitPrice(), item.getAdditionalStaffPriceSnapshot(), item.getDurationMinutes())).toList();
        Long lead = team.stream().filter(item -> item.getRole() == BookingStaffRole.LEAD)
                .map(BookingStaffAssignment::getStaffId).findFirst().orElse(null);
        return new RescheduleInfo(bookingId, booking.getHouseId(), serviceLines,
                team.stream().map(BookingStaffAssignment::getStaffId).toList(), lead,
                booking.getStartTime(), booking.getAreaNote(), booking.getTotalAmount());
    }

    @Override
    @Transactional
    public void proceedWithFewerStaff(Long bookingId, Long customerId) {
        var booking = lockCustomerBooking(bookingId, customerId);
        ensureUnstartedConfirmed(booking.getStatus(), booking.getWorkStartedAt());
        BookingTeamChangeRequest request = lockPendingRequest(bookingId);
        List<BookingStaffAssignment> team = activeTeam(bookingId);
        if (team.isEmpty()) throw new IllegalStateException("Cần có ít nhất một nhân viên để tiếp tục booking.");
        updatePriceForTeamSize(bookingId, team.size(), booking);
        resolve(request, customerId, "PROCEEDED_SMALLER");
    }

    @Override
    @Transactional
    public void cancelForTeamChange(Long bookingId, Long customerId) {
        var booking = lockCustomerBooking(bookingId, customerId);
        ensureUnstartedConfirmed(booking.getStatus(), booking.getWorkStartedAt());
        BookingTeamChangeRequest request = lockPendingRequest(bookingId);
        LocalDateTime cancelledAt = now();
        String reason = "Khách hủy miễn phí vì không đủ nhân viên cho booking đã đặt.";
        booking.setStatus(BookingStatus.CANCELLED);
        booking.setCancellationFee(ZERO);
        booking.setRefundAmount(ZERO);
        booking.setDelayMinutes(0);
        booking.setDelayDiscountPercent(ZERO);
        booking.setDelayDiscountAmount(ZERO);
        booking.setCancelledAt(cancelledAt);
        booking.setCancelledBy(customerId);
        booking.setCancellationReason(reason);
        bookings.save(booking);
        cancellationLogs.save(CancellationLog.builder().bookingId(booking.getBookingId())
                .customerId(booking.getCustomerId()).workerId(booking.getStaffId()).cancelledByType("CUSTOMER")
                .reasonCode("TEAM_UNAVAILABLE").reasonNote(reason).bookingTotalAmount(booking.getTotalAmount())
                .cancellationFee(ZERO).workerCompensation(ZERO).platformFee(ZERO).refundAmount(ZERO)
                .deltaMinutesToStart(Duration.between(cancelledAt, booking.getStartTime()).toMinutes())
                .isWorkerFault(false).createdAt(cancelledAt).build());
        resolve(request, customerId, "CANCELLED");
    }

    @Override
    @Transactional
    public void resolvePendingAsCancelled(Long bookingId, Long actorId) {
        requests.findFirstByBookingIdAndRequestStatusOrderByCreatedAtDesc(bookingId, "PENDING")
                .flatMap(request -> requests.lockById(request.getRequestId()))
                .filter(request -> "PENDING".equals(request.getRequestStatus()))
                .ifPresent(request -> resolve(request, actorId, "CANCELLED"));
    }

    @Override
    @Transactional
    public void reschedule(Long bookingId, Long customerId, List<Long> requestedStaffIds, Long leadStaffId,
            LocalDateTime startAt) {
        var observed = bookings.findById(bookingId)
                .orElseThrow(() -> new IllegalArgumentException("Lịch hẹn không tồn tại."));
        requireOwner(observed.getCustomerId(), customerId);
        ensureUnstartedConfirmed(observed.getStatus(), observed.getWorkStartedAt());
        requirePendingRequest(bookingId);
        if (requestedStaffIds == null || requestedStaffIds.isEmpty()
                || requestedStaffIds.stream().anyMatch(java.util.Objects::isNull)
                || new HashSet<>(requestedStaffIds).size() != requestedStaffIds.size()) {
            throw new IllegalArgumentException("Hãy chọn ít nhất một nhân viên khác nhau cho lịch mới.");
        }
        if (leadStaffId == null || !requestedStaffIds.contains(leadStaffId)) {
            throw new IllegalArgumentException("Trưởng nhóm phải nằm trong nhóm đã chọn.");
        }
        List<Long> staffIds = requestedStaffIds.stream().sorted().toList();
        var staffById = new java.util.LinkedHashMap<Long, com.housekeeping.management.entity.Staff>();
        for (Long staffId : staffIds) staffById.put(staffId, staffManagement.findActiveForBookingUpdate(staffId));
        var booking = bookings.lockEntity(bookingId)
                .orElseThrow(() -> new IllegalArgumentException("Lịch hẹn không tồn tại."));
        requireOwner(booking.getCustomerId(), customerId);
        ensureUnstartedConfirmed(booking.getStatus(), booking.getWorkStartedAt());
        BookingTeamChangeRequest request = lockPendingRequest(bookingId);
        BookingReservationService.validateRequestedStart(startAt, now());
        int duration = booking.getTotalDurationMinutes();
        LocalDateTime endAt = startAt.plusMinutes(duration);
        if (!endAt.toLocalDate().equals(startAt.toLocalDate())) {
            throw new IllegalArgumentException("Lịch phải kết thúc trong cùng ngày và giờ hoạt động.");
        }
        companySettings.lockSettingsForScheduling();
        if (booking.getLatitudeSnapshot() == null || booking.getLongitudeSnapshot() == null
                || !SpecificAddressRules.isSpecific(booking.getAddressSnapshot())) {
            throw new IllegalStateException("Địa chỉ snapshot của booking chưa đủ thông tin để kiểm tra tuyến và khu vực phục vụ.");
        }
        if (!houses.isWithinServiceRadius(booking.getLatitudeSnapshot(), booking.getLongitudeSnapshot())) {
            throw new IllegalArgumentException("Địa chỉ booking hiện nằm ngoài bán kính phục vụ của công ty.");
        }
        if (!companySettings.acceptsInterval(startAt.toLocalDate(), startAt.toLocalTime(), endAt.toLocalTime())) {
            throw new IllegalArgumentException("Toàn bộ lịch phải nằm trong giờ hoạt động của công ty.");
        }
        for (Long staffId : staffIds) {
            var staff = staffById.get(staffId);
            if (staffManagement.isOnApprovedLeave(staffId, startAt.toLocalDate())) {
                throw new IllegalArgumentException("Nhân viên #" + staffId + " đã được duyệt nghỉ trong ngày này.");
            }
            if (staff.getStaffType() == StaffType.PART_TIME && !staffManagement.acceptsAvailabilityInterval(
                    staffId, startAt.toLocalDate(), startAt.toLocalTime(), endAt.toLocalTime())) {
                throw new IllegalArgumentException("Toàn bộ lịch phải nằm trong khoảng sẵn sàng của từng nhân viên đã chọn.");
            }
            var travel = reservations.assessTravel(staffId, startAt, endAt,
                    booking.getLatitudeSnapshot(), booking.getLongitudeSnapshot(), bookingId);
            if (!travel.feasible()) {
                if (travel.status() == com.housekeeping.management.enums.TravelStatus.ROUTE_UNAVAILABLE)
                    throw new IllegalStateException("Nhân viên #" + staffId + ": " + travel.message());
                throw new IllegalArgumentException("Nhân viên #" + staffId + ": " + travel.message());
            }
        }
        List<BookingDetail> lines = details.findAllByBookingIdOrderByBookingDetailId(bookingId);
        if (lines.isEmpty()) throw new IllegalStateException("Booking không có dịch vụ để tính lại giá.");
        BigDecimal amount = priceFor(lines, staffIds.size());
        assignments.saveAllAndFlush(activeTeam(bookingId).stream().peek(item -> {
            item.setStatus(BookingStaffAssignmentStatus.REMOVED);
        }).toList());
        LocalDateTime assignedAt = now();
        assignments.saveAll(staffIds.stream().map(staffId -> {
            BookingStaffAssignment member = new BookingStaffAssignment();
            member.setBookingId(bookingId);
            member.setStaffId(staffId);
            member.setRole(staffId.equals(leadStaffId) ? BookingStaffRole.LEAD : BookingStaffRole.MEMBER);
            member.setStatus(BookingStaffAssignmentStatus.ASSIGNED);
            member.setAssignedAt(assignedAt);
            return member;
        }).toList());
        lines.forEach(line -> line.setStaffCountSnapshot(staffIds.size()));
        details.saveAll(lines);
        booking.setStaffId(leadStaffId);
        booking.setStartTime(startAt);
        booking.setEndTime(endAt);
        booking.setTotalAmount(amount);
        booking.setRescheduleCount(booking.getRescheduleCount() + 1);
        clearArrivalAndIncidentState(booking);
        bookings.save(booking);
        resolve(request, customerId, "RESCHEDULED");
    }

    private com.housekeeping.management.entity.Booking lockCustomerBooking(Long bookingId, Long customerId) {
        var booking = bookings.lockEntity(bookingId)
                .orElseThrow(() -> new IllegalArgumentException("Lịch hẹn không tồn tại."));
        requireOwner(booking.getCustomerId(), customerId);
        return booking;
    }

    private BookingTeamChangeRequest lockPendingRequest(Long bookingId) {
        var request = requests.findFirstByBookingIdAndRequestStatusOrderByCreatedAtDesc(bookingId, "PENDING")
                .orElseThrow(() -> new IllegalStateException("Booking không còn yêu cầu xử lý nhóm đang chờ."));
        return requests.lockById(request.getRequestId())
                .filter(item -> "PENDING".equals(item.getRequestStatus()))
                .orElseThrow(() -> new IllegalStateException("Yêu cầu đổi nhóm vừa được xử lý. Tải lại booking."));
    }

    private void resolve(BookingTeamChangeRequest request, Long customerId, String resolution) {
        request.setRequestStatus("RESOLVED");
        request.setResolution(resolution);
        request.setResolvedAt(now());
        request.setResolvedBy(customerId);
        requests.save(request);
    }

    private void updatePriceForTeamSize(Long bookingId, int count, com.housekeeping.management.entity.Booking booking) {
        List<BookingDetail> lines = details.findAllByBookingIdOrderByBookingDetailId(bookingId);
        if (lines.isEmpty()) throw new IllegalStateException("Booking không có dịch vụ để tính lại giá.");
        booking.setTotalAmount(priceFor(lines, count));
        lines.forEach(line -> line.setStaffCountSnapshot(count));
        details.saveAll(lines);
        bookings.save(booking);
    }

    private static BigDecimal priceFor(List<BookingDetail> lines, int count) {
        return lines.stream().map(line -> line.getUnitPrice().add(line.getAdditionalStaffPriceSnapshot()
                .multiply(BigDecimal.valueOf(count - 1L)))).reduce(ZERO, BigDecimal::add);
    }

    private List<BookingStaffAssignment> activeTeam(Long bookingId) {
        return assignments.findAllByBookingIdAndStatusOrderByRoleAscBookingStaffIdAsc(
                bookingId, BookingStaffAssignmentStatus.ASSIGNED);
    }

    private BookingTeamChangeRequest requirePendingRequest(Long bookingId) {
        return requests.findFirstByBookingIdAndRequestStatusOrderByCreatedAtDesc(bookingId, "PENDING")
                .orElseThrow(() -> new IllegalStateException("Booking không còn yêu cầu xử lý nhóm đang chờ."));
    }

    private static void requireOwner(Long ownerId, Long customerId) {
        if (ownerId == null || !ownerId.equals(customerId)) throw new AccessDeniedException("Lịch hẹn không thuộc tài khoản của bạn.");
    }

    private static void ensureUnstartedConfirmed(BookingStatus status, LocalDateTime workStartedAt) {
        if (status != BookingStatus.CONFIRMED) throw new IllegalStateException("Chỉ booking CONFIRMED mới được xử lý thay đổi nhóm.");
        if (workStartedAt != null) throw new IllegalStateException("Công việc đã bắt đầu nên không thể thay đổi nhóm hoặc lịch.");
    }

    private static void clearArrivalAndIncidentState(com.housekeeping.management.entity.Booking booking) {
        booking.setCustomerArrivedAt(null);
        booking.setStaffArrivedAt(null);
        booking.setCustomerDelayReportedAt(null);
        booking.setAdminNoShowVerifiedAt(null);
        booking.setAdminNoShowVerifiedBy(null);
        booking.setAdminNoShowNote(null);
        booking.setCustomerWaitedAt(null);
        booking.setArrivalVerifiedAt(null);
        booking.setArrivalVerificationRecordedAt(null);
        booking.setArrivalVerifiedBy(null);
        booking.setArrivalVerificationSource(null);
        booking.setArrivalVerificationNote(null);
        booking.setIncidentReportedAt(null);
        booking.setIncidentNote(null);
        booking.setIncidentExpectedArrivalAt(null);
        booking.setIncidentCustomerResponse(null);
        booking.setIncidentCustomerRespondedAt(null);
        booking.setWorkStartedAt(null);
        booking.setServiceCompletedAt(null);
        booking.setDelayMinutes(0);
        booking.setDelayDiscountPercent(ZERO);
        booking.setDelayDiscountAmount(ZERO);
    }

    private static LocalDateTime now() { return LocalDateTime.now(BUSINESS_OFFSET).withNano(0); }
}
