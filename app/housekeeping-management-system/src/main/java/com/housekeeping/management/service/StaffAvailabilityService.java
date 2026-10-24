package com.housekeeping.management.service;

import com.housekeeping.management.domain.StaffType;
import com.housekeeping.management.dto.request.PortalForms.AvailabilityForm;
import com.housekeeping.management.entity.Staff;
import com.housekeeping.management.repository.StaffAccountRepository;
import com.housekeeping.management.repository.StaffAvailabilityRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StaffAvailabilityService {
    private final StaffAccountRepository staffAccounts;
    private final StaffAvailabilityRepository availability;

    public StaffAvailabilityService(StaffAccountRepository staffAccounts, StaffAvailabilityRepository availability) {
        this.staffAccounts = staffAccounts;
        this.availability = availability;
    }

    @Transactional
    public void save(Long userId, Long availabilityId, AvailabilityForm form) {
        Long staffId = requirePartTimeStaff(userId).getStaffId();
        if (form.startTime() == null || form.endTime() == null || form.workDate() == null
                || !form.endTime().isAfter(form.startTime())) {
            throw new IllegalArgumentException("Giờ kết thúc phải sau giờ bắt đầu.");
        }
        if (availabilityId != null && availability.containsExistingBookingStart(availabilityId, staffId)
                && !availability.unchanged(availabilityId, staffId, form.workDate(), form.startTime(), form.endTime())) {
            throw new IllegalStateException("Không thể đổi khoảng đã có lịch hẹn bắt đầu bên trong.");
        }
        if (availability.overlapsAnotherInterval(staffId, form.workDate(), form.startTime(), form.endTime(), availabilityId)) {
            throw new IllegalArgumentException("Khoảng sẵn sàng bị chồng lên một khoảng khác trong cùng ngày.");
        }
        int affected = availabilityId == null
                ? availability.insert(staffId, form.workDate(), form.startTime(), form.endTime())
                : availability.update(availabilityId, staffId, form.workDate(), form.startTime(), form.endTime());
        if (affected != 1) throw new IllegalArgumentException("Khoảng sẵn sàng không tồn tại.");
    }

    @Transactional
    public void delete(Long userId, Long availabilityId) {
        Long staffId = requirePartTimeStaff(userId).getStaffId();
        if (availability.containsExistingBookingStart(availabilityId, staffId)) {
            throw new IllegalStateException("Không thể xóa khoảng đã có lịch hẹn bắt đầu bên trong.");
        }
        if (availability.delete(availabilityId, staffId) != 1) {
            throw new IllegalArgumentException("Khoảng sẵn sàng không tồn tại.");
        }
    }

    private Staff requirePartTimeStaff(Long userId) {
        Staff staff = staffAccounts.findActiveByUserIdForUpdate(userId)
                .orElseThrow(() -> new AccessDeniedException("Tài khoản Staff không tồn tại hoặc không hoạt động."));
        if (staff.getStaffType() != StaffType.PART_TIME) {
            throw new IllegalStateException("Nhân viên toàn thời gian không đăng ký khoảng sẵn sàng riêng.");
        }
        return staff;
    }
}
