package com.housekeeping.management.service;

import com.housekeeping.management.enums.ServiceStatus;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.housekeeping.management.enums.StaffType;
import com.housekeeping.management.enums.UserRole;
import com.housekeeping.management.enums.UserStatus;
import com.housekeeping.management.dto.request.PortalForms;
import com.housekeeping.management.entity.Services;
import com.housekeeping.management.entity.Staff;
import com.housekeeping.management.entity.User;
import com.housekeeping.management.repository.ServicesRepository;
import com.housekeeping.management.repository.BookingPersistenceRepository;
import com.housekeeping.management.repository.StaffAccountRepository;
import com.housekeeping.management.repository.StaffAvailabilityRepository;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Optional;
import java.util.List;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

class ManagementServicesTest {
    @Test
    void staffAvailabilityFormRulesApplyTwoHourNoticeAndQuarterHourGrid() {
        StaffAvailabilityService service = new com.housekeeping.management.service.impl.StaffAvailabilityServiceImpl(
                mock(StaffManagementService.class), mock(StaffAvailabilityRepository.class),
                mock(CompanyOperatingHoursService.class), mock(BookingQueryService.class));
        var rules = service.formRules(List.of(60, 90), LocalDateTime.parse("2026-11-12T08:02:00"));

        assertThat(rules.minimumServiceDuration()).isEqualTo(60);
        assertThat(rules.firstAvailableMinute()).isEqualTo(615);
        assertThat(service.canRegisterToday(LocalDate.parse("2026-11-12"),
                List.of(new StaffAvailabilityService.AvailabilityWindow(LocalTime.of(10, 0), LocalTime.of(12, 0))), rules)).isTrue();
        assertThat(service.canRegisterToday(LocalDate.parse("2026-11-12"),
                List.of(new StaffAvailabilityService.AvailabilityWindow(LocalTime.of(10, 0), LocalTime.of(11, 0))), rules)).isFalse();
    }

    @Test
    void registrationCreatesOneCanonicalActiveCustomerIdentity() {
        EntityManager entities = mock(EntityManager.class);
        PasswordEncoder passwords = mock(PasswordEncoder.class);
        when(passwords.encode("secret")).thenReturn("bcrypt-hash");
        new com.housekeeping.management.service.impl.RegistrationServiceImpl(entities, unlockedEmails(), passwords).registerCustomer(
                new PortalForms.CustomerRegistrationForm(" Customer ", " Customer@Example.test ",
                        " 0900000000 ", "secret", "secret"));
        var captured = org.mockito.ArgumentCaptor.forClass(User.class);
        verify(entities).persist(captured.capture());
        assertThat(captured.getValue().getFullName()).isEqualTo("Customer");
        assertThat(captured.getValue().getEmail()).isEqualTo("customer@example.test");
        assertThat(captured.getValue().getPhone()).isEqualTo("0900000000");
        assertThat(captured.getValue().getPasswordHash()).isEqualTo("bcrypt-hash");
        assertThat(captured.getValue().getRole()).isEqualTo(UserRole.CUSTOMER);
        assertThat(captured.getValue().getStatus()).isEqualTo(UserStatus.ACTIVE);
    }

    @Test
    void staffCreationPersistsSharedUserAndOneToOneStaffType() {
        EntityManager entities = mock(EntityManager.class);
        PasswordEncoder passwords = mock(PasswordEncoder.class);
        when(passwords.encode("staff-secret")).thenReturn("staff-hash");
        StaffManagementService service = new com.housekeeping.management.service.impl.StaffManagementServiceImpl(entities, mock(StaffAccountRepository.class),
                mock(StaffAvailabilityRepository.class), mock(com.housekeeping.management.repository.StaffLeaveRepository.class),
                mock(BookingStaffEligibilityService.class),
                unlockedEmails(), passwords, mock(AccountAuthorizationService.class));
        service.create(99L, new PortalForms.StaffCreateForm("Staff Name", "staff@example.test", "staff-secret",
                "0900000001", StaffType.PART_TIME));
        var users = org.mockito.ArgumentCaptor.forClass(User.class);
        var staff = org.mockito.ArgumentCaptor.forClass(Staff.class);
        verify(entities).persist(users.capture());
        verify(entities).persist(staff.capture());
        assertThat(users.getValue().getRole()).isEqualTo(UserRole.STAFF);
        assertThat(staff.getValue().getUser()).isSameAs(users.getValue());
        assertThat(staff.getValue().getStaffType()).isEqualTo(StaffType.PART_TIME);
    }

