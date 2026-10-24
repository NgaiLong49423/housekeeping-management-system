package com.housekeeping.management.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.housekeeping.management.domain.StaffType;
import com.housekeeping.management.domain.UserRole;
import com.housekeeping.management.domain.UserStatus;
import com.housekeeping.management.config.SecurityConfig;
import com.housekeeping.management.entity.Staff;
import com.housekeeping.management.entity.User;
import com.housekeeping.management.repository.StaffAccountRepository;
import com.housekeeping.management.repository.UserRepository;
import com.housekeeping.management.service.AuthService;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

class PortalIdentityTest {
    @Test
    void canonicalEmailResolvesSharedUserAndStaffAccountIds() {
        UserRepository users = mock(UserRepository.class);
        StaffAccountRepository staffAccounts = mock(StaffAccountRepository.class);
        User databaseUser = user(UserRole.STAFF, UserStatus.ACTIVE);
        Staff databaseStaff = new Staff();
        databaseStaff.setStaffId(17L);
        databaseStaff.setStaffType(StaffType.FULL_TIME);
        when(users.findByEmail("user@example.test")).thenReturn(Optional.of(databaseUser));
        when(staffAccounts.findByUser_UserId(7L)).thenReturn(Optional.of(databaseStaff));

        PortalUser user = (PortalUser) new AuthService(users, staffAccounts)
                .loadUserByUsername("  USER@EXAMPLE.TEST  ");

        assertThat(user.getRole()).isEqualTo("STAFF");
        assertThat(user.getAccountId()).isEqualTo(17L);
        assertThat(user.getUserId()).isEqualTo(7L);
        assertThat(user.getUsername()).isEqualTo("user@example.test");
    }

    @Test
    void missingOrInactiveUserCannotAuthenticate() {
        UserRepository users = mock(UserRepository.class);
        StaffAccountRepository staffAccounts = mock(StaffAccountRepository.class);
        when(users.findByEmail("user@example.test")).thenReturn(Optional.empty());
        AuthService authentication = new AuthService(users, staffAccounts);
        assertThatThrownBy(() -> authentication.loadUserByUsername("user@example.test"))
                .isInstanceOf(UsernameNotFoundException.class);

        when(users.findByEmail("user@example.test")).thenReturn(Optional.of(user(UserRole.CUSTOMER, UserStatus.INACTIVE)));
        assertThatThrownBy(() -> authentication.loadUserByUsername("user@example.test"))
                .isInstanceOf(UsernameNotFoundException.class);
    }

    @Test
    void staffIdentityWithoutStaffProfileCannotAuthenticate() {
        UserRepository users = mock(UserRepository.class);
        StaffAccountRepository staffAccounts = mock(StaffAccountRepository.class);
        when(users.findByEmail("user@example.test")).thenReturn(Optional.of(user(UserRole.STAFF, UserStatus.ACTIVE)));
        when(staffAccounts.findByUser_UserId(7L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> new AuthService(users, staffAccounts).loadUserByUsername("user@example.test"))
                .isInstanceOf(UsernameNotFoundException.class);
    }

    @Test
    void passwordEncoderAcceptsBcryptButDoesNotAllowPlaintextFallback() {
        var encoder = new SecurityConfig().passwordEncoder();
        String encoded = encoder.encode("test-password");
        assertThat(encoded).startsWith("{bcrypt}");
        assertThat(encoder.matches("test-password", encoded)).isTrue();
        assertThat(encoder.matches("test-password", encoded.substring("{bcrypt}".length()))).isTrue();
        assertThat(encoder.matches("test-password", "{noop}test-password")).isFalse();
    }

    private User user(UserRole role, UserStatus status) {
        User user = new User();
        user.setUserId(7L);
        user.setEmail("user@example.test");
        user.setPasswordHash("stored-hash");
        user.setFullName("Database identity");
        user.setPhone("0900000000");
        user.setRole(role);
        user.setStatus(status);
        return user;
    }
}
