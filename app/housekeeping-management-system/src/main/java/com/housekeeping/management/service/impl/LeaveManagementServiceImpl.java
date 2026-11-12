package com.housekeeping.management.service.impl;

import com.housekeeping.management.service.LeaveManagementService;
import com.housekeeping.management.service.BookingReservationService;
import com.housekeeping.management.service.StaffManagementService;
import com.housekeeping.management.service.AuthService;

import com.housekeeping.management.enums.LeaveStatus;
import com.housekeeping.management.dto.request.PortalForms.LeaveRequestForm;
import com.housekeeping.management.dto.entity.StaffLeaveDTO;
import com.housekeeping.management.dto.response.PortalViews.LeaveCard;
import com.housekeeping.management.entity.Staff;
import com.housekeeping.management.entity.StaffLeave;
import com.housekeeping.management.repository.StaffLeaveRepository;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class LeaveManagementServiceImpl implements LeaveManagementService {
    private static final DateTimeFormatter CREATED_AT_FORMAT = DateTimeFormatter.ofPattern("HH:mm · dd/MM/yyyy");

    private final StaffLeaveRepository leaves;
    private final StaffManagementService staffManagement;
    private final AuthService auth;
    private final BookingReservationService bookings;

    public LeaveManagementServiceImpl(StaffLeaveRepository leaves, StaffManagementService staffManagement,
            AuthService auth, BookingReservationService bookings) {
        this.leaves = leaves;
        this.staffManagement = staffManagement;
        this.auth = auth;
        this.bookings = bookings;
    }

    public List<LeaveCard> findForStaff(Long staffId) {
        requireActiveStaff(staffId);
        return leaves.findAllByStaff_StaffIdOrderByCreatedAtDesc(staffId).stream()
                .map(request -> toCard(request, staffId, null))
                .toList();
    }

    public List<LeaveCard> findAllForAdmin(Long adminUserId) {
        auth.findActiveAdmin(adminUserId);
        return leaves.findAllForAdmin().stream()
                .map(request -> {
                    List<Long> affected = request.getStatus() == LeaveStatus.PENDING
                            ? bookings.futureConfirmedBookingIds(request.getStaff().getStaffId(), request.getStartDate(), request.getEndDate()) : List.of();
                    return toCard(request, request.getStaff().getStaffId(), request.getStaff().getUser().getFullName(), affected);
                })
                .toList();
    }

    @Transactional
    public void submitLeaveRequest(Long staffId, LeaveRequestForm form) {
        validateDateRange(form);
        Staff staff = staffManagement.findActiveForBookingUpdate(staffId);

        StaffLeaveDTO dto = new StaffLeaveDTO(null, null, form.startDate(), form.endDate(), form.reason().trim(),
                LeaveStatus.PENDING, businessNow(), null, null);
        StaffLeave request = dto.convertDTOToEntity();
        request.setStaff(staff);
        leaves.save(request);
    }

    @Transactional
    public void reviewLeaveRequest(Long leaveId, Long adminUserId, LeaveStatus decision) {
        if (decision != LeaveStatus.APPROVED && decision != LeaveStatus.REJECTED) {
            throw new IllegalArgumentException("Quyết định đơn nghỉ phải là APPROVED hoặc REJECTED.");
        }
        com.housekeeping.management.entity.User reviewer = auth.findActiveAdmin(adminUserId);
        StaffLeave initial = leaves.findById(leaveId)
                .orElseThrow(() -> new IllegalArgumentException("Đơn nghỉ không tồn tại."));
        staffManagement.findByStaffIdForUpdate(initial.getStaff().getStaffId());
        StaffLeave request = leaves.findByIdForUpdate(leaveId)
                .orElseThrow(() -> new IllegalArgumentException("Đơn nghỉ không tồn tại."));
        if (request.getStatus() != LeaveStatus.PENDING) {
            throw new IllegalStateException("Chỉ có thể xét duyệt đơn đang ở trạng thái PENDING.");
        }
        if (decision == LeaveStatus.APPROVED) {
            List<Long> conflicts = bookings.futureConfirmedBookingIds(request.getStaff().getStaffId(), request.getStartDate(), request.getEndDate());
            if (!conflicts.isEmpty()) throw new IllegalStateException("Chưa thể duyệt nghỉ. Hãy đổi nhân viên hoặc hủy các Booking: " + conflicts);
        }
        request.setStatus(decision);
        request.setReviewedBy(reviewer.getUserId());
        request.setReviewedAt(businessNow());
    }

    private Staff requireActiveStaff(Long staffId) { return staffManagement.findActiveByStaffId(staffId); }

    private static void validateDateRange(LeaveRequestForm form) {
        if (form.startDate() == null || form.endDate() == null || form.reason() == null || form.reason().isBlank()) {
            throw new IllegalArgumentException("Ngày bắt đầu, ngày kết thúc và lý do nghỉ là bắt buộc.");
        }
        if (form.endDate().isBefore(form.startDate())) {
            throw new IllegalArgumentException("Ngày kết thúc không được trước ngày bắt đầu.");
        }
    }

    private static LeaveCard toCard(StaffLeave request, Long staffId, String staffName) { return toCard(request, staffId, staffName, List.of()); }
    private static LeaveCard toCard(StaffLeave request, Long staffId, String staffName, List<Long> conflicts) {
        LocalDateTime createdAt = request.getCreatedAt();
        return new LeaveCard(request.getLeaveId(), staffId, staffName, request.getStartDate(), request.getEndDate(),
                request.getReason(), request.getStatus().name(), createdAt == null ? "" : createdAt.format(CREATED_AT_FORMAT), conflicts);
    }

    private static LocalDateTime businessNow() {
        return OffsetDateTime.now(ZoneOffset.ofHours(7)).withNano(0).toLocalDateTime();
    }
}
