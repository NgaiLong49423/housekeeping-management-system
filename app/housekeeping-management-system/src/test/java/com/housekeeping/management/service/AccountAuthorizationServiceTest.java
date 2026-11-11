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
    private final AccountAuthorizationService authorization = new com.housekeeping.management.service.impl.AccountAuthorizationServiceImpl(users);

    @Test
    void permitsOnlyExistingActiveAdminAccounts() {
        when(users.findById(1L)).thenReturn(Optional.of(user(UserRole.ADMIN, UserStatus.ACTIVE)));
        when(users.findById(2L)).thenReturn(Optional.of(user(UserRole.CUSTOMER, UserStatus.ACTIVE)));
        when(users.findById(3L)).thenReturn(Optional.of(user(UserRole.ADMIN, UserStatus.INACTIVE)));
        when(users.findById(4L)).thenReturn(Optional.empty());
        when(users.findById(5L)).thenReturn(Optional.of(user(UserRole.MANAGER, UserStatus.ACTIVE)));

        authorization.requireActiveAdmin(1L);
        authorization.requireActiveAdmin(5L);

        assertThatThrownBy(() -> authorization.requireActiveAdmin(2L)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> authorization.requireActiveAdmin(3L)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> authorization.requireActiveAdmin(4L)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> authorization.requireActiveAdmin(null)).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void onlyActiveManagerCanUseManagerOperations() {
        when(users.findById(5L)).thenReturn(Optional.of(user(UserRole.MANAGER, UserStatus.ACTIVE)));
        when(users.findById(1L)).thenReturn(Optional.of(user(UserRole.ADMIN, UserStatus.ACTIVE)));
        when(users.findById(6L)).thenReturn(Optional.of(user(UserRole.MANAGER, UserStatus.INACTIVE)));

        authorization.requireManager(5L);

        assertThatThrownBy(() -> authorization.requireManager(1L)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> authorization.requireManager(6L)).isInstanceOf(AccessDeniedException.class);
    }

    private User user(UserRole role, UserStatus status) {
        User user = new User();
        user.setRole(role);
        user.setStatus(status);
        return user;
    }
}
