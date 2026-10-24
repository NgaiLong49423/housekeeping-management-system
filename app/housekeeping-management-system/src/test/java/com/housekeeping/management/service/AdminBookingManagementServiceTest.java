package com.housekeeping.management.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.housekeeping.management.domain.StaffType;
import com.housekeeping.management.domain.UserRole;
import com.housekeeping.management.domain.UserStatus;
import com.housekeeping.management.entity.Staff;
import com.housekeeping.management.entity.User;
import com.housekeeping.management.repository.BookingPersistenceRepository;
import com.housekeeping.management.repository.StaffAccountRepository;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AdminBookingManagementServiceTest {
    private final BookingPersistenceRepository bookings = mock(BookingPersistenceRepository.class);
    private final StaffAccountRepository staffAccounts = mock(StaffAccountRepository.class);
    private final AccountAuthorizationService authorization = mock(AccountAuthorizationService.class);
    private final AdminBookingManagementService service = new AdminBookingManagementService(bookings, staffAccounts, authorization);

    @BeforeEach
    void setup() {
        BookingPersistenceRepository.BookingState confirmed = new BookingPersistenceRepository.BookingState(
                51L, 4L, 12L, 22L, LocalDateTime.of(2026, 11, 1, 10, 0), LocalDateTime.of(2026, 11, 1, 12, 0), "CONFIRMED");
        when(bookings.findBooking(51L)).thenReturn(Optional.of(confirmed));
        when(bookings.lockBooking(51L)).thenReturn(Optional.of(confirmed));
    }

    @Test
    void adminCanCancelLateConfirmedBookingWithReason() {
        when(bookings.cancel(org.mockito.ArgumentMatchers.eq(51L), org.mockito.ArgumentMatchers.eq(99L),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any())).thenReturn(1);
        service.cancelConfirmedBooking(51L, 99L, "  Không thể bố trí nhân sự  ");

        verify(bookings).cancel(org.mockito.ArgumentMatchers.eq(51L), org.mockito.ArgumentMatchers.eq(99L),
                org.mockito.ArgumentMatchers.eq("Không thể bố trí nhân sự"), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void adminCannotCancelAwaitingPaymentOrCompletedBooking() {
        when(bookings.lockBooking(51L)).thenReturn(Optional.of(new BookingPersistenceRepository.BookingState(
                51L, 4L, 12L, 22L, LocalDateTime.now(), LocalDateTime.now().plusHours(2), "AWAITING_PAYMENT")));

        assertThatThrownBy(() -> service.cancelConfirmedBooking(51L, 99L, "reason"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("CONFIRMED");
        verify(bookings, never()).cancel(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void reassignmentKeepsOriginalStaffAndRejectsConflictingReplacement() {
        when(staffAccounts.findActiveByStaffIdForUpdate(13L)).thenReturn(Optional.of(activeStaff(13L, StaffType.FULL_TIME)));
        when(bookings.companyAcceptsStart(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any())).thenReturn(true);
        when(bookings.isOnApprovedLeave(13L, java.time.LocalDate.of(2026, 11, 1))).thenReturn(false);
        when(bookings.hasConflictingBookingExcept(13L, LocalDateTime.of(2026, 11, 1, 10, 0),
                LocalDateTime.of(2026, 11, 1, 12, 0), 51L)).thenReturn(true);

        assertThatThrownBy(() -> service.reassignConfirmedBooking(51L, 99L, 13L))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("trùng lịch");
        verify(bookings, never()).assignStaff(51L, 12L, 13L);
    }

    @Test
    void replacementMustBeAvailableAndOriginalAssignmentRemainsUntouchedUntilAllChecksPass() {
        when(staffAccounts.findActiveByStaffIdForUpdate(13L)).thenReturn(Optional.of(activeStaff(13L, StaffType.FULL_TIME)));
        when(bookings.companyAcceptsStart(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any())).thenReturn(true);
        when(bookings.isOnApprovedLeave(13L, java.time.LocalDate.of(2026, 11, 1))).thenReturn(true);

        assertThatThrownBy(() -> service.reassignConfirmedBooking(51L, 99L, 13L))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("duyệt nghỉ");
        verify(bookings, never()).assignStaff(51L, 12L, 13L);
    }

    @Test
    void eligibleReplacementChangesOnlyCurrentStaffAssignment() {
        when(staffAccounts.findActiveByStaffIdForUpdate(13L)).thenReturn(Optional.of(activeStaff(13L, StaffType.FULL_TIME)));
        when(bookings.companyAcceptsStart(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any())).thenReturn(true);
        when(bookings.isOnApprovedLeave(13L, java.time.LocalDate.of(2026, 11, 1))).thenReturn(false);
        when(bookings.hasConflictingBookingExcept(13L, LocalDateTime.of(2026, 11, 1, 10, 0),
                LocalDateTime.of(2026, 11, 1, 12, 0), 51L)).thenReturn(false);
        when(bookings.assignStaff(51L, 12L, 13L)).thenReturn(1);

        service.reassignConfirmedBooking(51L, 99L, 13L);

        verify(bookings).assignStaff(51L, 12L, 13L);
    }

    private static Staff activeStaff(long id, StaffType type) {
        User user = new User(); user.setRole(UserRole.STAFF); user.setStatus(UserStatus.ACTIVE);
        Staff staff = new Staff(); staff.setStaffId(id); staff.setUser(user); staff.setStaffType(type); return staff;
    }
}
