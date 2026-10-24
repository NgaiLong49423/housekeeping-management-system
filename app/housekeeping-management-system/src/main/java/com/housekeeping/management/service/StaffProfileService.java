package com.housekeeping.management.service;

import com.housekeeping.management.domain.UserStatus;
import com.housekeeping.management.domain.UserRole;
import com.housekeeping.management.dto.request.PortalForms.StaffProfileForm;
import com.housekeeping.management.entity.Staff;
import com.housekeeping.management.repository.StaffAccountRepository;
import com.housekeeping.management.repository.StaffProfileRepository;
import java.net.URI;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StaffProfileService {
    private final StaffProfileRepository profiles;
    private final StaffAccountRepository staffAccounts;

    public StaffProfileService(StaffProfileRepository profiles, StaffAccountRepository staffAccounts) {
        this.profiles = profiles;
        this.staffAccounts = staffAccounts;
    }

    @Transactional(readOnly = true)
    public StaffProfileForm findOwnProfile(long userId) {
        Staff staff = findActiveStaff(userId);
        return profiles.findByStaffId(staff.getStaffId()).orElseGet(() -> new StaffProfileForm(null, ""));
    }

    @Transactional
    public void updateOwnProfile(long userId, StaffProfileForm form) {
        Staff staff = staffAccounts.findActiveByUserIdForUpdate(userId)
                .orElseThrow(() -> new AccessDeniedException("Tài khoản Staff không tồn tại hoặc không hoạt động."));
        Long staffId = staff.getStaffId();
        String avatarUrl = blankToNull(form.avatarUrl());
        if (avatarUrl != null) {
            try {
                URI uri = URI.create(avatarUrl);
                if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null) {
                    throw new IllegalArgumentException("Ảnh đại diện phải dùng đường dẫn HTTPS hợp lệ.");
                }
            } catch (IllegalArgumentException invalidUri) {
                throw new IllegalArgumentException("Ảnh đại diện phải dùng đường dẫn HTTPS hợp lệ.");
            }
        }
        profiles.save(staffId, avatarUrl, blankToNull(form.bio()));
    }

    private Staff findActiveStaff(long userId) {
        return staffAccounts.findByUser_UserId(userId)
                .filter(staff -> staff.getUser().getRole() == UserRole.STAFF
                        && staff.getUser().getStatus() == UserStatus.ACTIVE)
                .orElseThrow(() -> new AccessDeniedException("Tài khoản Staff không tồn tại hoặc không hoạt động."));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
