package com.housekeeping.management.service.impl;

import com.housekeeping.management.service.StaffProfileService;
import com.housekeeping.management.service.StaffManagementService;

import com.housekeeping.management.dto.request.PortalForms.StaffProfileForm;
import com.housekeeping.management.entity.Staff;
import com.housekeeping.management.entity.StaffProfile;
import com.housekeeping.management.repository.StaffProfileRepository;
import java.net.URI;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StaffProfileServiceImpl implements StaffProfileService {
    private final StaffProfileRepository profiles;
    private final StaffManagementService staffManagement;

    public StaffProfileServiceImpl(StaffProfileRepository profiles, StaffManagementService staffManagement) {
        this.profiles = profiles;
        this.staffManagement = staffManagement;
    }

    @Transactional(readOnly = true)
    public StaffProfileForm findOwnProfile(long userId) {
        Staff staff = findActiveStaff(userId);
        return profiles.findByStaffId(staff.getStaffId())
                .map(profile -> new StaffProfileForm(profile.getAvatarUrl(), profile.getBio()))
                .orElseGet(() -> new StaffProfileForm(null, ""));
    }

    @Transactional
    public void updateOwnProfile(long userId, StaffProfileForm form) {
        Staff staff = staffManagement.findActiveByUserIdForUpdate(userId);
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
        StaffProfile profile = profiles.findByStaffId(staffId).orElseGet(() -> {
            StaffProfile created = new StaffProfile();
            created.setStaffId(staffId);
            return created;
        });
        profile.setAvatarUrl(avatarUrl);
        profile.setBio(blankToNull(form.bio()));
        profiles.save(profile);
    }

    private Staff findActiveStaff(long userId) {
        return staffManagement.findActiveByUserId(userId);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
