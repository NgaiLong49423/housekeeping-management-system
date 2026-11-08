package com.housekeeping.management.service;

import com.housekeeping.management.domain.StaffType;
import com.housekeeping.management.dto.request.PortalForms.AvailabilityForm;
import com.housekeeping.management.dto.request.PortalForms.AvailabilityBatchForm;
import com.housekeeping.management.entity.Staff;
import com.housekeeping.management.repository.StaffAccountRepository;
import com.housekeeping.management.repository.StaffAvailabilityRepository;
import com.housekeeping.management.repository.CompanySettingsRepository;
import java.time.LocalTime;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StaffAvailabilityService {
    private final StaffAccountRepository staffAccounts;
    private final StaffAvailabilityRepository availability;
    private final CompanySettingsRepository settings;

    public StaffAvailabilityService(StaffAccountRepository staffAccounts, StaffAvailabilityRepository availability, CompanySettingsRepository settings) {
        this.staffAccounts = staffAccounts;
        this.availability = availability;
        this.settings = settings;
    }

    @Transactional
    public void save(Long userId, Long availabilityId, AvailabilityForm form) {
        Long staffId = requirePartTimeStaff(userId).getStaffId();
        settings.lockCompanySettings();
        if (availabilityId != null) throw new IllegalArgumentException("Khoảng đã đăng ký phải được thay thế để lưu lịch sử; hãy dùng thao tác cập nhật.");
        validateInterval(staffId, form, null);
        availability.insert(staffId, form.workDate(), form.startTime(), form.endTime());
    }

    @Transactional
    public int saveForDate(Long userId, LocalDate workDate, List<LocalTime> startTimes, List<LocalTime> endTimes) {
        Long staffId = requirePartTimeStaff(userId).getStaffId();
        settings.lockCompanySettings();
        if (workDate == null || startTimes == null || endTimes == null || startTimes.isEmpty()
                || startTimes.size() != endTimes.size() || startTimes.size() > 8) {
            throw new IllegalArgumentException("Hãy chọn từ 1 đến 8 khoảng giờ hợp lệ cho ngày này.");
        }
        List<AvailabilityForm> ranges = new java.util.ArrayList<>();
        for (int i = 0; i < startTimes.size(); i++) {
            AvailabilityForm range = new AvailabilityForm(workDate, startTimes.get(i), endTimes.get(i));
            validateInterval(staffId, range, null);
            for (AvailabilityForm previous : ranges) {
                if (range.startTime().isBefore(previous.endTime()) && range.endTime().isAfter(previous.startTime())) {
                    throw new IllegalArgumentException("Các khoảng giờ bạn chọn đang chồng lấn nhau.");
                }
            }
            ranges.add(range);
        }
        for (AvailabilityForm range : ranges) {
            if (availability.insert(staffId, workDate, range.startTime(), range.endTime()) != 1) {
                throw new IllegalStateException("Không thể lưu các khoảng giờ. Hãy tải lại tuần và thử lại.");
            }
        }
        return ranges.size();
    }

    @Transactional
    public int saveBatch(Long userId, AvailabilityBatchForm form) {
        Long staffId = requirePartTimeStaff(userId).getStaffId();
        settings.lockCompanySettings();
        if (form.startDate() == null || form.endDate() == null || form.endDate().isBefore(form.startDate())) {
            throw new IllegalArgumentException("Ngày kết thúc phải bằng hoặc sau ngày bắt đầu.");
        }
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh"));
        if (form.startDate().isBefore(today)) throw new IllegalArgumentException("Không thể đăng ký khoảng thời gian đã qua.");
        if (form.endDate().isAfter(form.startDate().plusDays(366))) throw new IllegalArgumentException("Mỗi lần đăng ký chỉ áp dụng tối đa trong một năm.");
        Set<Integer> weekdays = form.daysOfWeek() == null ? Set.of() : Set.copyOf(form.daysOfWeek());
        if (weekdays.isEmpty() || weekdays.stream().anyMatch(day -> day < 1 || day > 7)) {
            throw new IllegalArgumentException("Hãy chọn ít nhất một thứ trong tuần hợp lệ.");
        }
        List<LocalDate> dates = Stream.iterate(form.startDate(), date -> !date.isAfter(form.endDate()), date -> date.plusDays(1))
                .filter(date -> weekdays.contains(date.getDayOfWeek().getValue())).toList();
        if (dates.isEmpty()) throw new IllegalArgumentException("Khoảng ngày không có ngày nào khớp với các thứ đã chọn.");
        for (LocalDate date : dates) {
            validateInterval(staffId, new AvailabilityForm(date, form.startTime(), form.endTime()), null);
        }
        for (LocalDate date : dates) {
            if (availability.insert(staffId, date, form.startTime(), form.endTime()) != 1) {
                throw new IllegalStateException("Không thể lưu đầy đủ các ngày đã chọn. Hãy tải lại và thử lại.");
            }
        }
        return dates.size();
    }

    @Transactional
    public void replace(Long userId, Long availabilityId, AvailabilityForm form) {
        Long staffId = requirePartTimeStaff(userId).getStaffId();
        settings.lockCompanySettings();
        replaceAvailability(staffId, availabilityId, form);
    }

    private void replaceAvailability(Long staffId, Long availabilityId, AvailabilityForm form) {
        if (!availability.isActive(availabilityId, staffId)) {
            throw new IllegalArgumentException("Khoảng sẵn sàng không tồn tại hoặc đã ngừng hiệu lực.");
        }
        if (availability.isForPastDate(availabilityId,staffId,LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh")))) {
            throw new IllegalStateException("Không thể sửa khoảng sẵn sàng của ngày đã qua; lịch sử chỉ được xem.");
        }
        if (availability.containsFutureConfirmedBooking(availabilityId, staffId)) {
            throw new IllegalStateException("Không thể thay đổi khoảng đang chứa Booking CONFIRMED trong tương lai. Hãy yêu cầu Admin đổi nhân viên hoặc hủy lịch trước.");
        }
        validateInterval(staffId, form, availabilityId);
        if (availability.deactivate(availabilityId, staffId) != 1
                || availability.insert(staffId, form.workDate(), form.startTime(), form.endTime()) != 1) {
            throw new IllegalStateException("Không thể thay thế khoảng sẵn sàng. Hãy tải lại trang và thử lại.");
        }
    }

    private void validateInterval(Long staffId, AvailabilityForm form, Long exceptId) {
        if (form.startTime() == null || form.endTime() == null || form.workDate() == null
                || !form.endTime().isAfter(form.startTime())) {
            throw new IllegalArgumentException("Giờ kết thúc phải sau giờ bắt đầu.");
        }
        if (form.workDate().isBefore(LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh")))) {
            throw new IllegalArgumentException("Chỉ có thể đăng ký hoặc sửa lịch từ hôm nay trở đi.");
        }
        if (!quarterHour(form.startTime()) || !quarterHour(form.endTime())) throw new IllegalArgumentException("Giờ chỉ được chọn phút 00, 15, 30 hoặc 45.");
        boolean inCompanyWindow = settings.findOperatingHoursForDate(form.workDate()).stream().anyMatch(hour -> hour.dayOfWeek() == form.workDate().getDayOfWeek().getValue()
                        && !hour.closed() && hour.openingTime() != null && hour.closingTime() != null
                        && !form.startTime().isBefore(hour.openingTime()) && !form.endTime().isAfter(hour.closingTime()));
        if (!inCompanyWindow) throw new IllegalArgumentException("Khoảng Staff phải nằm trọn trong một khung giờ hoạt động của công ty.");
        if (availability.overlapsAnotherInterval(staffId, form.workDate(), form.startTime(), form.endTime(), exceptId)) {
            throw new IllegalArgumentException("Khoảng sẵn sàng bị chồng lên một khoảng khác trong cùng ngày.");
        }
    }

    @Transactional
    public void delete(Long userId, Long availabilityId) {
        Long staffId = requirePartTimeStaff(userId).getStaffId();
        settings.lockCompanySettings();
        if (availability.isForPastDate(availabilityId,staffId,LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh")))) {
            throw new IllegalStateException("Không thể thay đổi khoảng sẵn sàng của ngày đã qua; lịch sử chỉ được xem.");
        }
        if (availability.containsFutureConfirmedBooking(availabilityId, staffId)) {
            throw new IllegalStateException("Không thể ngừng khoảng sẵn sàng đang chứa Booking CONFIRMED trong tương lai. Hãy yêu cầu Admin đổi nhân viên hoặc hủy lịch trước.");
        }
        if (availability.deactivate(availabilityId, staffId) != 1) {
            throw new IllegalArgumentException("Khoảng sẵn sàng không tồn tại.");
        }
    }

    private static boolean quarterHour(LocalTime t) { return t.getSecond() == 0 && t.getNano() == 0 && t.getMinute() % 15 == 0; }

    private Staff requirePartTimeStaff(Long userId) {
        Staff staff = staffAccounts.findActiveByUserIdForUpdate(userId)
                .orElseThrow(() -> new AccessDeniedException("Tài khoản Staff không tồn tại hoặc không hoạt động."));
        if (staff.getStaffType() != StaffType.PART_TIME) {
            throw new IllegalStateException("Nhân viên toàn thời gian không đăng ký khoảng sẵn sàng riêng.");
        }
        return staff;
    }
}
