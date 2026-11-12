package com.housekeeping.management.service;

import com.housekeeping.management.enums.UserRole;
import com.housekeeping.management.enums.UserStatus;
import org.springframework.security.access.AccessDeniedException;

public interface AccountAuthorizationService {

    public void requireActiveAdmin(Long userId);
    public boolean isActiveAdmin(Long userId);
    public boolean isManager(Long userId);
    public void requireManager(Long userId);}
