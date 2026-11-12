package com.housekeeping.management.service.impl;

import com.housekeeping.management.service.AccountAuthorizationService;
import com.housekeeping.management.service.AdminAccountService;
import com.housekeeping.management.service.GlobalEmailUniquenessService;

import com.housekeeping.management.dto.request.PortalForms.AdminAccountForm;
import com.housekeeping.management.dto.response.PortalViews.AdminAccountCard;
import com.housekeeping.management.enums.UserRole;
import com.housekeeping.management.enums.UserStatus;
import com.housekeeping.management.entity.User;
import com.housekeeping.management.repository.AdminAccountRepository;
import java.util.List;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminAccountServiceImpl implements AdminAccountService {
    private final AccountAuthorizationService authorization;
    private final AdminAccountRepository accounts;
    private final GlobalEmailUniquenessService emails;
    private final PasswordEncoder passwords;

    public AdminAccountServiceImpl(AccountAuthorizationService authorization, AdminAccountRepository accounts,
            GlobalEmailUniquenessService emails, PasswordEncoder passwords) {
        this.authorization = authorization;
        this.accounts = accounts;
        this.emails = emails;
        this.passwords = passwords;
    }

    public List<AdminAccountCard> findAll(Long managerId) {
        authorization.requireManager(managerId);
        return accounts.findByRoleOrderByStatusAscFullNameAsc(UserRole.ADMIN).stream()
                .map(user -> new AdminAccountCard(user.getUserId(), user.getFullName(), user.getEmail(),
                        user.getPhone(), user.getStatus().name()))
                .toList();
    }

    @Transactional
    public void create(Long managerId, AdminAccountForm form) {
        authorization.requireManager(managerId);
        if (form == null || form.name() == null || form.name().isBlank() || form.email() == null
                || form.phone() == null || form.phone().isBlank() || form.password() == null
                || form.password().length() < 8 || form.password().length() > 72) {
            throw new IllegalArgumentException("Thông tin Admin chưa hợp lệ.");
        }
        String passwordHash = passwords.encode(form.password());
        emails.executeWithUniqueEmail(form.email(), canonicalEmail -> {
            User user = new User();
            user.setFullName(form.name().trim());
            user.setEmail(canonicalEmail);
            user.setPasswordHash(passwordHash);
            user.setPhone(form.phone().trim());
            user.setRole(UserRole.ADMIN);
            user.setStatus(UserStatus.ACTIVE);
            accounts.save(user);
            return user.getUserId();
        });
    }

    @Transactional
    public void updateStatus(Long managerId, Long adminId, String status) {
        authorization.requireManager(managerId);
        if (!"ACTIVE".equals(status) && !"INACTIVE".equals(status)) {
            throw new IllegalArgumentException("Trạng thái Admin không hợp lệ.");
        }
        User admin = accounts.findByUserIdAndRole(adminId, UserRole.ADMIN)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy tài khoản Admin."));
        admin.setStatus(UserStatus.valueOf(status));
    }
}
