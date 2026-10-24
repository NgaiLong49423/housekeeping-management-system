package com.housekeeping.management.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.housekeeping.management.domain.StaffType;
import com.housekeeping.management.dto.request.PortalForms;
import com.housekeeping.management.service.PortalActionsService;
import com.housekeeping.management.domain.UserRole;
import com.housekeeping.management.domain.UserStatus;
import com.housekeeping.management.domain.entity.Services;
import com.housekeeping.management.entity.Staff;
import com.housekeeping.management.entity.User;
import com.housekeeping.management.repository.BookingDetailRepository;
import com.housekeeping.management.repository.BookingRepository;
import com.housekeeping.management.repository.CustomerRepository;
import com.housekeeping.management.repository.ServicesRepository;
import com.housekeeping.management.repository.StaffAccountRepository;
import com.housekeeping.management.repository.StaffRepository;
import com.housekeeping.management.repository.WorkScheduleRepository;
import com.housekeeping.management.service.GlobalEmailUniquenessService;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

class PortalActionsServiceTest {
    @Test
    void customerRegistrationPersistsOneSharedUserWithCanonicalEmailAndEncodedPassword() {
        EntityManager entityManager = mock(EntityManager.class);
        GlobalEmailUniquenessService emails = unlockedEmailService();
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        when(encoder.encode("plaintext-password")).thenReturn("encoded-password");
        PortalActionsService actions = actions(entityManager, emails, encoder, null, null);

        actions.registerCustomer(new PortalForms.CustomerRegistrationForm(
                "  Customer Name  ", " Customer@Example.test ", " 0900000000 ", "plaintext-password", "plaintext-password"));

        var captor = org.mockito.ArgumentCaptor.forClass(User.class);
        verify(entityManager).persist(captor.capture());
        User user = captor.getValue();
        assertThat(user.getFullName()).isEqualTo("Customer Name");
        assertThat(user.getEmail()).isEqualTo("customer@example.test");
        assertThat(user.getPhone()).isEqualTo("0900000000");
        assertThat(user.getPasswordHash()).isEqualTo("encoded-password");
        assertThat(user.getRole()).isEqualTo(UserRole.CUSTOMER);
        assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
    }

    @Test
    void staffCreationStoresRoleInUsersAndOneToOneStaffType() {
        EntityManager entityManager = mock(EntityManager.class);
        GlobalEmailUniquenessService emails = unlockedEmailService();
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        when(encoder.encode("staff-password")).thenReturn("encoded-staff-password");
        PortalActionsService actions = actions(entityManager, emails, encoder, null, null);

        actions.createStaff(new PortalForms.StaffCreateForm("Staff Name", "staff@example.test", "staff-password",
                "0900000001", StaffType.PART_TIME));

        var users = org.mockito.ArgumentCaptor.forClass(User.class);
        var staff = org.mockito.ArgumentCaptor.forClass(Staff.class);
        verify(entityManager).persist(users.capture());
        verify(entityManager).persist(staff.capture());
        assertThat(users.getValue().getRole()).isEqualTo(UserRole.STAFF);
        assertThat(users.getValue().getEmail()).isEqualTo("staff@example.test");
        assertThat(staff.getValue().getUser()).isSameAs(users.getValue());
        assertThat(staff.getValue().getStaffType()).isEqualTo(StaffType.PART_TIME);
        verify(entityManager, org.mockito.Mockito.times(2)).flush();
    }

    @Test
    void deactivateAndReactivateStaffChangesUserStatusWithoutDeletingHistory() {
        StaffAccountRepository staffAccounts = mock(StaffAccountRepository.class);
        User user = new User();
        user.setStatus(UserStatus.ACTIVE);
        Staff staff = new Staff();
        staff.setUser(user);
        when(staffAccounts.findById(9L)).thenReturn(Optional.of(staff));
        PortalActionsService actions = actions(mock(EntityManager.class), mock(GlobalEmailUniquenessService.class),
                mock(PasswordEncoder.class), staffAccounts, null);

        actions.deleteStaff(9L);
        assertThat(user.getStatus()).isEqualTo(UserStatus.INACTIVE);
        actions.activateStaff(9L);
        assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
    }

    @Test
    void serviceDeactivationRetainsTheServiceRow() {
        ServicesRepository services = mock(ServicesRepository.class);
        Services service = new Services();
        service.setStatus(Services.Status.ACTIVE);
        when(services.findById(4L)).thenReturn(Optional.of(service));
        PortalActionsService actions = actions(mock(EntityManager.class), mock(GlobalEmailUniquenessService.class),
                mock(PasswordEncoder.class), null, services);

        actions.deleteService(4L);
        assertThat(service.getStatus()).isEqualTo(Services.Status.INACTIVE);
        actions.activateService(4L);
        assertThat(service.getStatus()).isEqualTo(Services.Status.ACTIVE);
    }

    @SuppressWarnings("unchecked")
    private GlobalEmailUniquenessService unlockedEmailService() {
        GlobalEmailUniquenessService emails = mock(GlobalEmailUniquenessService.class);
        try {
            when(emails.executeWithUniqueEmail(anyString(), any())).thenAnswer(call -> {
                Function<String, Object> persist = call.getArgument(1);
                return persist.apply(GlobalEmailUniquenessService.canonicalize(call.getArgument(0)));
            });
        } catch (Throwable failure) {
            throw failure;
        }
        return emails;
    }

    private PortalActionsService actions(EntityManager em, GlobalEmailUniquenessService emails, PasswordEncoder encoder,
            StaffAccountRepository staffAccounts, ServicesRepository services) {
        return new PortalActionsService(em, mock(BookingRepository.class), mock(com.housekeeping.management.repository.BookingPersistenceRepository.class), mock(StaffRepository.class),
                mock(WorkScheduleRepository.class), mock(JdbcTemplate.class), emails, encoder,
                staffAccounts == null ? mock(StaffAccountRepository.class) : staffAccounts,
                services == null ? mock(ServicesRepository.class) : services);
    }
}
