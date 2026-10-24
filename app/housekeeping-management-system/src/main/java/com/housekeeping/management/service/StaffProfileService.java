package com.housekeeping.management.service;

import com.housekeeping.management.dto.request.PortalForms.StaffProfileForm;
import com.housekeeping.management.repository.StaffProfileRepository;
import java.net.URI;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StaffProfileService {
    private final StaffProfileRepository profiles;
    public StaffProfileService(StaffProfileRepository profiles) { this.profiles = profiles; }

    @Transactional(readOnly = true)
    public StaffProfileForm findOwnProfile(long staffId) {
        return profiles.findByStaffId(staffId).orElseGet(() -> new StaffProfileForm(null, ""));
    }

    @Transactional
    public void updateOwnProfile(long staffId, StaffProfileForm form) {
        if (!profiles.lockStaff(staffId)) throw new IllegalArgumentException("Không tìm thấy hồ sơ nhân viên.");
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

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