    @Test
    void deactivationRetainsStaffAndServiceRowsAndAllowsReactivation() {
        StaffAccountRepository staffAccounts = mock(StaffAccountRepository.class);
        BookingStaffEligibilityService bookings = mock(BookingStaffEligibilityService.class);
        ServicesRepository services = mock(ServicesRepository.class);
        User user = new User(); user.setStatus(UserStatus.ACTIVE);
        Staff staff = new Staff(); staff.setUser(user);
        Services service = new Services(); service.setStatus(ServiceStatus.ACTIVE);
        when(staffAccounts.findByStaffIdForUpdate(9L)).thenReturn(Optional.of(staff));
        when(staffAccounts.findById(9L)).thenReturn(Optional.of(staff));
        when(services.findById(4L)).thenReturn(Optional.of(service));
        StaffManagementService staffManagement = new com.housekeeping.management.service.impl.StaffManagementServiceImpl(mock(EntityManager.class), staffAccounts,
                mock(StaffAvailabilityRepository.class), mock(com.housekeeping.management.repository.StaffLeaveRepository.class), bookings,
                mock(GlobalEmailUniquenessService.class), mock(PasswordEncoder.class), mock(AccountAuthorizationService.class));
        ServiceManagementService serviceManagement = new com.housekeeping.management.service.impl.ServiceManagementServiceImpl(services, mock(AccountAuthorizationService.class));
        staffManagement.deactivate(99L, 9L); serviceManagement.deactivate(99L, 4L);
        assertThat(user.getStatus()).isEqualTo(UserStatus.INACTIVE);
        assertThat(service.getStatus()).isEqualTo(ServiceStatus.INACTIVE);
        staffManagement.activate(99L, 9L); serviceManagement.activate(99L, 4L);
        assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(service.getStatus()).isEqualTo(ServiceStatus.ACTIVE);
    }

    @Test
    void staffCannotBeDeactivatedWhileUnfinishedBookingsRemain() {
        StaffAccountRepository staffAccounts = mock(StaffAccountRepository.class);
        BookingStaffEligibilityService bookings = mock(BookingStaffEligibilityService.class);
        User user = new User(); user.setStatus(UserStatus.ACTIVE);
        Staff staff = new Staff(); staff.setUser(user);
        when(staffAccounts.findByStaffIdForUpdate(9L)).thenReturn(Optional.of(staff));
        when(bookings.hasUnfinishedBookingForStaff(9L)).thenReturn(true);
        StaffManagementService service = new com.housekeeping.management.service.impl.StaffManagementServiceImpl(mock(EntityManager.class), staffAccounts,
                mock(StaffAvailabilityRepository.class), mock(com.housekeeping.management.repository.StaffLeaveRepository.class), bookings,
                mock(GlobalEmailUniquenessService.class), mock(PasswordEncoder.class), mock(AccountAuthorizationService.class));

        assertThatThrownBy(() -> service.deactivate(99L, 9L))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("chưa hoàn tất");

        assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
        verify(staffAccounts).findByStaffIdForUpdate(9L);
    }

