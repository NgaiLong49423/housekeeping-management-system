package com.housekeeping.management.service;

import com.housekeeping.management.domain.UserRole;
import com.housekeeping.management.domain.UserStatus;
import com.housekeeping.management.dto.request.PortalForms.StaffCreateForm;
import com.housekeeping.management.dto.request.PortalForms.StaffUpdateForm;
import com.housekeeping.management.entity.Staff;
import com.housekeeping.management.entity.User;
import com.housekeeping.management.repository.StaffAccountRepository;
import jakarta.persistence.EntityManager;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StaffManagementService {
    private final EntityManager entities;
    private final StaffAccountRepository staffAccounts;
    private final GlobalEmailUniquenessService emails;
    private final PasswordEncoder passwords;

    public StaffManagementService(EntityManager entities, StaffAccountRepository staffAccounts,
            GlobalEmailUniquenessService emails, PasswordEncoder passwords) {
        this.entities = entities;
        this.staffAccounts = staffAccounts;
        this.emails = emails;
        this.passwords = passwords;
    }

    @Transactional
    public void create(StaffCreateForm form) {
        String passwordHash = passwords.encode(form.password());
        emails.executeWithUniqueEmail(form.email(), canonicalEmail -> {
            User user = new User();
            user.setFullName(form.name().trim());
            user.setEmail(canonicalEmail);
            user.setPasswordHash(passwordHash);
            user.setPhone(form.phone().trim());
            user.setRole(UserRole.STAFF);
            user.setStatus(UserStatus.ACTIVE);
            entities.persist(user);
            entities.flush();
            Staff staff = new Staff();
            staff.setUser(user);
            staff.setStaffType(form.staffType());
            entities.persist(staff);
            entities.flush();
            return null;
        });
    }

    @Transactional
    public void update(Long staffId, StaffUpdateForm form) {
        Staff staff = findStaff(staffId);
        staff.getUser().setFullName(form.name().trim());
        if (form.phone() != null && !form.phone().isBlank()) staff.getUser().setPhone(form.phone().trim());
    }

    @Transactional
    public void deactivate(Long staffId) { findStaff(staffId).getUser().setStatus(UserStatus.INACTIVE); }

    @Transactional
    public void activate(Long staffId) { findStaff(staffId).getUser().setStatus(UserStatus.ACTIVE); }

    private Staff findStaff(Long staffId) {
        return staffAccounts.findById(staffId)
                .orElseThrow(() -> new IllegalArgumentException("Nhân viên không tồn tại."));
    }
}
