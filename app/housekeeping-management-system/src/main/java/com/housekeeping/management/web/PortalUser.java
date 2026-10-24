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

    public PortalUser(Long accountId, String email, String hash, String displayName, String role) {
        this(accountId, accountId, email, hash, displayName, role);
    }

    public PortalUser(Long accountId, Long userId, String email, String hash, String displayName, String role) {
        super(email, hash, List.of(new SimpleGrantedAuthority("ROLE_" + role)));
        this.accountId = accountId;
        this.userId = userId;
        this.displayName = displayName;
        this.role = role;
    }

    public Long getAccountId() { return accountId; }
    public Long getUserId() { return userId; }
    public String getDisplayName() { return displayName; }
    public String getRole() { return role; }
}
