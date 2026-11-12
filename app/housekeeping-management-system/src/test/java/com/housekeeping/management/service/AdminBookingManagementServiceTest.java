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
    private final BookingDelayCompensationSettingsService compensationSettings = mock(BookingDelayCompensationSettingsService.class);
    private final AdminBookingManagementService service = new com.housekeeping.management.service.impl.AdminBookingManagementServiceImpl(
            bookings, staffManagement, authorization, reservations, houses, companySettings, compensationSettings);

    @BeforeEach
    void setup() {
        BookingPersistenceRepository.BookingState confirmed = new BookingPersistenceRepository.BookingState(
                51L, 4L, 12L, 22L, LocalDateTime.of(2026, 11, 1, 10, 0), LocalDateTime.of(2026, 11, 1, 12, 0), "CONFIRMED", java.math.BigDecimal.TEN, java.math.BigDecimal.TEN);
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
                51L, 4L, 12L, 22L, LocalDateTime.now(), LocalDateTime.now().plusHours(2), "AWAITING_PAYMENT", null, null)));

        assertThatThrownBy(() -> service.cancelConfirmedBooking(51L, 99L, "reason"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("CONFIRMED");
        verify(bookings, never()).cancel(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void adminVerificationStoresActualArrivalAndCalculatedCompensation() {
        LocalDateTime now = LocalDateTime.now(java.time.ZoneOffset.ofHours(7)).withNano(0);
        LocalDateTime start = now.minusMinutes(40);
        LocalDateTime actualArrival = start.plusMinutes(20);
        var workflow = new BookingPersistenceRepository.BookingWorkflowState(51L, 4L, 12L, start,
                start.plusHours(1), "CONFIRMED", new java.math.BigDecimal("150000"), start.plusMinutes(25),
                null, null, null, null, null, null, null, java.math.BigDecimal.ZERO, java.math.BigDecimal.ZERO,
                start.plusMinutes(10), null, null, null, null, null);
        when(bookings.lockBookingWorkflow(51L)).thenReturn(Optional.of(workflow));
        when(compensationSettings.get()).thenReturn(java.util.List.of(
                new com.housekeeping.management.domain.BookingDelayCompensation.Tier(1, 15, 5),
                new com.housekeeping.management.domain.BookingDelayCompensation.Tier(16, 30, 10),
                new com.housekeeping.management.domain.BookingDelayCompensation.Tier(31, null, 20)));
        when(bookings.verifyDisputedArrival(org.mockito.ArgumentMatchers.eq(51L), org.mockito.ArgumentMatchers.eq(99L),
                org.mockito.ArgumentMatchers.eq(actualArrival), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.eq("Đã đối chiếu"), org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any())).thenReturn(1);

        service.verifyDisputedArrival(51L, 99L, actualArrival, "  Đã đối chiếu  ");

        verify(authorization).requireActiveAdmin(99L);
        verify(bookings).verifyDisputedArrival(org.mockito.ArgumentMatchers.eq(51L), org.mockito.ArgumentMatchers.eq(99L),
                org.mockito.ArgumentMatchers.eq(actualArrival), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.eq("Đã đối chiếu"), org.mockito.ArgumentMatchers.eq(20),
                org.mockito.ArgumentMatchers.eq(new java.math.BigDecimal("10")),
                org.mockito.ArgumentMatchers.eq(new java.math.BigDecimal("15000.00")));
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