    @Test
    void serviceSaveKeepsTheApprovedFieldsAndDefaultsNewServiceToActive() {
        ServicesRepository services = mock(ServicesRepository.class);
        new com.housekeeping.management.service.impl.ServiceManagementServiceImpl(services, mock(AccountAuthorizationService.class)).save(99L, null,
                new PortalForms.ServiceForm("  Dọn nhà  ", "  Mô tả  ", 90, new BigDecimal("125000.00")));
        var captured = org.mockito.ArgumentCaptor.forClass(Services.class);
        verify(services).saveAndFlush(captured.capture());
        assertThat(captured.getValue().getServiceName()).isEqualTo("Dọn nhà");
        assertThat(captured.getValue().getDescription()).isEqualTo("Mô tả");
        assertThat(captured.getValue().getDurationMinutes()).isEqualTo(90);
        assertThat(captured.getValue().getPrice()).isEqualByComparingTo("125000.00");
        assertThat(captured.getValue().getStatus()).isEqualTo(ServiceStatus.ACTIVE);
    }

    @Test
    void serviceSaveNormalizesBlankDescriptionToNull() {
        ServicesRepository services = mock(ServicesRepository.class);
        new com.housekeeping.management.service.impl.ServiceManagementServiceImpl(services, mock(AccountAuthorizationService.class)).save(99L, null,
                new PortalForms.ServiceForm("Dọn nhà", "   ", 90, new BigDecimal("125000.00")));
        var captured = org.mockito.ArgumentCaptor.forClass(Services.class);
        verify(services).saveAndFlush(captured.capture());
        assertThat(captured.getValue().getDescription()).isNull();
    }

    @Test
    void partTimeAvailabilityRejectsAnOverlappingInterval() {
        LocalDate workDate = LocalDate.now(java.time.ZoneId.of("Asia/Ho_Chi_Minh")).plusDays(5);
        StaffAccountRepository staffAccounts = mock(StaffAccountRepository.class);
        StaffAvailabilityRepository availability = mock(StaffAvailabilityRepository.class);
        Staff staff = new Staff(); staff.setStaffType(StaffType.PART_TIME);
        staff.setStaffId(8L);
        when(staffAccounts.findActiveByUserIdForUpdate(800L)).thenReturn(Optional.of(staff));
        var existing = new com.housekeeping.management.entity.StaffAvailability();
        existing.setAvailabilityId(25L); existing.setStartTime(LocalTime.of(10, 0));
        existing.setEndTime(LocalTime.of(13, 0)); existing.setActive(true);
        when(availability.findAllByStaffIdAndWorkDateAndActiveTrue(8L, workDate)).thenReturn(List.of(existing));
        var settings = mock(CompanyOperatingHoursService.class);
        when(settings.forDate(workDate)).thenReturn(java.util.stream.IntStream.rangeClosed(1,7).mapToObj(day -> new PortalForms.OperatingHoursForm(day, false, LocalTime.of(6,0), LocalTime.of(21,0))).toList());
        StaffAvailabilityService service = availabilityService(staffAccounts, availability, settings);
        assertThatThrownBy(() -> service.save(800L, null, new PortalForms.AvailabilityForm(
                workDate, LocalTime.of(9, 0), LocalTime.of(12, 0))))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("bị chồng");
        org.mockito.Mockito.verify(availability, org.mockito.Mockito.never()).insert(any(), any(), any(), any());
    }

    @Test
    void partTimeStaffCannotChangeAvailabilityThatContainsAConfirmedBookingStart() {
        StaffAccountRepository staffAccounts = mock(StaffAccountRepository.class);
        StaffAvailabilityRepository availability = mock(StaffAvailabilityRepository.class);
        Staff staff = new Staff();
        staff.setStaffType(StaffType.PART_TIME);
        staff.setStaffId(8L);
        when(staffAccounts.findActiveByUserIdForUpdate(800L)).thenReturn(Optional.of(staff));
        StaffAvailabilityService service = availabilityService(staffAccounts, availability, mock(CompanyOperatingHoursService.class));

        assertThatThrownBy(() -> service.save(800L, 25L, new PortalForms.AvailabilityForm(
                LocalDate.of(2026, 11, 3), LocalTime.of(10, 0), LocalTime.of(13, 0))))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("thay thế");

        verify(availability, never()).insert(any(), any(), any(), any());
    }

