package com.housekeeping.management.service;

import com.housekeeping.management.domain.StaffType;
import com.housekeeping.management.domain.UserRole;
import com.housekeeping.management.domain.UserStatus;
import com.housekeeping.management.entity.Staff;
import com.housekeeping.management.entity.User;
import com.housekeeping.management.repository.BookingPersistenceRepository;
import com.housekeeping.management.repository.StaffAccountRepository;
import com.housekeeping.management.repository.UserRepository;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminBookingManagementService {
    private static final ZoneOffset BUSINESS_OFFSET = ZoneOffset.ofHours(7);
    private final BookingPersistenceRepository bookings;
    private final StaffAccountRepository staffAccounts;
    private final UserRepository users;

    public AdminBookingManagementService(BookingPersistenceRepository bookings,
            StaffAccountRepository staffAccounts, UserRepository users) {
        this.bookings = bookings;
        this.staffAccounts = staffAccounts;
        this.users = users;
    }

    @Transactional
    public void cancelConfirmedBooking(Long bookingId, Long adminId, String reason) {
        requireActiveAdmin(adminId);
        var booking = bookings.lockBooking(bookingId)
                .orElseThrow(() -> new IllegalArgumentException("Lịch hẹn không tồn tại."));
        if (!"CONFIRMED".equals(booking.status())) {
            throw new IllegalStateException("Chỉ lịch đang CONFIRMED mới được Admin hủy; trạng thái thanh toán khác chưa có quy tắc hoàn tiền.");
        }
        if (bookings.cancel(bookingId, adminId, reason.trim(), now()) != 1) {
            throw new IllegalStateException("Trạng thái lịch hẹn vừa thay đổi. Hãy tải lại trang.");
        }
    }

    @Transactional
    public void reassignConfirmedBooking(Long bookingId, Long adminId, Long replacementStaffId) {
        requireActiveAdmin(adminId);
        var observed = bookings.findBooking(bookingId)
                .orElseThrow(() -> new IllegalArgumentException("Lịch hẹn không tồn tại."));
        if (!"CONFIRMED".equals(observed.status())) {
            throw new IllegalStateException("Chỉ có thể đổi nhân viên cho lịch đang CONFIRMED.");
        }
        // Match booking creation's Staff -> Bookings lock order to avoid a cycle with overlap queries.
        Staff replacement = staffAccounts.findActiveByStaffIdForUpdate(replacementStaffId)
                .orElseThrow(() -> new IllegalArgumentException("Nhân viên thay thế không tồn tại hoặc không hoạt động."));
        var booking = bookings.lockBooking(bookingId)
                .orElseThrow(() -> new IllegalArgumentException("Lịch hẹn không tồn tại."));
        if (!"CONFIRMED".equals(booking.status())) {
            throw new IllegalStateException("Chỉ có thể đổi nhân viên cho lịch đang CONFIRMED.");
        }
        if (booking.staffId().equals(replacementStaffId)) return;
        var start = booking.startTime();
        if (!bookings.companyAcceptsStart(start.toLocalDate(), start.toLocalTime())) {
            throw new IllegalStateException("Giờ bắt đầu của lịch không nằm trong giờ hoạt động hiện được cấu hình.");
        }
        if (bookings.isOnApprovedLeave(replacementStaffId, start.toLocalDate())) {
            throw new IllegalArgumentException("Nhân viên thay thế đã được duyệt nghỉ trong ngày này.");
        }
        if (replacement.getStaffType() == StaffType.PART_TIME
                && !bookings.hasPartTimeAvailability(replacementStaffId, start.toLocalDate(), start.toLocalTime())) {
            throw new IllegalArgumentException("Nhân viên bán thời gian chưa đăng ký sẵn sàng ở giờ bắt đầu này.");
        }
        if (bookings.hasConflictingBookingExcept(replacementStaffId, start, booking.endTime(), bookingId)) {
            throw new IllegalArgumentException("Nhân viên thay thế bị trùng lịch hoặc không đủ khoảng đệm 60 phút.");
        }
        if (bookings.assignStaff(bookingId, booking.staffId(), replacementStaffId) != 1) {
            throw new IllegalStateException("Lịch hẹn vừa thay đổi. Hãy tải lại trang.");
        }
    }

    private void requireActiveAdmin(Long adminId) {
        User admin = users.findById(adminId).orElse(null);
        if (admin == null || admin.getRole() != UserRole.ADMIN || admin.getStatus() != UserStatus.ACTIVE) {
            throw new AccessDeniedException("Chỉ Admin đang hoạt động mới có thể xử lý lịch hẹn.");
        }
    }

    private static LocalDateTime now() { return LocalDateTime.now(BUSINESS_OFFSET).withNano(0); }
}
