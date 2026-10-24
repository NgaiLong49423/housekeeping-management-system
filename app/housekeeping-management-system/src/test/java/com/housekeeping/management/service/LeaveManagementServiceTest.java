package com.housekeeping.management.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.housekeeping.management.domain.LeaveStatus;
import com.housekeeping.management.domain.StaffType;
import com.housekeeping.management.domain.UserRole;
import com.housekeeping.management.domain.UserStatus;
import com.housekeeping.management.dto.request.PortalForms.LeaveRequestForm;
import com.housekeeping.management.entity.Staff;
import com.housekeeping.management.entity.StaffLeave;
import com.housekeeping.management.entity.User;
import com.housekeeping.management.repository.StaffAccountRepository;
import com.housekeeping.management.repository.StaffLeaveRepository;
import com.housekeeping.management.repository.UserRepository;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

class LeaveManagementServiceTest {
    private final StaffLeaveRepository leaves = mock(StaffLeaveRepository.class);
    private final StaffAccountRepository staffAccounts = mock(StaffAccountRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final LeaveManagementService service = new LeaveManagementService(leaves, staffAccounts, users);
    private final Staff staff = activeStaff();
    private final User admin = activeAdmin();

    @BeforeEach
    void setUp() {
        when(staffAccounts.findActiveByStaffIdForUpdate(7L)).thenReturn(Optional.of(staff));
        when(staffAccounts.findByStaffIdForUpdate(7L)).thenReturn(Optional.of(staff));
        when(users.findById(3L)).thenReturn(Optional.of(admin));
    }

    @Test
    void staffSubmitsWholeDayRangeAsPendingWithoutChangingAvailabilityImmediately() {
        service.submitLeaveRequest(7L, new LeaveRequestForm(
                LocalDate.of(2026, 11, 3), LocalDate.of(2026, 11, 4), "  Personal matter  "));

        var captor = org.mockito.ArgumentCaptor.forClass(StaffLeave.class);
        verify(leaves).save(captor.capture());
        StaffLeave saved = captor.getValue();
        assertThat(saved.getStaff()).isSameAs(staff);
        assertThat(saved.getStartDate()).isEqualTo(LocalDate.of(2026, 11, 3));
        assertThat(saved.getEndDate()).isEqualTo(LocalDate.of(2026, 11, 4));
        assertThat(saved.getReason()).isEqualTo("Personal matter");
        assertThat(saved.getStatus()).isEqualTo(LeaveStatus.PENDING);
        assertThat(saved.getReviewedBy()).isNull();
        assertThat(saved.getReviewedAt()).isNull();
    }

    @Test
    void rejectsReverseDateRangeBeforePersisting() {
        assertThatThrownBy(() -> service.submitLeaveRequest(7L, new LeaveRequestForm(
                LocalDate.of(2026, 11, 4), LocalDate.of(2026, 11, 3), "Personal matter")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Ngày kết thúc không được trước ngày bắt đầu.");
        verify(leaves, never()).save(any());
    }

    @Test
    void adminApprovalRecordsReviewerAndReviewTimeOnPendingRequest() {
        StaffLeave request = pendingLeave();
        when(leaves.findById(21L)).thenReturn(Optional.of(request));
        when(leaves.findByIdForUpdate(21L)).thenReturn(Optional.of(request));

        service.reviewLeaveRequest(21L, 3L, LeaveStatus.APPROVED);

        assertThat(request.getStatus()).isEqualTo(LeaveStatus.APPROVED);
        assertThat(request.getReviewedBy()).isEqualTo(3L);
        assertThat(request.getReviewedAt()).isNotNull();
        verify(staffAccounts).findByStaffIdForUpdate(7L);
    }

    @Test
    void inactiveOrNonAdminReviewerCannotDecideLeave() {
        User customer = new User();
        customer.setRole(UserRole.CUSTOMER);
        customer.setStatus(UserStatus.ACTIVE);
        when(users.findById(4L)).thenReturn(Optional.of(customer));

        assertThatThrownBy(() -> service.reviewLeaveRequest(21L, 4L, LeaveStatus.REJECTED))
                .isInstanceOf(AccessDeniedException.class);
        verify(leaves, never()).findById(21L);
    }

    @Test
    void alreadyReviewedRequestCannotBeChangedAgain() {
        StaffLeave request = pendingLeave();
        request.setStatus(LeaveStatus.REJECTED);
        when(leaves.findById(21L)).thenReturn(Optional.of(request));
        when(leaves.findByIdForUpdate(21L)).thenReturn(Optional.of(request));

        assertThatThrownBy(() -> service.reviewLeaveRequest(21L, 3L, LeaveStatus.APPROVED))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Chỉ có thể xét duyệt đơn đang ở trạng thái PENDING.");
        assertThat(request.getStatus()).isEqualTo(LeaveStatus.REJECTED);
    }

    private static StaffLeave pendingLeave() {
        StaffLeave request = new StaffLeave();
        request.setLeaveId(21L);
        request.setStaff(activeStaff());
        request.setStartDate(LocalDate.of(2026, 11, 3));
        request.setEndDate(LocalDate.of(2026, 11, 4));
        request.setReason("Personal matter");
        request.setStatus(LeaveStatus.PENDING);
        return request;
    }

    private static Staff activeStaff() {
        User user = new User();
        user.setStatus(UserStatus.ACTIVE);
        user.setRole(UserRole.STAFF);
        Staff staff = new Staff();
        staff.setStaffId(7L);
        staff.setUser(user);
        staff.setStaffType(StaffType.PART_TIME);
        return staff;
    }

    private static User activeAdmin() {
        User user = new User();
        user.setUserId(3L);
        user.setStatus(UserStatus.ACTIVE);
        user.setRole(UserRole.ADMIN);
        return user;
    }
}