    @Test
    void partTimeStaffCanRegisterTheSameHoursAcrossSelectedWeekdaysInOneBatch() {
        StaffAccountRepository staffAccounts = mock(StaffAccountRepository.class);
        StaffAvailabilityRepository availability = mock(StaffAvailabilityRepository.class);
        var settings = mock(CompanyOperatingHoursService.class);
        Staff staff = new Staff(); staff.setStaffType(StaffType.PART_TIME); staff.setStaffId(8L);
        when(staffAccounts.findActiveByUserIdForUpdate(800L)).thenReturn(Optional.of(staff));
        when(settings.forDate(any())).thenReturn(java.util.stream.IntStream.rangeClosed(1, 7)
                .mapToObj(day -> new PortalForms.OperatingHoursForm(day, false, LocalTime.of(6, 0), LocalTime.of(21, 0))).toList());
        when(availability.insert(eq(8L), any(), eq(LocalTime.of(9, 0)), eq(LocalTime.of(12, 0)))).thenReturn(1);
        StaffAvailabilityService service = availabilityService(staffAccounts, availability, settings);
        LocalDate monday = LocalDate.now(java.time.ZoneId.of("Asia/Ho_Chi_Minh"));
        while (monday.getDayOfWeek() != java.time.DayOfWeek.MONDAY) monday = monday.plusDays(1);

        int inserted = service.saveBatch(800L, new PortalForms.AvailabilityBatchForm(monday, monday.plusDays(6),
                List.of(1, 3, 5), LocalTime.of(9, 0), LocalTime.of(12, 0)));

        assertThat(inserted).isEqualTo(3);
        verify(availability).insert(8L, monday, LocalTime.of(9, 0), LocalTime.of(12, 0));
        verify(availability).insert(8L, monday.plusDays(2), LocalTime.of(9, 0), LocalTime.of(12, 0));
        verify(availability).insert(8L, monday.plusDays(4), LocalTime.of(9, 0), LocalTime.of(12, 0));
    }

    @Test
    void partTimeStaffCanRegisterMultipleNonOverlappingIntervalsForOneDate() {
        LocalDate workDate = LocalDate.now(java.time.ZoneId.of("Asia/Ho_Chi_Minh")).plusDays(5);
        StaffAccountRepository staffAccounts = mock(StaffAccountRepository.class);
        StaffAvailabilityRepository availability = mock(StaffAvailabilityRepository.class);
        var settings = mock(CompanyOperatingHoursService.class);
        Staff staff = new Staff(); staff.setStaffType(StaffType.PART_TIME); staff.setStaffId(8L);
        when(staffAccounts.findActiveByUserIdForUpdate(800L)).thenReturn(Optional.of(staff));
        when(settings.forDate(workDate)).thenReturn(List.of(
                new PortalForms.OperatingHoursForm(workDate.getDayOfWeek().getValue(), false, LocalTime.of(6, 0), LocalTime.of(21, 0))));
        when(availability.insert(eq(8L), eq(workDate), any(), any())).thenReturn(1);
        StaffAvailabilityService service = availabilityService(staffAccounts, availability, settings);

        int inserted = service.saveForDate(800L, workDate,
                List.of(LocalTime.of(6, 0), LocalTime.of(13, 0)),
                List.of(LocalTime.of(9, 0), LocalTime.of(17, 0)));

        assertThat(inserted).isEqualTo(2);
        verify(availability).insert(8L, workDate, LocalTime.of(6, 0), LocalTime.of(9, 0));
        verify(availability).insert(8L, workDate, LocalTime.of(13, 0), LocalTime.of(17, 0));
    }

