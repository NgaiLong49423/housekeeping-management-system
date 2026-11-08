package com.housekeeping.management.service;

import com.housekeeping.management.domain.StaffType;
import com.housekeeping.management.entity.Staff;
import com.housekeeping.management.repository.BookingPersistenceRepository;
import com.housekeeping.management.repository.StaffAccountRepository;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminBookingManagementService {
    private static final ZoneOffset BUSINESS_OFFSET = ZoneOffset.ofHours(7);
    private final BookingPersistenceRepository bookings;
    private final StaffAccountRepository staffAccounts;
    private final AccountAuthorizationService authorization;
    private final BookingReservationService reservations;
    private final HouseService houses;

    public AdminBookingManagementService(BookingPersistenceRepository bookings,
            StaffAccountRepository staffAccounts, AccountAuthorizationService authorization,
            BookingReservationService reservations, HouseService houses) {
        this.bookings = bookings;
        this.staffAccounts = staffAccounts;
        this.authorization = authorization;
        this.reservations = reservations;
        this.houses = houses;
    }

    @Transactional
    public void cancelConfirmedBooking(Long bookingId, Long adminId, String reason) {
        authorization.requireActiveAdmin(adminId);
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
        authorization.requireActiveAdmin(adminId);
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
        bookings.lockCompanySettings();
        var start = booking.startTime();
        if (!bookings.companyAcceptsInterval(start.toLocalDate(), start.toLocalTime(), booking.endTime().toLocalTime())
                || booking.endTime().toLocalDate().isAfter(start.toLocalDate())) {
            throw new IllegalStateException("Lịch không nằm trọn trong cùng một khung giờ hoạt động hiện được cấu hình.");
        }
        if (bookings.isOnApprovedLeave(replacementStaffId, start.toLocalDate())) {
            throw new IllegalArgumentException("Nhân viên thay thế đã được duyệt nghỉ trong ngày này.");
        }
        if (replacement.getStaffType() == StaffType.PART_TIME
                && !bookings.partTimeAcceptsInterval(replacementStaffId, start.toLocalDate(), start.toLocalTime(), booking.endTime().toLocalTime())) {
            throw new IllegalArgumentException("Toàn bộ lịch phải nằm trong khoảng sẵn sàng của nhân viên bán thời gian.");
        }
        if (booking.latitude() == null || booking.longitude() == null || !houses.isWithinServiceRadius(booking.latitude(), booking.longitude())) throw new IllegalArgumentException("Địa chỉ Booking nằm ngoài vùng phục vụ hoặc thiếu tọa độ.");
        BookingReservationService.TravelAssessment travel=reservations.assessTravel(replacementStaffId,start,booking.endTime(),
                booking.latitude(),booking.longitude(),bookingId);
        if(!travel.feasible()) {
            if(travel.status()==BookingReservationService.TravelStatus.ROUTE_UNAVAILABLE)throw new IllegalStateException(travel.message());
            throw new IllegalArgumentException(travel.message());
        }
        if (bookings.assignStaff(bookingId, booking.staffId(), replacementStaffId) != 1) {
            throw new IllegalStateException("Lịch hẹn vừa thay đổi. Hãy tải lại trang.");
        }
    }

    private static LocalDateTime now() { return LocalDateTime.now(BUSINESS_OFFSET).withNano(0); }
}
