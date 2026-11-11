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
import com.housekeeping.management.entity.House;
import com.housekeeping.management.repository.BookingPersistenceRepository;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AdminBookingManagementServiceTest {
    private final BookingPersistenceRepository bookings = mock(BookingPersistenceRepository.class);
    private final StaffManagementService staffManagement = mock(StaffManagementService.class);
    private final AccountAuthorizationService authorization = mock(AccountAuthorizationService.class);
    private final BookingReservationService reservations = mock(BookingReservationService.class);
    private final HouseService houses = mock(HouseService.class);
    private final CompanySettingsService companySettings = mock(CompanySettingsService.class);
    private final AdminBookingManagementService service = new com.housekeeping.management.service.impl.AdminBookingManagementServiceImpl(bookings, staffManagement, authorization, reservations, houses, companySettings);

    @BeforeEach
    void setup() {
        BookingPersistenceRepository.BookingState confirmed = new BookingPersistenceRepository.BookingState(
                51L, 4L, 12L, 22L, LocalDateTime.of(2026, 11, 1, 10, 0), LocalDateTime.of(2026, 11, 1, 12, 0), "CONFIRMED", java.math.BigDecimal.TEN, java.math.BigDecimal.TEN);
        when(bookings.findBooking(51L)).thenReturn(Optional.of(confirmed));
        when(bookings.lockBooking(51L)).thenReturn(Optional.of(confirmed));
        when(houses.isWithinServiceRadius(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any())).thenReturn(true);
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
                51L, 4L, 12L, 22L, LocalDateTime.now(), LocalDateTime.now().plusHours(2), "AWAITING_PAYMENT", null, null)));

        assertThatThrownBy(() -> service.cancelConfirmedBooking(51L, 99L, "reason"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("CONFIRMED");
        verify(bookings, never()).cancel(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void reassignmentKeepsOriginalStaffAndRejectsConflictingReplacement() {
        when(staffManagement.findActiveForBookingUpdate(13L)).thenReturn(activeStaff(13L, StaffType.FULL_TIME));
        when(companySettings.acceptsInterval(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any())).thenReturn(true);
        when(staffManagement.isOnApprovedLeave(13L, java.time.LocalDate.of(2026, 11, 1))).thenReturn(false);
        when(companySettings.acceptsInterval(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any())).thenReturn(true);
        when(bookings.hasConflictingBookingExcept(13L, LocalDateTime.of(2026, 11, 1, 10, 0),
                LocalDateTime.of(2026, 11, 1, 12, 0), 51L, 60)).thenReturn(true);
        when(reservations.hasInsufficientTravelGap(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any())).thenReturn(true);
        when(reservations.assessTravel(org.mockito.ArgumentMatchers.eq(13L),org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.eq(51L))).thenReturn(new BookingReservationService.TravelAssessment(
                        BookingReservationService.TravelStatus.OVERLAP,"Nhân viên thay thế đã có booking trùng giờ.",
                        null,null,0,0,0,52L));

        assertThatThrownBy(() -> service.reassignConfirmedBooking(51L, 99L, 13L))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("trùng giờ");
        verify(bookings, never()).assignStaff(51L, 12L, 13L);
    }

    @Test
    void replacementMustBeAvailableAndOriginalAssignmentRemainsUntouchedUntilAllChecksPass() {
        when(staffManagement.findActiveForBookingUpdate(13L)).thenReturn(activeStaff(13L, StaffType.FULL_TIME));
        when(companySettings.acceptsInterval(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any())).thenReturn(true);
        when(staffManagement.isOnApprovedLeave(13L, java.time.LocalDate.of(2026, 11, 1))).thenReturn(true);

        assertThatThrownBy(() -> service.reassignConfirmedBooking(51L, 99L, 13L))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("duyệt nghỉ");
        verify(bookings, never()).assignStaff(51L, 12L, 13L);
    }

    @Test
    void eligibleReplacementChangesOnlyCurrentStaffAssignment() {
        when(staffManagement.findActiveForBookingUpdate(13L)).thenReturn(activeStaff(13L, StaffType.FULL_TIME));
        when(companySettings.acceptsStart(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any())).thenReturn(true);
        when(staffManagement.isOnApprovedLeave(13L, java.time.LocalDate.of(2026, 11, 1))).thenReturn(false);
        when(companySettings.acceptsInterval(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any())).thenReturn(true);
        when(bookings.hasConflictingBookingExcept(13L, LocalDateTime.of(2026, 11, 1, 10, 0),
                LocalDateTime.of(2026, 11, 1, 12, 0), 51L, 60)).thenReturn(false);
        when(reservations.assessTravel(org.mockito.ArgumentMatchers.eq(13L),org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.eq(51L))).thenReturn(new BookingReservationService.TravelAssessment(
                        BookingReservationService.TravelStatus.FEASIBLE,"",null,null,0,0,0,null));
        when(bookings.assignStaff(51L, 12L, 13L)).thenReturn(1);

        service.reassignConfirmedBooking(51L, 99L, 13L);

        verify(bookings).assignStaff(51L, 12L, 13L);
    }

    private static Staff activeStaff(long id, StaffType type) {
        User user = new User(); user.setRole(UserRole.STAFF); user.setStatus(UserStatus.ACTIVE);
        Staff staff = new Staff(); staff.setStaffId(id); staff.setUser(user); staff.setStaffType(type); return staff;
    }
}
