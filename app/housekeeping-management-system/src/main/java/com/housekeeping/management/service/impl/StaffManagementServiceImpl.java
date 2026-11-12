package com.housekeeping.management.service.impl;

import com.housekeeping.management.service.AccountAuthorizationService;
import com.housekeeping.management.service.GlobalEmailUniquenessService;
import com.housekeeping.management.service.StaffManagementService;
import com.housekeeping.management.service.BookingStaffEligibilityService;

import com.housekeeping.management.dto.AccountLockRules;
import com.housekeeping.management.enums.UserRole;
import com.housekeeping.management.enums.UserStatus;
import com.housekeeping.management.dto.request.PortalForms.StaffCreateForm;
import com.housekeeping.management.dto.request.PortalForms.StaffUpdateForm;
import com.housekeeping.management.dto.entity.StaffDTO;
import com.housekeeping.management.dto.entity.UserDTO;
import com.housekeeping.management.entity.Staff;
import com.housekeeping.management.entity.User;
import com.housekeeping.management.repository.StaffAccountRepository;
import com.housekeeping.management.repository.StaffAvailabilityRepository;
import com.housekeeping.management.repository.StaffLeaveRepository;
import jakarta.persistence.EntityManager;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StaffManagementServiceImpl implements StaffManagementService {
    private final EntityManager entities;
    private final StaffAccountRepository staffAccounts;
    private final StaffAvailabilityRepository availability;
    private final StaffLeaveRepository leaves;
    private final BookingStaffEligibilityService bookingEligibility;
    private final GlobalEmailUniquenessService emails;
    private final PasswordEncoder passwords;
    private final AccountAuthorizationService authorization;

    public StaffManagementServiceImpl(EntityManager entities, StaffAccountRepository staffAccounts,
            StaffAvailabilityRepository availability, StaffLeaveRepository leaves,
            BookingStaffEligibilityService bookingEligibility,
            GlobalEmailUniquenessService emails, PasswordEncoder passwords,
            AccountAuthorizationService authorization) {
        this.entities = entities;
        this.staffAccounts = staffAccounts;
        this.availability = availability;
        this.leaves = leaves;
        this.bookingEligibility = bookingEligibility;
        this.emails = emails;
        this.passwords = passwords;
        this.authorization = authorization;
    }

    @Transactional
    public void create(Long adminId, StaffCreateForm form) {
        authorization.requireActiveAdmin(adminId);
        String passwordHash = passwords.encode(form.password());
        emails.executeWithUniqueEmail(form.email(), canonicalEmail -> {
            UserDTO userDto = new UserDTO(null, form.name().trim(), canonicalEmail, form.phone().trim(),
                    UserRole.STAFF, UserStatus.ACTIVE);
            User user = userDto.convertDTOToEntity(passwordHash);
            entities.persist(user);
            entities.flush();
            StaffDTO staffDto = new StaffDTO(null, userDto, form.staffType());
            Staff staff = staffDto.convertDTOToEntity();
            staff.setUser(user);
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
    public void deactivate(Long adminId, Long staffId, String reason) {
        authorization.requireActiveAdmin(adminId);
        String lockReason = AccountLockRules.requireReason(reason);
        Staff staff = staffAccounts.findByStaffIdForUpdate(staffId)
                .orElseThrow(() -> new IllegalArgumentException("Nhân viên không tồn tại."));
        if (bookingEligibility.hasUnfinishedBookingForStaff(staffId)) {
            throw new IllegalStateException("Không thể ngừng hoạt động khi nhân viên còn lịch hẹn chưa hoàn tất.");
        }
        staff.getUser().lock(lockReason, adminId, AccountLockRules.now());
    }

    @Transactional
    public void activate(Long adminId, Long staffId) {
        authorization.requireActiveAdmin(adminId);
        findStaff(staffId).getUser().unlock(adminId, AccountLockRules.now());
    }

    @Transactional
    public Staff findActiveForBookingUpdate(Long staffId) {
        return staffAccounts.findActiveByStaffIdForUpdate(staffId)
                .orElseThrow(() -> new IllegalArgumentException("Nhân viên không tồn tại hoặc không hoạt động."));
    }

    @Override
    @Transactional(readOnly = true)
    public Staff findActiveByStaffId(Long staffId) {
        return staffAccounts.findById(staffId)
                .filter(staff -> staff.getUser().getRole() == UserRole.STAFF && staff.getUser().getStatus() == UserStatus.ACTIVE)
                .orElseThrow(() -> new IllegalArgumentException("Tài khoản nhân viên không còn hoạt động."));
    }

    @Override
    @Transactional
    public Staff findByStaffIdForUpdate(Long staffId) {
        return staffAccounts.findByStaffIdForUpdate(staffId)
                .orElseThrow(() -> new IllegalArgumentException("Nhân viên của đơn nghỉ không tồn tại."));
    }

    @Override
    @Transactional(readOnly = true)
    public Staff findActiveByUserId(Long userId) {
        return staffAccounts.findByUser_UserId(userId)
                .filter(staff -> staff.getUser().getRole() == UserRole.STAFF && staff.getUser().getStatus() == UserStatus.ACTIVE)
                .orElseThrow(() -> new org.springframework.security.access.AccessDeniedException("Tài khoản Staff không tồn tại hoặc không hoạt động."));
    }

    @Override
    @Transactional
    public Staff findActiveByUserIdForUpdate(Long userId) {
        return staffAccounts.findActiveByUserIdForUpdate(userId)
                .orElseThrow(() -> new org.springframework.security.access.AccessDeniedException("Tài khoản Staff không tồn tại hoặc không hoạt động."));
    }

    @Override
    @Transactional(readOnly = true)
    public java.util.Optional<Long> findStaffIdByUserId(Long userId) {
        return staffAccounts.findByUser_UserId(userId).map(Staff::getStaffId);
    }

    @Override
    @Transactional(readOnly = true)
    public java.util.Optional<Long> findUserIdByStaffId(Long staffId) {
        return staffAccounts.findById(staffId).map(profile -> profile.getUser().getUserId());
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isOnApprovedLeave(Long staffId, java.time.LocalDate date) {
        return leaves.existsByStaff_StaffIdAndStatusAndStartDateLessThanEqualAndEndDateGreaterThanEqual(
                staffId, com.housekeeping.management.enums.LeaveStatus.APPROVED, date, date);
    }

    @Override
    @Transactional
    public boolean acceptsAvailabilityInterval(Long staffId, java.time.LocalDate date,
            java.time.LocalTime start, java.time.LocalTime end) {
        return availability.findAllByStaffIdAndWorkDateAndActiveTrue(staffId, date).stream()
                .anyMatch(window -> !start.isBefore(window.getStartTime())
                        && !end.isAfter(window.getEndTime()));
    }

    private Staff findStaff(Long staffId) {
        return staffAccounts.findById(staffId)
                .orElseThrow(() -> new IllegalArgumentException("Nhân viên không tồn tại."));
    }
}
