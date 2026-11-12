package com.housekeeping.management.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.housekeeping.management.dto.request.PortalForms.ChangePasswordForm;
import com.housekeeping.management.entity.User;
import com.housekeeping.management.enums.UserRole;
import com.housekeeping.management.enums.UserStatus;
import com.housekeeping.management.repository.UserRepository;
import com.housekeeping.management.service.impl.PasswordChangeServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

class PasswordChangeServiceTest {
    private final UserRepository users = mock(UserRepository.class);
    private final PasswordEncoder passwords = mock(PasswordEncoder.class);
    private final PasswordChangeService service = new PasswordChangeServiceImpl(users, passwords);

    @Test
    void successfulChangeUpdatesHashAndClearsRequiredFlag() {
        User user = forcedStaff();
        when(users.findByUserIdForUpdate(22L)).thenReturn(java.util.Optional.of(user));
        when(passwords.matches("temporary-password", "temporary-hash")).thenReturn(true);
        when(passwords.matches("new-password", "temporary-hash")).thenReturn(false);
        when(passwords.encode("new-password")).thenReturn("new-hash");

        service.changePassword(22L, new ChangePasswordForm(
                "temporary-password", "new-password", "new-password"));

        assertThat(user.getPasswordHash()).isEqualTo("new-hash");
        assertThat(user.isMustChangePassword()).isFalse();
    }

    @Test
    void incorrectCurrentPasswordLeavesHashAndFlagUnchanged() {
        User user = forcedStaff();
        when(users.findByUserIdForUpdate(22L)).thenReturn(java.util.Optional.of(user));
        when(passwords.matches("wrong-password", "temporary-hash")).thenReturn(false);

        assertThatThrownBy(() -> service.changePassword(22L,
                new ChangePasswordForm("wrong-password", "new-password", "new-password")))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("hiện tại");

        assertThat(user.getPasswordHash()).isEqualTo("temporary-hash");
        assertThat(user.isMustChangePassword()).isTrue();
        verify(passwords, never()).encode("new-password");
    }

    @Test
    void mismatchedConfirmationLeavesHashAndFlagUnchanged() {
        User user = forcedStaff();
        when(users.findByUserIdForUpdate(22L)).thenReturn(java.util.Optional.of(user));
        when(passwords.matches("temporary-password", "temporary-hash")).thenReturn(true);

        assertThatThrownBy(() -> service.changePassword(22L,
                new ChangePasswordForm("temporary-password", "new-password", "different-password")))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("xác nhận");

        assertThat(user.getPasswordHash()).isEqualTo("temporary-hash");
        assertThat(user.isMustChangePassword()).isTrue();
        verify(passwords, never()).encode("new-password");
    }

    @Test
    void newPasswordMustDifferFromCurrentPassword() {
        User user = forcedStaff();
        when(users.findByUserIdForUpdate(22L)).thenReturn(java.util.Optional.of(user));
        when(passwords.matches("temporary-password", "temporary-hash")).thenReturn(true);

        assertThatThrownBy(() -> service.changePassword(22L,
                new ChangePasswordForm("temporary-password", "temporary-password", "temporary-password")))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("phải khác");

        assertThat(user.getPasswordHash()).isEqualTo("temporary-hash");
        assertThat(user.isMustChangePassword()).isTrue();
        verify(passwords, never()).encode("temporary-password");
    }

    @Test
    void newPasswordCannotExceedBcryptUtf8ByteLimit() {
        User user = forcedStaff();
        String longVietnamesePassword = "ế".repeat(25);
        when(users.findByUserIdForUpdate(22L)).thenReturn(java.util.Optional.of(user));
        when(passwords.matches("temporary-password", "temporary-hash")).thenReturn(true);

        assertThatThrownBy(() -> service.changePassword(22L,
                new ChangePasswordForm("temporary-password", longVietnamesePassword, longVietnamesePassword)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("72 byte UTF-8");

        assertThat(user.getPasswordHash()).isEqualTo("temporary-hash");
        assertThat(user.isMustChangePassword()).isTrue();
    }

    @Test
    void onlyActiveStaffAndAdminWithRequiredFlagCanUseThisFlow() {
        User user = forcedStaff();
        user.setMustChangePassword(false);
        when(users.findByUserIdForUpdate(22L)).thenReturn(java.util.Optional.of(user));

        assertThatThrownBy(() -> service.changePassword(22L,
                new ChangePasswordForm("temporary-password", "new-password", "new-password")))
                .isInstanceOf(IllegalStateException.class);
    }

    private User forcedStaff() {
        User user = new User();
        user.setUserId(22L);
        user.setRole(UserRole.STAFF);
        user.setStatus(UserStatus.ACTIVE);
        user.setPasswordHash("temporary-hash");
        user.setMustChangePassword(true);
        return user;
    }
}
