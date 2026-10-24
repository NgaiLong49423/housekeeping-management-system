package com.housekeeping.management.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.housekeeping.management.domain.StaffType;
import com.housekeeping.management.domain.UserRole;
import com.housekeeping.management.domain.UserStatus;
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
import java.time.LocalTime;
import java.util.Optional;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

class ManagementServicesTest {
    @Test
    void registrationCreatesOneCanonicalActiveCustomerIdentity() {
        EntityManager entities = mock(EntityManager.class);
        PasswordEncoder passwords = mock(PasswordEncoder.class);
        when(passwords.encode("secret")).thenReturn("bcrypt-hash");
        new RegistrationService(entities, unlockedEmails(), passwords).registerCustomer(
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
        StaffManagementService service = new StaffManagementService(entities, mock(StaffAccountRepository.class),
                mock(BookingPersistenceRepository.class),
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
        BookingPersistenceRepository bookings = mock(BookingPersistenceRepository.class);
        ServicesRepository services = mock(ServicesRepository.class);
        User user = new User(); user.setStatus(UserStatus.ACTIVE);
        Staff staff = new Staff(); staff.setUser(user);
        Services service = new Services(); service.setStatus(Services.Status.ACTIVE);
        when(staffAccounts.findByStaffIdForUpdate(9L)).thenReturn(Optional.of(staff));
        when(staffAccounts.findById(9L)).thenReturn(Optional.of(staff));
        when(services.findById(4L)).thenReturn(Optional.of(service));
        StaffManagementService staffManagement = new StaffManagementService(mock(EntityManager.class), staffAccounts, bookings,
                mock(GlobalEmailUniquenessService.class), mock(PasswordEncoder.class), mock(AccountAuthorizationService.class));
        ServiceManagementService serviceManagement = new ServiceManagementService(services, mock(AccountAuthorizationService.class));
        staffManagement.deactivate(99L, 9L); serviceManagement.deactivate(99L, 4L);
        assertThat(user.getStatus()).isEqualTo(UserStatus.INACTIVE);
        assertThat(service.getStatus()).isEqualTo(Services.Status.INACTIVE);
        staffManagement.activate(99L, 9L); serviceManagement.activate(99L, 4L);
        assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(service.getStatus()).isEqualTo(Services.Status.ACTIVE);
    }

    @Test
    void staffCannotBeDeactivatedWhileUnfinishedBookingsRemain() {
        StaffAccountRepository staffAccounts = mock(StaffAccountRepository.class);
        BookingPersistenceRepository bookings = mock(BookingPersistenceRepository.class);
        User user = new User(); user.setStatus(UserStatus.ACTIVE);
        Staff staff = new Staff(); staff.setUser(user);
        when(staffAccounts.findByStaffIdForUpdate(9L)).thenReturn(Optional.of(staff));
        when(bookings.hasUnfinishedBookingForStaff(9L)).thenReturn(true);
        StaffManagementService service = new StaffManagementService(mock(EntityManager.class), staffAccounts, bookings,
                mock(GlobalEmailUniquenessService.class), mock(PasswordEncoder.class), mock(AccountAuthorizationService.class));

        assertThatThrownBy(() -> service.deactivate(99L, 9L))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("chưa hoàn tất");

        assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
        verify(staffAccounts).findByStaffIdForUpdate(9L);
    }

    @Test
    void serviceSaveKeepsTheApprovedFieldsAndDefaultsNewServiceToActive() {
        ServicesRepository services = mock(ServicesRepository.class);
        new ServiceManagementService(services, mock(AccountAuthorizationService.class)).save(99L, null,
                new PortalForms.ServiceForm("  Dọn nhà  ", "Mô tả", 90, new BigDecimal("125000.00")));
        var captured = org.mockito.ArgumentCaptor.forClass(Services.class);
        verify(services).saveAndFlush(captured.capture());
        assertThat(captured.getValue().getServiceName()).isEqualTo("Dọn nhà");
        assertThat(captured.getValue().getDurationMinutes()).isEqualTo(90);
        assertThat(captured.getValue().getPrice()).isEqualByComparingTo("125000.00");
        assertThat(captured.getValue().getStatus()).isEqualTo(Services.Status.ACTIVE);
    }

    @Test
    void partTimeAvailabilityRejectsAnOverlappingInterval() {
        StaffAccountRepository staffAccounts = mock(StaffAccountRepository.class);
        StaffAvailabilityRepository availability = mock(StaffAvailabilityRepository.class);
        Staff staff = new Staff(); staff.setStaffType(StaffType.PART_TIME);
        staff.setStaffId(8L);
        when(staffAccounts.findActiveByUserIdForUpdate(800L)).thenReturn(Optional.of(staff));
        when(availability.overlapsAnotherInterval(8L, LocalDate.of(2026, 11, 3),
                LocalTime.of(9, 0), LocalTime.of(12, 0), null)).thenReturn(true);
        StaffAvailabilityService service = new StaffAvailabilityService(staffAccounts, availability);
        assertThatThrownBy(() -> service.save(800L, null, new PortalForms.AvailabilityForm(
                LocalDate.of(2026, 11, 3), LocalTime.of(9, 0), LocalTime.of(12, 0))))
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
        when(availability.containsExistingBookingStart(25L, 8L)).thenReturn(true);
        StaffAvailabilityService service = new StaffAvailabilityService(staffAccounts, availability);

        assertThatThrownBy(() -> service.save(800L, 25L, new PortalForms.AvailabilityForm(
                LocalDate.of(2026, 11, 3), LocalTime.of(10, 0), LocalTime.of(13, 0))))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("đã có lịch hẹn");

        verify(availability).containsExistingBookingStart(25L, 8L);
        verify(availability, never()).update(any(), any(), any(), any(), any());
    }

    @Test
    void partTimeStaffCannotDeleteAvailabilityThatContainsAConfirmedBookingStart() {
        StaffAccountRepository staffAccounts = mock(StaffAccountRepository.class);
        StaffAvailabilityRepository availability = mock(StaffAvailabilityRepository.class);
        Staff staff = new Staff();
        staff.setStaffType(StaffType.PART_TIME);
        staff.setStaffId(8L);
        when(staffAccounts.findActiveByUserIdForUpdate(800L)).thenReturn(Optional.of(staff));
        when(availability.containsExistingBookingStart(25L, 8L)).thenReturn(true);
        StaffAvailabilityService service = new StaffAvailabilityService(staffAccounts, availability);

        assertThatThrownBy(() -> service.delete(800L, 25L))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("đã có lịch hẹn");

        verify(availability).containsExistingBookingStart(25L, 8L);
        verify(availability, never()).delete(25L, 8L);
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
}
