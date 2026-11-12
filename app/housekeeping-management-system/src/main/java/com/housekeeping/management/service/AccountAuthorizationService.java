package com.housekeeping.management.service;

import com.housekeeping.management.domain.UserRole;
import com.housekeeping.management.domain.UserStatus;
import org.springframework.security.access.AccessDeniedException;

public interface AccountAuthorizationService {

    public void requireActiveAdmin(Long userId);
    public boolean isActiveAdmin(Long userId);
    public boolean isManager(Long userId);
    public void requireManager(Long userId);
    /** Rỗng khi tài khoản đang hoạt động; ngược lại là lý do khóa (chuỗi rỗng nếu không ghi lý do hoặc không còn tồn tại). */
    java.util.Optional<String> lockReasonIfInactive(Long userId);}