    @Test
    void partTimeStaffCannotRegisterOverlappingIntervalsInOneSubmission() {
        LocalDate workDate = LocalDate.now(java.time.ZoneId.of("Asia/Ho_Chi_Minh")).plusDays(5);
        StaffAccountRepository staffAccounts = mock(StaffAccountRepository.class);
        StaffAvailabilityRepository availability = mock(StaffAvailabilityRepository.class);
        var settings = mock(CompanyOperatingHoursService.class);
        Staff staff = new Staff(); staff.setStaffType(StaffType.PART_TIME); staff.setStaffId(8L);
        when(staffAccounts.findActiveByUserIdForUpdate(800L)).thenReturn(Optional.of(staff));
        when(settings.forDate(workDate)).thenReturn(List.of(
                new PortalForms.OperatingHoursForm(workDate.getDayOfWeek().getValue(), false, LocalTime.of(6, 0), LocalTime.of(21, 0))));
        when(availability.insert(eq(8L), eq(workDate), any(), any())).thenReturn(1);
        StaffAvailabilityService service = availabilityService(staffAccounts, availability, settings);

        assertThatThrownBy(() -> service.saveForDate(800L, workDate,
                List.of(LocalTime.of(8, 0), LocalTime.of(11, 0)),
                List.of(LocalTime.of(12, 0), LocalTime.of(13, 0))))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("chồng lấn");
        verify(availability, never()).insert(any(), any(), any(), any());
    }

    @Test
    void partTimeStaffCannotDeleteAvailabilityThatContainsAConfirmedBookingStart() {
        StaffAccountRepository staffAccounts = mock(StaffAccountRepository.class);
        StaffAvailabilityRepository availability = mock(StaffAvailabilityRepository.class);
        Staff staff = new Staff();
        staff.setStaffType(StaffType.PART_TIME);
        staff.setStaffId(8L);
        when(staffAccounts.findActiveByUserIdForUpdate(800L)).thenReturn(Optional.of(staff));
        com.housekeeping.management.entity.StaffAvailability interval = new com.housekeeping.management.entity.StaffAvailability();
        interval.setAvailabilityId(25L); interval.setStaffId(8L); interval.setWorkDate(LocalDate.of(2026, 11, 3));
        interval.setStartTime(LocalTime.of(9, 0)); interval.setEndTime(LocalTime.of(13, 0)); interval.setActive(true);
        when(availability.findById(25L)).thenReturn(Optional.of(interval));
        BookingQueryService bookings = mock(BookingQueryService.class);
        when(bookings.hasFutureConfirmedBookingOverlapping(eq(8L), any(), any(), any())).thenReturn(true);
        StaffAvailabilityService service = availabilityService(staffAccounts, availability,
                mock(CompanyOperatingHoursService.class), bookings);

        assertThatThrownBy(() -> service.delete(800L, 25L))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("Booking CONFIRMED");

        verify(bookings).hasFutureConfirmedBookingOverlapping(eq(8L), any(), any(), any());
        verify(availability, never()).deactivate(25L, 8L);
    }

    @SuppressWarnings("unchecked")
    private GlobalEmailUniquenessService unlockedEmails() {
        GlobalEmailUniquenessService emails = mock(GlobalEmailUniquenessService.class);
        when(emails.executeWithUniqueEmail(anyString(), any())).thenAnswer(call -> {
            Function<String, Object> create = call.getArgument(1);
            return create.apply(GlobalEmailUniquenessService.canonicalize(call.getArgument(0)));
        });
        return emails;
    }

    private StaffAvailabilityService availabilityService(StaffAccountRepository staffAccounts,
            StaffAvailabilityRepository availability, CompanyOperatingHoursService settings) {
        return availabilityService(staffAccounts, availability, settings, mock(BookingQueryService.class));
    }

    private StaffAvailabilityService availabilityService(StaffAccountRepository staffAccounts,
            StaffAvailabilityRepository availability, CompanyOperatingHoursService settings,
            BookingQueryService bookings) {
        StaffManagementService staffManagement = mock(StaffManagementService.class);
        when(staffManagement.findActiveByUserIdForUpdate(any())).thenAnswer(call ->
                staffAccounts.findActiveByUserIdForUpdate(call.getArgument(0)).orElseThrow());
        return new com.housekeeping.management.service.impl.StaffAvailabilityServiceImpl(
                staffManagement, availability, settings, bookings);
    }
}
