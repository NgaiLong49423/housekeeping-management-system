package com.housekeeping.management.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.housekeeping.management.entity.User;
import com.housekeeping.management.enums.UserRole;
import com.housekeeping.management.enums.UserStatus;
import com.housekeeping.management.repository.UserRepository;
import com.housekeeping.management.service.impl.AuthServiceImpl;
import com.housekeeping.management.web.PortalUser;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class AuthServiceTest {
    @Test
    void authenticatedPrincipalCarriesRequiredPasswordChangeState() {
        UserRepository users = mock(UserRepository.class);
        StaffManagementService staffManagement = mock(StaffManagementService.class);
        User admin = new User();
        admin.setUserId(42L);
        admin.setEmail("new-admin@example.test");
        admin.setFullName("New Admin");
        admin.setPasswordHash("encoded-temporary-password");
        admin.setRole(UserRole.ADMIN);
        admin.setStatus(UserStatus.ACTIVE);
        admin.setMustChangePassword(true);
        when(users.findByEmail("new-admin@example.test")).thenReturn(Optional.of(admin));

        var principal = (PortalUser) new AuthServiceImpl(users, staffManagement)
                .loadUserByUsername("new-admin@example.test");

        assertThat(principal.isMustChangePassword()).isTrue();
        assertThat(principal.getUserId()).isEqualTo(42L);
        assertThat(principal.getPassword()).isEqualTo("encoded-temporary-password");
    }
}
