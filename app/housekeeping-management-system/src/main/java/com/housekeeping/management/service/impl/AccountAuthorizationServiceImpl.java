package com.housekeeping.management.service.impl;

import com.housekeeping.management.service.AccountAuthorizationService;

import com.housekeeping.management.enums.UserRole;
import com.housekeeping.management.enums.UserStatus;
import com.housekeeping.management.entity.User;
import com.housekeeping.management.repository.UserRepository;
import java.util.Optional;
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

    /**
     * Dùng cho bộ lọc phiên đăng nhập. Tài khoản không còn tồn tại cũng bị coi như đã khóa (lý do rỗng),
     * để phiên đăng nhập cũ của tài khoản đó bị hủy luôn.
     */
    @Override
    public Optional<String> lockReasonIfInactive(Long userId) {
        if (userId == null) return Optional.of("");
        Optional<User> user = users.findById(userId);
        if (user.isEmpty()) return Optional.of("");
        if (user.get().getStatus() == UserStatus.ACTIVE) return Optional.empty();
        String reason = user.get().getStatusReason();
        return Optional.of(reason == null ? "" : reason);
    }

    public boolean isManager(Long userId) {
        return userId != null && users.findById(userId)
                .filter(user -> user.getRole() == UserRole.MANAGER && user.getStatus() == UserStatus.ACTIVE)
                .isPresent();
    }
}
