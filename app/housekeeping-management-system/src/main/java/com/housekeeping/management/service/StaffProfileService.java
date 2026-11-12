package com.housekeeping.management.service;

import com.housekeeping.management.enums.UserStatus;
import com.housekeeping.management.enums.UserRole;
import com.housekeeping.management.dto.request.PortalForms.StaffProfileForm;
import com.housekeeping.management.entity.Staff;
import java.net.URI;
import org.springframework.security.access.AccessDeniedException;

public interface StaffProfileService {

    public StaffProfileForm findOwnProfile(long userId);
    public void updateOwnProfile(long userId, StaffProfileForm form);}
