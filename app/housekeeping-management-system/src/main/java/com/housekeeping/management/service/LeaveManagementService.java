package com.housekeeping.management.service;

import com.housekeeping.management.domain.LeaveStatus;
import com.housekeeping.management.domain.UserRole;
import com.housekeeping.management.domain.UserStatus;
import com.housekeeping.management.dto.request.PortalForms.LeaveRequestForm;
import com.housekeeping.management.dto.response.PortalViews.LeaveCard;
import com.housekeeping.management.entity.Staff;
import com.housekeeping.management.entity.StaffLeave;
import com.housekeeping.management.entity.User;
import com.housekeeping.management.repository.StaffAccountRepository;
import com.housekeeping.management.repository.StaffLeaveRepository;
import com.housekeeping.management.repository.UserRepository;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class LeaveManagementService {
    private static final DateTimeFormatter CREATED_AT_FORMAT = DateTimeFormatter.ofPattern("HH:mm · dd/MM/yyyy");

    private final StaffLeaveRepository leaves;
    private final StaffAccountRepository staffAccounts;
    private final UserRepository users;

    public LeaveManagementService(StaffLeaveRepository leaves, StaffAccountRepository staffAccounts, UserRepository users) {
        this.leaves = leaves;
        this.staffAccounts = staffAccounts;
        this.users = users;
    }

    public List<LeaveCard> findForStaff(Long staffId) {
        requireActiveStaff(staffId);
        return leaves.findAllByStaff_StaffIdOrderByCreatedAtDesc(staffId).stream()
                .map(request -> toCard(request, staffId, null))
                .toList();
    }

    public List<LeaveCard> findAllForAdmin(Long adminUserId) {
        requireActiveAdmin(adminUserId);
        return leaves.findAllForAdmin().stream()
                .map(request -> toCard(request, request.getStaff().getStaffId(), request.getStaff().getUser().getFullName()))
                .toList();
    }

    @Transactional
    public void submitLeaveRequest(Long staffId, LeaveRequestForm form) {
        validateDateRange(form);
        Staff staff = staffAccounts.findActiveByStaffIdForUpdate(staffId)
                .orElseThrow(() -> new IllegalArgumentException("Tài khoản nhân viên không còn hoạt động."));

        StaffLeave request = new StaffLeave();
        request.setStaff(staff);
        request.setStartDate(form.startDate());
        request.setEndDate(form.endDate());
        request.setReason(form.reason().trim());
        request.setStatus(LeaveStatus.PENDING);
        request.setCreatedAt(businessNow());
        leaves.save(request);
    }

    @Transactional
    public void reviewLeaveRequest(Long leaveId, Long adminUserId, LeaveStatus decision) {
        if (decision != LeaveStatus.APPROVED && decision != LeaveStatus.REJECTED) {
            throw new IllegalArgumentException("Quyết định đơn nghỉ phải là APPROVED hoặc REJECTED.");
        }
        User reviewer = requireActiveAdmin(adminUserId);
        StaffLeave initial = leaves.findById(leaveId)
                .orElseThrow(() -> new IllegalArgumentException("Đơn nghỉ không tồn tại."));
        staffAccounts.findByStaffIdForUpdate(initial.getStaff().getStaffId())
                .orElseThrow(() -> new IllegalArgumentException("Nhân viên của đơn nghỉ không tồn tại."));
        StaffLeave request = leaves.findByIdForUpdate(leaveId)
                .orElseThrow(() -> new IllegalArgumentException("Đơn nghỉ không tồn tại."));
        if (request.getStatus() != LeaveStatus.PENDING) {
            throw new IllegalStateException("Chỉ có thể xét duyệt đơn đang ở trạng thái PENDING.");
        }
        request.setStatus(decision);
        request.setReviewedBy(reviewer.getUserId());
        request.setReviewedAt(businessNow());
    }

    private Staff requireActiveStaff(Long staffId) {
        return staffAccounts.findById(staffId)
                .filter(staff -> staff.getUser().getStatus() == UserStatus.ACTIVE)
                .orElseThrow(() -> new IllegalArgumentException("Tài khoản nhân viên không còn hoạt động."));
    }

    private User requireActiveAdmin(Long adminUserId) {
        return users.findById(adminUserId)
                .filter(user -> user.getRole() == UserRole.ADMIN && user.getStatus() == UserStatus.ACTIVE)
                .orElseThrow(() -> new AccessDeniedException("Tài khoản Admin không còn hợp lệ."));
    }

    private static void validateDateRange(LeaveRequestForm form) {
        if (form.startDate() == null || form.endDate() == null || form.reason() == null || form.reason().isBlank()) {
            throw new IllegalArgumentException("Ngày bắt đầu, ngày kết thúc và lý do nghỉ là bắt buộc.");
        }
        if (form.endDate().isBefore(form.startDate())) {
            throw new IllegalArgumentException("Ngày kết thúc không được trước ngày bắt đầu.");
        }
    }

    private static LeaveCard toCard(StaffLeave request, Long staffId, String staffName) {
        LocalDateTime createdAt = request.getCreatedAt();
        return new LeaveCard(request.getLeaveId(), staffId, staffName, request.getStartDate(), request.getEndDate(),
                request.getReason(), request.getStatus().name(), createdAt == null ? "" : createdAt.format(CREATED_AT_FORMAT));
    }

    private static LocalDateTime businessNow() {
        return OffsetDateTime.now(ZoneOffset.ofHours(7)).withNano(0).toLocalDateTime();
    }
}
