package com.housekeeping.management.service;

import com.housekeeping.management.enums.UserRole;
import com.housekeeping.management.enums.UserStatus;
import com.housekeeping.management.dto.request.PortalForms.StaffCreateForm;
import com.housekeeping.management.dto.request.PortalForms.StaffUpdateForm;
import com.housekeeping.management.entity.Staff;
import com.housekeeping.management.entity.User;
import jakarta.persistence.EntityManager;
import org.springframework.security.crypto.password.PasswordEncoder;

public interface StaffManagementService {

    public void create(Long adminId, StaffCreateForm form);
    public void update(Long adminId, Long staffId, StaffUpdateForm form);
    public void deactivate(Long adminId, Long staffId, String reason);
    public void activate(Long adminId, Long staffId);
    Staff findActiveForBookingUpdate(Long staffId);
    Staff findActiveByStaffId(Long staffId);
    Staff findByStaffIdForUpdate(Long staffId);
    Staff findActiveByUserId(Long userId);
    Staff findActiveByUserIdForUpdate(Long userId);
    java.util.Optional<Long> findStaffIdByUserId(Long userId);
    java.util.Optional<Long> findUserIdByStaffId(Long staffId);
    boolean isOnApprovedLeave(Long staffId, java.time.LocalDate date);
    boolean acceptsAvailabilityInterval(Long staffId, java.time.LocalDate date,
            java.time.LocalTime start, java.time.LocalTime end);
}
