package com.housekeeping.management.service.impl;

import com.housekeeping.management.service.StaffAvailabilityService;
import com.housekeeping.management.service.StaffManagementService;
import com.housekeeping.management.service.CompanyOperatingHoursService;
import com.housekeeping.management.service.BookingQueryService;

import com.housekeeping.management.enums.StaffType;
import com.housekeeping.management.dto.request.PortalForms.AvailabilityForm;
import com.housekeeping.management.dto.request.PortalForms.AvailabilityBatchForm;
import com.housekeeping.management.dto.request.PortalForms.OperatingHoursForm;
import com.housekeeping.management.entity.Staff;
import com.housekeeping.management.repository.StaffAvailabilityRepository;
import java.time.LocalTime;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;

@Service
public class StaffAvailabilityServiceImpl implements StaffAvailabilityService {
    private final StaffManagementService staffManagement;
    private final StaffAvailabilityRepository availability;
    private final CompanyOperatingHoursService settings;
    private final BookingQueryService bookings;

    public StaffAvailabilityServiceImpl(StaffManagementService staffManagement, StaffAvailabilityRepository availability,
            CompanyOperatingHoursService settings, BookingQueryService bookings) {
        this.staffManagement = staffManagement;
        this.availability = availability;
        this.settings = settings;
        this.bookings = bookings;
    }

    @Override
    public AvailabilityFormRules formRules(List<Integer> serviceDurations, java.time.LocalDateTime currentTime) {
        if (currentTime == null) {
            currentTime = java.time.LocalDateTime.now(java.time.ZoneId.of("Asia/Ho_Chi_Minh"));
        }
        List<Integer> validDurations = serviceDurations != null ? serviceDurations.stream().filter(java.util.Objects::nonNull).toList() : List.of();
        int minimumDuration = validDurations.isEmpty() ? 60 : validDurations.stream().mapToInt(Integer::intValue).min().orElse(60);
        java.time.LocalDateTime earliestToday = currentTime.plusMinutes(120);
        int minutes = earliestToday.getHour() * 60 + earliestToday.getMinute();
        int firstAvailableMinute = ((minutes + 14) / 15) * 15;
        return new AvailabilityFormRules(earliestToday, minimumDuration, firstAvailableMinute);
    }

    @Override
    public boolean canRegisterToday(LocalDate today, List<AvailabilityWindow> windows, AvailabilityFormRules rules) {
        if (today == null || windows == null || rules == null || windows.isEmpty()
                || rules.earliestToday().toLocalDate().isAfter(today)) return false;
        return windows.stream().anyMatch(window -> {
            int opening = window.start().getHour() * 60 + window.start().getMinute();
            int start = Math.max(opening, rules.firstAvailableMinute());
            return start + rules.minimumServiceDuration() <= window.end().getHour() * 60 + window.end().getMinute();
        });
    }

    @Transactional(isolation = Isolation.SERIALIZABLE)
    public void save(Long userId, Long availabilityId, AvailabilityForm form) {
        Long staffId = requirePartTimeStaff(userId).getStaffId();
        settings.lockSettingsForScheduling();
        if (availabilityId != null) throw new IllegalArgumentException("Khoảng đã đăng ký phải được thay thế để lưu lịch sử; hãy dùng thao tác cập nhật.");
        validateInterval(staffId, form, null);
        availability.insert(staffId, form.workDate(), form.startTime(), form.endTime());
    }

