package com.housekeeping.management.web;

import java.util.List;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;

/** Identity comes from the authenticated database row, never a submitted role or customer ID. */
public final class PortalUser extends User {
    private final Long accountId;
    private final Long userId;
    private final String displayName;
    private final String role;
    private final String lockReason;
    private final boolean mustChangePassword;

    public PortalUser(Long accountId, String email, String hash, String displayName, String role) {
        this(accountId, accountId, email, hash, displayName, role);
    }

    public PortalUser(Long accountId, Long userId, String email, String hash, String displayName, String role) {
        this(accountId, userId, email, hash, displayName, role, false, null);
    }

    /**
     * {@code locked=true}: tài khoản đã bị khóa. Lý do chỉ được báo cho người dùng sau khi mật khẩu
     * đã được kiểm tra đúng (xem {@code SecurityConfig}).
     */
    public PortalUser(Long accountId, Long userId, String email, String hash, String displayName, String role,
            boolean locked, String lockReason) {
        this(accountId, userId, email, hash, displayName, role, locked, lockReason, false);
    }

    public PortalUser(Long accountId, Long userId, String email, String hash, String displayName, String role,
            boolean locked, String lockReason, boolean mustChangePassword) {
        super(email, hash, true, true, true, !locked, authorities(role));
        this.accountId = accountId;
        this.userId = userId;
        this.displayName = displayName;
        this.role = role;
        this.lockReason = locked ? lockReason : null;
        this.mustChangePassword = mustChangePassword;
    }

    public String getLockReason() { return lockReason; }

    public Long getAccountId() { return accountId; }
    public Long getUserId() { return userId; }
    public String getDisplayName() { return displayName; }
    public String getRole() { return role; }
    public boolean isMustChangePassword() { return mustChangePassword; }
    public boolean isAdmin() { return "ADMIN".equals(role) || "MANAGER".equals(role); }
    public boolean isManager() { return "MANAGER".equals(role); }

    private static List<SimpleGrantedAuthority> authorities(String role) {
        if ("MANAGER".equals(role)) {
            return List.of(new SimpleGrantedAuthority("ROLE_MANAGER"), new SimpleGrantedAuthority("ROLE_ADMIN"));
        }
        return List.of(new SimpleGrantedAuthority("ROLE_" + role));
    }
}
