package com.housekeeping.management.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.housekeeping.management.entity.Booking;
import com.housekeeping.management.entity.BookingDetail;
import com.housekeeping.management.entity.BookingStaffAssignment;
import com.housekeeping.management.entity.BookingTeamChangeRequest;
import com.housekeeping.management.enums.BookingStaffAssignmentStatus;
import com.housekeeping.management.enums.BookingStaffRole;
import com.housekeeping.management.enums.BookingStatus;
import com.housekeeping.management.enums.StaffType;
import com.housekeeping.management.enums.TravelStatus;
import com.housekeeping.management.repository.BookingDetailRepository;
import com.housekeeping.management.repository.BookingPersistenceRepository;
import com.housekeeping.management.repository.BookingStaffAssignmentRepository;
import com.housekeeping.management.repository.BookingTeamChangeRequestRepository;
import com.housekeeping.management.repository.CancellationLogRepository;
import com.housekeeping.management.service.impl.BookingTeamChangeServiceImpl;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class BookingTeamChangeServiceTest {
    private static final Long BOOKING_ID = 42L;
    private static final Long CUSTOMER_ID = 7L;
    private static final Long ADMIN_ID = 9L;

    @Test
    void adminMayRequestRemovalOfNonLeadAndCreatesPendingChoice() {
        var bookings = mock(BookingPersistenceRepository.class);
        var assignments = mock(BookingStaffAssignmentRepository.class);
        var requests = mock(BookingTeamChangeRequestRepository.class);
        var booking = booking();
        var lead = member(11L, BookingStaffRole.LEAD);
        var removed = member(12L, BookingStaffRole.MEMBER);
        when(bookings.lockEntity(BOOKING_ID)).thenReturn(Optional.of(booking));
        when(requests.findFirstByBookingIdAndRequestStatusOrderByCreatedAtDesc(BOOKING_ID, "PENDING"))
                .thenReturn(Optional.empty());
        when(assignments.findAllByBookingIdAndStatusOrderByRoleAscBookingStaffIdAsc(BOOKING_ID,
                BookingStaffAssignmentStatus.ASSIGNED)).thenReturn(List.of(lead, removed));

        service(bookings, assignments, requests, mock(BookingDetailRepository.class), mock(CancellationLogRepository.class),
                mock(AccountAuthorizationService.class), mock(StaffManagementService.class), mock(CompanySettingsService.class),
                mock(BookingReservationService.class), mock(HouseService.class)).requestReduction(BOOKING_ID, ADMIN_ID, 12L, "Nhân viên báo nghỉ.");

        assertThat(removed.getStatus()).isEqualTo(BookingStaffAssignmentStatus.REMOVED);
        ArgumentCaptor<BookingTeamChangeRequest> saved = ArgumentCaptor.forClass(BookingTeamChangeRequest.class);
        verify(requests).save(saved.capture());
        assertThat(saved.getValue().getRequestStatus()).isEqualTo("PENDING");
        assertThat(saved.getValue().getProposedStaffCount()).isEqualTo(1);
        verify(assignments).saveAndFlush(removed);
    }

    @Test
    void adminPromotesAnAvailableMemberBeforeRemovingMissingLead() {
        var bookings = mock(BookingPersistenceRepository.class);
        var assignments = mock(BookingStaffAssignmentRepository.class);
        var requests = mock(BookingTeamChangeRequestRepository.class);
        var booking = booking();
        var lead = member(11L, BookingStaffRole.LEAD);
        var member = member(12L, BookingStaffRole.MEMBER);
        when(bookings.lockEntity(BOOKING_ID)).thenReturn(Optional.of(booking));
        when(requests.findFirstByBookingIdAndRequestStatusOrderByCreatedAtDesc(BOOKING_ID, "PENDING"))
                .thenReturn(Optional.empty());
        when(assignments.findAllByBookingIdAndStatusOrderByRoleAscBookingStaffIdAsc(BOOKING_ID,
                BookingStaffAssignmentStatus.ASSIGNED)).thenReturn(List.of(lead, member));
        var service = service(bookings, assignments, requests, mock(BookingDetailRepository.class),
                mock(CancellationLogRepository.class), mock(AccountAuthorizationService.class),
                mock(StaffManagementService.class), mock(CompanySettingsService.class), mock(BookingReservationService.class), mock(HouseService.class));

        service.requestReduction(BOOKING_ID, ADMIN_ID, 11L, "Trưởng nhóm báo nghỉ.");

        assertThat(booking.getStaffId()).isEqualTo(12L);
        assertThat(member.getRole()).isEqualTo(BookingStaffRole.LEAD);
        assertThat(lead.getStatus()).isEqualTo(BookingStaffAssignmentStatus.REMOVED);
        verify(bookings).save(booking);
        verify(requests).save(any());
    }

    @Test
    void adminMayRequestFreeCancelOrRescheduleWhenRemovingOnlyStaff() {
        var bookings = mock(BookingPersistenceRepository.class);
        var assignments = mock(BookingStaffAssignmentRepository.class);
        var requests = mock(BookingTeamChangeRequestRepository.class);
        when(bookings.lockEntity(BOOKING_ID)).thenReturn(Optional.of(booking()));
        when(requests.findFirstByBookingIdAndRequestStatusOrderByCreatedAtDesc(BOOKING_ID, "PENDING"))
                .thenReturn(Optional.empty());
        var onlyStaff = member(11L, BookingStaffRole.LEAD);
        when(assignments.findAllByBookingIdAndStatusOrderByRoleAscBookingStaffIdAsc(BOOKING_ID,
                BookingStaffAssignmentStatus.ASSIGNED)).thenReturn(List.of(onlyStaff));
        var service = service(bookings, assignments, requests, mock(BookingDetailRepository.class),
                mock(CancellationLogRepository.class), mock(AccountAuthorizationService.class),
                mock(StaffManagementService.class), mock(CompanySettingsService.class), mock(BookingReservationService.class), mock(HouseService.class));

        service.requestReduction(BOOKING_ID, ADMIN_ID, 11L, "Nhân viên duy nhất báo nghỉ.");

        assertThat(onlyStaff.getStatus()).isEqualTo(BookingStaffAssignmentStatus.REMOVED);
        ArgumentCaptor<BookingTeamChangeRequest> saved = ArgumentCaptor.forClass(BookingTeamChangeRequest.class);
        verify(requests).save(saved.capture());
        assertThat(saved.getValue().getProposedStaffCount()).isZero();
    }

    @Test
    void customerCanOpenRescheduleForBookingWithNoRemainingStaff() {
        var bookings = mock(BookingPersistenceRepository.class);
        var assignments = mock(BookingStaffAssignmentRepository.class);
        var requests = mock(BookingTeamChangeRequestRepository.class);
        var details = mock(BookingDetailRepository.class);
        var booking = booking();
        var request = pendingRequest(); request.setProposedStaffCount(0);
        var line = new BookingDetail(); line.setServiceId(10L); line.setServiceNameSnapshot("Dọn nhà");
        line.setUnitPrice(new BigDecimal("100.00")); line.setAdditionalStaffPriceSnapshot(new BigDecimal("20.00"));
        line.setDurationMinutes(60);
        when(bookings.findById(BOOKING_ID)).thenReturn(Optional.of(booking));
        when(requests.findFirstByBookingIdAndRequestStatusOrderByCreatedAtDesc(BOOKING_ID, "PENDING"))
                .thenReturn(Optional.of(request));
        when(assignments.findAllByBookingIdAndStatusOrderByRoleAscBookingStaffIdAsc(BOOKING_ID,
                BookingStaffAssignmentStatus.ASSIGNED)).thenReturn(List.of());
        when(details.findAllByBookingIdOrderByBookingDetailId(BOOKING_ID)).thenReturn(List.of(line));
        var service = service(bookings, assignments, requests, details, mock(CancellationLogRepository.class),
                mock(AccountAuthorizationService.class), mock(StaffManagementService.class),
                mock(CompanySettingsService.class), mock(BookingReservationService.class), mock(HouseService.class));

        var info = service.rescheduleInfo(BOOKING_ID, CUSTOMER_ID);

        assertThat(info.staffIds()).isEmpty();
        assertThat(info.leadStaffId()).isNull();
        assertThat(info.serviceIds()).containsExactly(10L);
    }

    @Test
    void acceptingSmallerTeamRecalculatesAmountFromBookingSnapshots() {
        var bookings = mock(BookingPersistenceRepository.class);
        var assignments = mock(BookingStaffAssignmentRepository.class);
        var requests = mock(BookingTeamChangeRequestRepository.class);
        var details = mock(BookingDetailRepository.class);
        var booking = booking(); booking.setTotalAmount(new BigDecimal("150.00"));
        var request = pendingRequest();
        var line = new BookingDetail(); line.setUnitPrice(new BigDecimal("100.00"));
        line.setAdditionalStaffPriceSnapshot(new BigDecimal("50.00")); line.setStaffCountSnapshot(2);
        when(bookings.lockEntity(BOOKING_ID)).thenReturn(Optional.of(booking));
        when(requests.findFirstByBookingIdAndRequestStatusOrderByCreatedAtDesc(BOOKING_ID, "PENDING"))
                .thenReturn(Optional.of(request));
        when(requests.lockById(71L)).thenReturn(Optional.of(request));
        when(assignments.findAllByBookingIdAndStatusOrderByRoleAscBookingStaffIdAsc(BOOKING_ID,
                BookingStaffAssignmentStatus.ASSIGNED)).thenReturn(List.of(member(11L, BookingStaffRole.LEAD)));
        when(details.findAllByBookingIdOrderByBookingDetailId(BOOKING_ID)).thenReturn(List.of(line));
        var service = service(bookings, assignments, requests, details, mock(CancellationLogRepository.class),
                mock(AccountAuthorizationService.class), mock(StaffManagementService.class), mock(CompanySettingsService.class),
                mock(BookingReservationService.class), mock(HouseService.class));

        service.proceedWithFewerStaff(BOOKING_ID, CUSTOMER_ID);

        assertThat(booking.getTotalAmount()).isEqualByComparingTo("100.00");
        assertThat(line.getStaffCountSnapshot()).isEqualTo(1);
        assertThat(request.getRequestStatus()).isEqualTo("RESOLVED");
        assertThat(request.getResolution()).isEqualTo("PROCEEDED_SMALLER");
    }

    @Test
    void cancelingBecauseTeamCannotBeFilledIsFreeAndAudited() {
        var bookings = mock(BookingPersistenceRepository.class);
        var assignments = mock(BookingStaffAssignmentRepository.class);
        var requests = mock(BookingTeamChangeRequestRepository.class);
        var cancellationLogs = mock(CancellationLogRepository.class);
        var booking = booking();
        var request = pendingRequest();
        when(bookings.lockEntity(BOOKING_ID)).thenReturn(Optional.of(booking));
        when(requests.findFirstByBookingIdAndRequestStatusOrderByCreatedAtDesc(BOOKING_ID, "PENDING"))
                .thenReturn(Optional.of(request));
        when(requests.lockById(71L)).thenReturn(Optional.of(request));
        var service = service(bookings, assignments, requests, mock(BookingDetailRepository.class), cancellationLogs,
                mock(AccountAuthorizationService.class), mock(StaffManagementService.class), mock(CompanySettingsService.class),
                mock(BookingReservationService.class), mock(HouseService.class));

        service.cancelForTeamChange(BOOKING_ID, CUSTOMER_ID);

        assertThat(booking.getStatus()).isEqualTo(BookingStatus.CANCELLED);
        assertThat(booking.getCancellationFee()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(booking.getRefundAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(request.getResolution()).isEqualTo("CANCELLED");
        verify(cancellationLogs).save(any());
        verify(bookings).save(booking);
    }

    @Test
    void reschedulingUpdatesTheSameBookingAndKeepsOriginalStaffAndServices() {
        var bookings = mock(BookingPersistenceRepository.class);
        var assignments = mock(BookingStaffAssignmentRepository.class);
        var requests = mock(BookingTeamChangeRequestRepository.class);
        var details = mock(BookingDetailRepository.class);
        var staffManagement = mock(StaffManagementService.class);
        var company = mock(CompanySettingsService.class);
        var reservations = mock(BookingReservationService.class);
        var houses = mock(HouseService.class);
        var booking = booking();
        booking.setOriginalStaffId(11L); booking.setTotalDurationMinutes(60);
        booking.setAddressSnapshot("12 Nguyen Hue");
        booking.setLatitudeSnapshot(new BigDecimal("10.2")); booking.setLongitudeSnapshot(new BigDecimal("106.2"));
        var request = pendingRequest();
        var detail = new BookingDetail(); detail.setUnitPrice(new BigDecimal("120.00"));
        detail.setAdditionalStaffPriceSnapshot(new BigDecimal("35.00")); detail.setDurationMinutes(60);
        var candidate = new com.housekeeping.management.entity.Staff(); candidate.setStaffType(StaffType.FULL_TIME);
        LocalDateTime newStart = LocalDateTime.now(ZoneOffset.ofHours(7)).plusDays(1).withHour(10).withMinute(0)
                .withSecond(0).withNano(0);
        when(bookings.findById(BOOKING_ID)).thenReturn(Optional.of(booking));
        when(bookings.lockEntity(BOOKING_ID)).thenReturn(Optional.of(booking));
        when(requests.findFirstByBookingIdAndRequestStatusOrderByCreatedAtDesc(BOOKING_ID, "PENDING"))
                .thenReturn(Optional.of(request));
        when(requests.lockById(71L)).thenReturn(Optional.of(request));
        when(staffManagement.findActiveForBookingUpdate(12L)).thenReturn(candidate);
        when(assignments.findAllByBookingIdAndStatusOrderByRoleAscBookingStaffIdAsc(BOOKING_ID,
                BookingStaffAssignmentStatus.ASSIGNED)).thenReturn(List.of(member(11L, BookingStaffRole.LEAD)));
        when(details.findAllByBookingIdOrderByBookingDetailId(BOOKING_ID)).thenReturn(List.of(detail));
        when(company.acceptsInterval(newStart.toLocalDate(), newStart.toLocalTime(), newStart.plusMinutes(60).toLocalTime()))
                .thenReturn(true);
        when(houses.isWithinServiceRadius(any(), any())).thenReturn(true);
        when(reservations.assessTravel(eq(12L), eq(newStart), eq(newStart.plusMinutes(60)), any(), any(), eq(BOOKING_ID)))
                .thenReturn(new BookingReservationService.TravelAssessment(TravelStatus.FEASIBLE, "ok", null, null, 0, 0, 0, null));
        var service = service(bookings, assignments, requests, details, mock(CancellationLogRepository.class),
                mock(AccountAuthorizationService.class), staffManagement, company, reservations, houses);

        service.reschedule(BOOKING_ID, CUSTOMER_ID, List.of(12L), 12L, newStart);

        assertThat(booking.getBookingId()).isEqualTo(BOOKING_ID);
        assertThat(booking.getOriginalStaffId()).isEqualTo(11L);
        assertThat(booking.getStaffId()).isEqualTo(12L);
        assertThat(booking.getStartTime()).isEqualTo(newStart);
        assertThat(booking.getTotalAmount()).isEqualByComparingTo("120.00");
        assertThat(booking.getRescheduleCount()).isEqualTo(1);
        assertThat(detail.getStaffCountSnapshot()).isEqualTo(1);
        assertThat(request.getResolution()).isEqualTo("RESCHEDULED");
    }

    private static BookingTeamChangeServiceImpl service(BookingPersistenceRepository bookings,
            BookingStaffAssignmentRepository assignments, BookingTeamChangeRequestRepository requests,
            BookingDetailRepository details, CancellationLogRepository cancellationLogs,
            AccountAuthorizationService authorization, StaffManagementService staffManagement,
            CompanySettingsService company, BookingReservationService reservations, HouseService houses) {
        return new BookingTeamChangeServiceImpl(bookings, assignments, requests, details, cancellationLogs,
                authorization, staffManagement, company, reservations, houses);
    }

    private static Booking booking() {
        var booking = new Booking(); booking.setBookingId(BOOKING_ID); booking.setCustomerId(CUSTOMER_ID);
        booking.setStaffId(11L); booking.setHouseId(50L); booking.setStatus(BookingStatus.CONFIRMED);
        booking.setStartTime(LocalDateTime.now(ZoneOffset.ofHours(7)).plusDays(2));
        booking.setEndTime(booking.getStartTime().plusHours(1)); booking.setTotalDurationMinutes(60);
        booking.setTotalAmount(new BigDecimal("100.00")); booking.setCreatedAt(LocalDateTime.now(ZoneOffset.ofHours(7)));
        return booking;
    }

    private static BookingStaffAssignment member(Long staffId, BookingStaffRole role) {
        var member = new BookingStaffAssignment(); member.setBookingId(BOOKING_ID); member.setStaffId(staffId);
        member.setRole(role); member.setStatus(BookingStaffAssignmentStatus.ASSIGNED);
        member.setAssignedAt(LocalDateTime.now(ZoneOffset.ofHours(7)));
        return member;
    }

    private static BookingTeamChangeRequest pendingRequest() {
        var request = new BookingTeamChangeRequest(); request.setRequestId(71L); request.setBookingId(BOOKING_ID);
        request.setRequestedBy(ADMIN_ID); request.setAffectedStaffId(12L); request.setProposedStaffCount(1);
        request.setReason("Không tìm được người thay thế."); request.setRequestStatus("PENDING");
        request.setCreatedAt(LocalDateTime.now(ZoneOffset.ofHours(7)));
        return request;
    }
}