    @Transactional(isolation = Isolation.SERIALIZABLE)
    public int saveForDate(Long userId, LocalDate workDate, List<LocalTime> startTimes, List<LocalTime> endTimes) {
        Long staffId = requirePartTimeStaff(userId).getStaffId();
        settings.lockSettingsForScheduling();
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

    @Transactional(isolation = Isolation.SERIALIZABLE)
    public int saveBatch(Long userId, AvailabilityBatchForm form) {
        Long staffId = requirePartTimeStaff(userId).getStaffId();
        settings.lockSettingsForScheduling();
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

    @Transactional(isolation = Isolation.SERIALIZABLE)
    public void replace(Long userId, Long availabilityId, AvailabilityForm form) {
        Long staffId = requirePartTimeStaff(userId).getStaffId();
        settings.lockSettingsForScheduling();
        replaceAvailability(staffId, availabilityId, form);
    }

    private void replaceAvailability(Long staffId, Long availabilityId, AvailabilityForm form) {
        if (!availability.isActive(availabilityId, staffId)) {
            throw new IllegalArgumentException("Khoảng sẵn sàng không tồn tại hoặc đã ngừng hiệu lực.");
        }
        if (availability.isForPastDate(availabilityId,staffId,LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh")))) {
            throw new IllegalStateException("Không thể sửa khoảng sẵn sàng của ngày đã qua; lịch sử chỉ được xem.");
        }
        if (hasFutureConfirmedBookingWithinAvailability(availabilityId, staffId)) {
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
        boolean inCompanyWindow = settings.forDate(form.workDate()).stream().anyMatch(hour -> hour.dayOfWeek() == form.workDate().getDayOfWeek().getValue()
                        && !hour.closed() && hour.openingTime() != null && hour.closingTime() != null
                        && !form.startTime().isBefore(hour.openingTime()) && !form.endTime().isAfter(hour.closingTime()));
        if (!inCompanyWindow) throw new IllegalArgumentException("Khoảng Staff phải nằm trọn trong một khung giờ hoạt động của công ty.");
        if (availability.overlapsAnotherInterval(staffId, form.workDate(), form.startTime(), form.endTime(), exceptId)) {
            throw new IllegalArgumentException("Khoảng sẵn sàng bị chồng lên một khoảng khác trong cùng ngày.");
        }
    }

    @Transactional(isolation = Isolation.SERIALIZABLE)
    public void delete(Long userId, Long availabilityId) {
        Long staffId = requirePartTimeStaff(userId).getStaffId();
        settings.lockSettingsForScheduling();
        if (availability.isForPastDate(availabilityId,staffId,LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh")))) {
            throw new IllegalStateException("Không thể thay đổi khoảng sẵn sàng của ngày đã qua; lịch sử chỉ được xem.");
        }
        if (hasFutureConfirmedBookingWithinAvailability(availabilityId, staffId)) {
            throw new IllegalStateException("Không thể ngừng khoảng sẵn sàng đang chứa Booking CONFIRMED trong tương lai. Hãy yêu cầu Admin đổi nhân viên hoặc hủy lịch trước.");
        }
        if (availability.deactivate(availabilityId, staffId) != 1) {
            throw new IllegalArgumentException("Khoảng sẵn sàng không tồn tại.");
        }
    }

    @Override
    @Transactional
    public void deactivateOutsideCompanyHours(LocalDate from, LocalDate until,
            List<OperatingHoursForm> proposedHours) {
        List<com.housekeeping.management.entity.StaffAvailability> active = until == null
                ? availability.findAllByActiveTrueAndWorkDateGreaterThanEqual(from)
                : availability.findAllByActiveTrueAndWorkDateGreaterThanEqualAndWorkDateLessThan(from, until);
        List<com.housekeeping.management.entity.StaffAvailability> toDeactivate = active.stream().filter(interval -> {
            List<OperatingHoursForm> windows = proposedHours == null
                    ? settings.forDate(interval.getWorkDate()) : proposedHours;
            int day = interval.getWorkDate().getDayOfWeek().getValue();
            return windows.stream().noneMatch(window -> window.dayOfWeek() == day && !window.closed()
                    && window.openingTime() != null && window.closingTime() != null
                    && !interval.getStartTime().isBefore(window.openingTime())
                    && !interval.getEndTime().isAfter(window.closingTime()));
        }).toList();
        toDeactivate.forEach(interval -> interval.setActive(false));
        if (!toDeactivate.isEmpty()) availability.saveAll(toDeactivate);
    }

    private static boolean quarterHour(LocalTime t) { return t.getSecond() == 0 && t.getNano() == 0 && t.getMinute() % 15 == 0; }

    private boolean hasFutureConfirmedBookingWithinAvailability(Long availabilityId, Long staffId) {
        return availability.findById(availabilityId)
                .filter(interval -> interval.getStaffId().equals(staffId) && interval.isActive())
                .map(interval -> bookings.hasFutureConfirmedBookingOverlapping(staffId, interval.getWorkDate(),
                        interval.getStartTime(), interval.getEndTime()))
                .orElse(false);
    }

    private Staff requirePartTimeStaff(Long userId) {
        Staff staff = staffManagement.findActiveByUserIdForUpdate(userId);
        if (staff.getStaffType() != StaffType.PART_TIME) {
            throw new IllegalStateException("Nhân viên toàn thời gian không đăng ký khoảng sẵn sàng riêng.");
        }
        return staff;
    }
}
