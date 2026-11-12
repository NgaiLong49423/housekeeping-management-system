package com.housekeeping.management.service.impl;

import com.housekeeping.management.dto.request.PortalForms.ChangePasswordForm;
import com.housekeeping.management.entity.User;
import com.housekeeping.management.enums.UserRole;
import com.housekeeping.management.enums.UserStatus;
import com.housekeeping.management.repository.UserRepository;
import com.housekeeping.management.service.PasswordChangeService;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PasswordChangeServiceImpl implements PasswordChangeService {
    private static final int MIN_PASSWORD_LENGTH = 8;
    private static final int MAX_BCRYPT_BYTES = 72;

    private final UserRepository users;
    private final PasswordEncoder passwords;

    public PasswordChangeServiceImpl(UserRepository users, PasswordEncoder passwords) {
        this.users = users;
        this.passwords = passwords;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isPasswordChangeRequired(Long userId) {
        return userId != null && users.findById(userId).map(User::isMustChangePassword).orElse(false);
    }

    @Override
    @Transactional
    public void changePassword(Long userId, ChangePasswordForm form) {
        if (userId == null || form == null) {
            throw new IllegalArgumentException("Thông tin đổi mật khẩu chưa hợp lệ.");
        }

        User user = users.findByUserIdForUpdate(userId)
                .orElseThrow(() -> new AccessDeniedException("Tài khoản không còn hợp lệ."));
        if (user.getStatus() != UserStatus.ACTIVE
                || (user.getRole() != UserRole.STAFF && user.getRole() != UserRole.ADMIN)) {
            throw new AccessDeniedException("Tài khoản không được phép đổi mật khẩu theo luồng này.");
        }
        if (!user.isMustChangePassword()) {
            throw new IllegalStateException("Tài khoản này không còn yêu cầu đổi mật khẩu.");
        }
        if (form.currentPassword() == null || form.currentPassword().isBlank()
                || !passwords.matches(form.currentPassword(), user.getPasswordHash())) {
            throw new IllegalArgumentException("Mật khẩu hiện tại không chính xác.");
        }

        String newPassword = form.newPassword();
        if (newPassword == null || newPassword.isBlank() || newPassword.length() < MIN_PASSWORD_LENGTH) {
            throw new IllegalArgumentException("Mật khẩu mới phải có ít nhất 8 ký tự.");
        }
        if (newPassword.getBytes(StandardCharsets.UTF_8).length > MAX_BCRYPT_BYTES) {
            throw new IllegalArgumentException("Mật khẩu mới không được vượt quá 72 byte UTF-8.");
        }
        if (!Objects.equals(newPassword, form.confirmPassword())) {
            throw new IllegalArgumentException("Mật khẩu xác nhận chưa khớp.");
        }
        if (passwords.matches(newPassword, user.getPasswordHash())) {
            throw new IllegalArgumentException("Mật khẩu mới phải khác mật khẩu hiện tại.");
        }

        user.setPasswordHash(passwords.encode(newPassword));
        user.setMustChangePassword(false);
    }
}
