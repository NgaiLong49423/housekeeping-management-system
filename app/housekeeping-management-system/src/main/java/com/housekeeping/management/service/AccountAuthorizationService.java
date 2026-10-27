package com.housekeeping.management.service;

import com.housekeeping.management.domain.UserRole;
import com.housekeeping.management.domain.UserStatus;
import com.housekeeping.management.repository.UserRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

@Service
public class AccountAuthorizationService {
    private final UserRepository users;

    public AccountAuthorizationService(UserRepository users) {
        this.users = users;
    }

    public void requireActiveAdmin(Long userId) {
        boolean isActiveAdmin = userId != null && users.findById(userId)
                .filter(user -> user.getRole() == UserRole.ADMIN && user.getStatus() == UserStatus.ACTIVE)
                .isPresent();
        if (!isActiveAdmin) {
            throw new AccessDeniedException("Chỉ Admin đang hoạt động mới có thể thực hiện thao tác này.");
        }
    }
}
