package com.housekeeping.management.service;

import com.housekeeping.management.domain.UserRole;
import com.housekeeping.management.domain.UserStatus;
import com.housekeeping.management.dto.request.PortalForms.StaffCreateForm;
import com.housekeeping.management.dto.request.PortalForms.StaffUpdateForm;
import com.housekeeping.management.entity.Staff;
import com.housekeeping.management.entity.User;
import com.housekeeping.management.repository.BookingPersistenceRepository;
import com.housekeeping.management.repository.StaffAccountRepository;
import jakarta.persistence.EntityManager;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StaffManagementService {
    private final EntityManager entities;
    private final StaffAccountRepository staffAccounts;
    private final BookingPersistenceRepository bookings;
    private final GlobalEmailUniquenessService emails;
    private final PasswordEncoder passwords;
    private final AccountAuthorizationService authorization;

    public StaffManagementService(EntityManager entities, StaffAccountRepository staffAccounts,
            BookingPersistenceRepository bookings,
            GlobalEmailUniquenessService emails, PasswordEncoder passwords,
            AccountAuthorizationService authorization) {
        this.entities = entities;
        this.staffAccounts = staffAccounts;
        this.bookings = bookings;
        this.emails = emails;
        this.passwords = passwords;
        this.authorization = authorization;
    }

    @Transactional
    public void create(Long adminId, StaffCreateForm form) {
        authorization.requireActiveAdmin(adminId);
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
    public void update(Long adminId, Long staffId, StaffUpdateForm form) {
        authorization.requireActiveAdmin(adminId);
        Staff staff = findStaff(staffId);
        staff.getUser().setFullName(form.name().trim());
        if (form.phone() != null && !form.phone().isBlank()) staff.getUser().setPhone(form.phone().trim());
    }

    @Transactional
    public void deactivate(Long adminId, Long staffId) {
        authorization.requireActiveAdmin(adminId);
        Staff staff = staffAccounts.findByStaffIdForUpdate(staffId)
                .orElseThrow(() -> new IllegalArgumentException("Nhân viên không tồn tại."));
        if (bookings.hasUnfinishedBookingForStaff(staffId)) {
            throw new IllegalStateException("Không thể ngừng hoạt động khi nhân viên còn lịch hẹn chưa hoàn tất.");
        }
        staff.getUser().setStatus(UserStatus.INACTIVE);
    }

    @Transactional
    public void activate(Long adminId, Long staffId) {
        authorization.requireActiveAdmin(adminId);
        findStaff(staffId).getUser().setStatus(UserStatus.ACTIVE);
    }

    private Staff findStaff(Long staffId) {
        return staffAccounts.findById(staffId)
                .orElseThrow(() -> new IllegalArgumentException("Nhân viên không tồn tại."));
    }
}
