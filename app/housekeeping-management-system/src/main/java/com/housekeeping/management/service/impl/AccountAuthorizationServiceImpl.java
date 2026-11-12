package com.housekeeping.management.service.impl;

import com.housekeeping.management.service.AccountAuthorizationService;

import com.housekeeping.management.domain.UserRole;
import com.housekeeping.management.domain.UserStatus;
import com.housekeeping.management.repository.UserRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

@Service
public class AccountAuthorizationServiceImpl implements AccountAuthorizationService {
    private final UserRepository users;

    public AccountAuthorizationServiceImpl(UserRepository users) {
        this.users = users;
    }

    public void requireActiveAdmin(Long userId) {
        if (!isActiveAdmin(userId)) {
            throw new AccessDeniedException("Chỉ Admin đang hoạt động mới có thể thực hiện thao tác này.");
        }
    }

    public boolean isActiveAdmin(Long userId) {
        return userId != null && users.findById(userId)
                .filter(user -> (user.getRole() == UserRole.ADMIN || user.getRole() == UserRole.MANAGER)
                        && user.getStatus() == UserStatus.ACTIVE)
                .isPresent();
    }

    public void requireManager(Long userId) {
        if (!isManager(userId)) {
            throw new AccessDeniedException("Chỉ Manager đang hoạt động mới có quyền này.");
        }
    }

    public boolean isManager(Long userId) {
        return userId != null && users.findById(userId)
                .filter(user -> user.getRole() == UserRole.MANAGER && user.getStatus() == UserStatus.ACTIVE)
                .isPresent();
    }
}
