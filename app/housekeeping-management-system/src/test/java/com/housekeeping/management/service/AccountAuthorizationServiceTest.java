package com.housekeeping.management.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.housekeeping.management.domain.UserRole;
import com.housekeeping.management.domain.UserStatus;
import com.housekeeping.management.entity.User;
import com.housekeeping.management.repository.UserRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

class AccountAuthorizationServiceTest {
    private final UserRepository users = mock(UserRepository.class);
    private final AccountAuthorizationService authorization = new AccountAuthorizationService(users);

    @Test
    void permitsOnlyExistingActiveAdminAccounts() {
        when(users.findById(1L)).thenReturn(Optional.of(user(UserRole.ADMIN, UserStatus.ACTIVE)));
        when(users.findById(2L)).thenReturn(Optional.of(user(UserRole.CUSTOMER, UserStatus.ACTIVE)));
        when(users.findById(3L)).thenReturn(Optional.of(user(UserRole.ADMIN, UserStatus.INACTIVE)));
        when(users.findById(4L)).thenReturn(Optional.empty());

        authorization.requireActiveAdmin(1L);

        assertThatThrownBy(() -> authorization.requireActiveAdmin(2L)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> authorization.requireActiveAdmin(3L)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> authorization.requireActiveAdmin(4L)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> authorization.requireActiveAdmin(null)).isInstanceOf(AccessDeniedException.class);
    }

    private User user(UserRole role, UserStatus status) {
        User user = new User();
        user.setRole(role);
        user.setStatus(status);
        return user;
    }
}
