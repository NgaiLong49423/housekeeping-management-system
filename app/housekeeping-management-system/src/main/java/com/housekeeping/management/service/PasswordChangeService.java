package com.housekeeping.management.service;

import com.housekeeping.management.dto.request.PortalForms.ChangePasswordForm;

public interface PasswordChangeService {
    boolean isPasswordChangeRequired(Long userId);
    void changePassword(Long userId, ChangePasswordForm form);
}
