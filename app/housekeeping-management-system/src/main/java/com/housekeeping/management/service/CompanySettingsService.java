package com.housekeeping.management.service;

import com.housekeeping.management.dto.request.PortalForms.CompanyLocationForm;
import com.housekeeping.management.dto.request.PortalForms.CompanyScheduleVersionForm;
import com.housekeeping.management.dto.request.PortalForms.CompanySettingsForm;
import com.housekeeping.management.dto.request.PortalForms.OperatingHoursForm;
import com.housekeeping.management.repository.CompanySettingsRepository;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.DayOfWeek;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CompanySettingsService {
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private final CompanySettingsRepository settings;

    public CompanySettingsService(CompanySettingsRepository settings) {
        this.settings = settings;
    }

    @Transactional(readOnly = true)
    public CompanySettingsForm get() {
        CompanySettingsForm current = settings.findSettings()
                .orElse(new CompanySettingsForm("", null, null, null, List.of()));
        return withCompleteWeek(current);
    }

    @Transactional(readOnly = true)
    public CompanySettingsForm forDate(LocalDate date) {
        CompanySettingsForm current = settings.findSettingsForDate(date)
                .orElse(new CompanySettingsForm("", null, null, null, List.of()));
        return withCompleteWeek(current);
    }

    private CompanySettingsForm withCompleteWeek(CompanySettingsForm current) {
        List<OperatingHoursForm> complete = completeWeek(current.operatingHours());
        return new CompanySettingsForm(current.companyAddress(), current.latitude(), current.longitude(),
                current.serviceRadiusKm() == null ? new java.math.BigDecimal("10.00") : current.serviceRadiusKm(), complete);
    }

    @Transactional(readOnly = true)
    public CompanyScheduleVersionForm scheduleForEditor(LocalDate date) {
        List<OperatingHoursForm> hours = settings.findOperatingHoursForDate(date);
        if (hours.isEmpty()) hours = closedWeek();
        return new CompanyScheduleVersionForm(date, completeWeek(hours));
    }

    @Transactional(readOnly = true)
    public List<LocalDate> versionDates() {
        return settings.scheduleVersionDates();
    }

    @Transactional(readOnly = true)
    public List<ScheduleWindow> scheduleWindows() {
        return settings.scheduleVersionDates().stream()
                .flatMap(date -> settings.findOperatingHoursForDate(date).stream()
                        .map(hour -> new ScheduleWindow(date, hour.dayOfWeek(), hour.closed(),
                                hour.openingTime(), hour.closingTime())))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<OperatingHoursForm> operatingHoursForDate(LocalDate date) {
        return settings.findOperatingHoursForDate(date);
    }

    @Transactional(readOnly = true)
    public boolean hasExactVersion(LocalDate date) {
        return settings.exactVersionDate(date).isPresent();
    }

    @Transactional(readOnly = true)
    public LocalDate today() {
        return LocalDate.now(BUSINESS_ZONE);
    }

    @Transactional(readOnly = true)
    public LocalDate firstSelectableWeekStart() {
        return today().with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY));
    }

    @Transactional
    public void saveLocation(CompanyLocationForm form) {
        validateLocation(form);
        settings.lockCompanySettings();
        List<Long> conflicts = settings.conflictingFutureConfirmedBookings(form);
        if (!conflicts.isEmpty()) {
            throw new IllegalStateException("Chưa thể đổi vị trí/bán kính. Hãy xử lý Booking: " + conflicts);
        }
        settings.saveLocation(form);
    }

    @Transactional
    public void saveSchedule(CompanyScheduleVersionForm form) {
        saveSchedule(form, false);
    }

    @Transactional
    public void saveSchedule(CompanyScheduleVersionForm form, boolean editingExisting) {
        if (form == null || form.effectiveFrom() == null) {
            throw new IllegalArgumentException("Ngày hiệu lực là bắt buộc.");
        }
        validateSchedule(form.operatingHours());

        LocalDate today = today();
        LocalDate currentWeekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        boolean currentWeek = form.effectiveFrom().equals(currentWeekStart);
        if (form.effectiveFrom().isBefore(today) && !editingExisting && !currentWeek) {
            throw new IllegalArgumentException("Ngày hiệu lực phải là hôm nay hoặc trong tương lai.");
        }
        if (form.effectiveFrom().getDayOfWeek() != DayOfWeek.MONDAY) {
            throw new IllegalArgumentException("Ngày bắt đầu phiên bản phải là Thứ Hai để lịch khớp tuần từ Thứ Hai đến Chủ nhật.");
        }

        List<Long> companyIds = settings.lockCompanySettings();
        if (companyIds.size() != 1) {
            throw new IllegalStateException("Hãy lưu chính xác một cấu hình công ty trước khi tạo lịch.");
        }
        boolean exactVersionExists = settings.exactVersionDate(form.effectiveFrom()).isPresent();
        if (exactVersionExists != editingExisting) {
            throw new IllegalStateException(exactVersionExists
                    ? "Tuần này đã có lịch. Chọn tuần chưa được cấu hình hoặc mở phiên bản hiện có để chỉnh sửa."
                    : "Phiên bản lịch vừa thay đổi. Hãy tải lại trang trước khi lưu.");
        }
        if (form.effectiveFrom().isBefore(today)) {
            LocalDate currentVersion = settings.scheduleVersionDates().stream().filter(date -> !date.isAfter(today))
                    .max(LocalDate::compareTo).orElse(null);
            if (!currentWeek && (!editingExisting || !form.effectiveFrom().equals(currentVersion))) {
                throw new IllegalArgumentException("Chỉ được chỉnh lịch tuần hiện tại hoặc tương lai; tuần cũ chỉ xem được.");
            }
        }
        if (currentWeek && today.getDayOfWeek().getValue() > 1 && settings.hasAnySchedule()) {
            for (int day = 1; day < today.getDayOfWeek().getValue(); day++) {
                final int dayNumber=day;
                LocalDate pastDate=form.effectiveFrom().plusDays(day-1L);
                List<OperatingHoursForm> existing=java.util.Optional.ofNullable(settings.findOperatingHoursForDate(pastDate)).orElse(List.of()).stream()
                        .filter(hour->hour.dayOfWeek()==dayNumber).toList();
                List<OperatingHoursForm> proposed=form.operatingHours().stream()
                        .filter(hour->hour.dayOfWeek()==dayNumber).toList();
                if (!canonicalDay(existing).equals(canonicalDay(proposed))) {
                    throw new IllegalStateException("Không thể chỉnh " + dayName(day) + " đã qua trong tuần hiện tại. Hãy giữ nguyên ngày đó.");
                }
            }
        }
        if (!settings.hasAnySchedule() && !currentWeek && !form.effectiveFrom().equals(firstSelectableWeekStart())) {
            throw new IllegalArgumentException("Hãy tạo phiên bản giờ hoạt động đầu tiên từ tuần có thể chọn gần nhất.");
        }

        LocalDate until = settings.nextVersionDate(form.effectiveFrom());
        List<Long> conflicts = settings.bookingsInVersionInterval(form.effectiveFrom(), until);
        if (!conflicts.isEmpty()) {
            throw new IllegalStateException("Không thể tạo hoặc sửa phiên bản lịch đang có booking hoạt động trong khoảng hiệu lực. Hãy xử lý Booking: " + conflicts);
        }

        settings.saveSchedule(form.effectiveFrom(), form.operatingHours());
    }

    @Transactional
    public void cancelSchedule(LocalDate effectiveFrom) {
        if (effectiveFrom == null || !effectiveFrom.isAfter(today())) {
            throw new IllegalArgumentException("Chỉ có thể hủy phiên bản lịch trong tương lai.");
        }
        List<Long> companyIds = settings.lockCompanySettings();
        if (companyIds.size() != 1) {
            throw new IllegalStateException("Cần đúng một cấu hình công ty để hủy phiên bản lịch.");
        }
        if (settings.exactVersionDate(effectiveFrom).isEmpty()) {
            throw new IllegalArgumentException("Không tìm thấy phiên bản lịch.");
        }

        List<Long> conflicts = settings.bookingsInVersionInterval(effectiveFrom, settings.nextVersionDate(effectiveFrom));
        if (!conflicts.isEmpty()) {
            throw new IllegalStateException("Không thể hủy phiên bản lịch đang có booking hoạt động trong khoảng hiệu lực. Hãy xử lý Booking: " + conflicts);
        }

        settings.cancelSchedule(effectiveFrom);
    }

    public void validate(CompanySettingsForm form) {
        validateLocation(form == null ? null : new CompanyLocationForm(form.companyAddress(), form.latitude(),
                form.longitude(), form.serviceRadiusKm()));
        validateSchedule(form.operatingHours());
    }

    private void validateLocation(CompanyLocationForm form) {
        if (form == null || form.serviceRadiusKm() == null || form.serviceRadiusKm().signum() <= 0
                || form.latitude() == null || form.longitude() == null
                || form.companyAddress() == null || form.companyAddress().isBlank()) {
            throw new IllegalArgumentException("Địa chỉ, tọa độ cơ sở và bán kính dương là bắt buộc.");
        }
    }

    public void validateSchedule(List<OperatingHoursForm> hours) {
        if (hours == null || hours.size() < 7 || hours.size() > 56
                || hours.stream().anyMatch(hour -> hour == null || hour.dayOfWeek() == null)
                || !hours.stream().map(OperatingHoursForm::dayOfWeek).collect(Collectors.toSet())
                        .equals(Set.of(1, 2, 3, 4, 5, 6, 7))) {
            throw new IllegalArgumentException("Cần cấu hình đủ bảy ngày; mỗi ngày có ít nhất một dòng giờ hoặc đánh dấu nghỉ.");
        }

        for (int day = 1; day <= 7; day++) {
            final int dayNumber = day;
            List<OperatingHoursForm> dayHours = hours.stream()
                    .filter(hour -> hour.dayOfWeek().equals(dayNumber)).toList();
            if (dayHours.stream().anyMatch(OperatingHoursForm::closed) && dayHours.size() != 1) {
                throw new IllegalArgumentException("Ngày nghỉ không được đi cùng các khung giờ khác.");
            }

            List<OperatingHoursForm> sorted = dayHours.stream().filter(hour -> !hour.closed())
                    .sorted(java.util.Comparator.comparing(OperatingHoursForm::openingTime,
                            java.util.Comparator.nullsFirst(java.util.Comparator.naturalOrder())))
                    .toList();
            if (sorted.isEmpty() && !dayHours.getFirst().closed()) {
                throw new IllegalArgumentException("Mỗi ngày phải có khung giờ hợp lệ hoặc được đánh dấu nghỉ.");
            }

            LocalTime previousEnd = null;
            for (OperatingHoursForm hour : sorted) {
                if (hour.openingTime() == null || hour.closingTime() == null
                        || !hour.openingTime().isBefore(hour.closingTime())) {
                    throw new IllegalArgumentException("Ngày mở cửa phải có giờ bắt đầu và giờ kết thúc hợp lệ.");
                }
                if (!quarterHour(hour.openingTime()) || !quarterHour(hour.closingTime())) {
                    throw new IllegalArgumentException("Giờ hoạt động chỉ được chọn phút 00, 15, 30 hoặc 45.");
                }
                if (previousEnd != null && hour.openingTime().isBefore(previousEnd)) {
                    throw new IllegalArgumentException("Các khung giờ trong cùng ngày không được chồng lấn.");
                }
                previousEnd = hour.closingTime();
            }
        }
    }

    private static List<OperatingHoursForm> completeWeek(List<OperatingHoursForm> hours) {
        Set<Integer> configuredDays = hours.stream().map(OperatingHoursForm::dayOfWeek).collect(Collectors.toSet());
        List<OperatingHoursForm> complete = new ArrayList<>(hours);
        for (int day = 1; day <= 7; day++) {
            if (!configuredDays.contains(day)) complete.add(new OperatingHoursForm(day, true, null, null));
        }
        return complete;
    }

    private static List<OperatingHoursForm> closedWeek() {
        return java.util.stream.IntStream.rangeClosed(1, 7)
                .mapToObj(day -> new OperatingHoursForm(day, true, null, null)).toList();
    }

    private static boolean quarterHour(LocalTime time) {
        return time.getSecond() == 0 && time.getNano() == 0 && time.getMinute() % 15 == 0;
    }

    private static List<String> canonicalDay(List<OperatingHoursForm> hours) {
        return hours.stream().map(hour->hour.closed()?"CLOSED":hour.openingTime()+"-"+hour.closingTime())
                .sorted().toList();
    }

    private static String dayName(int day) {
        return switch(day){case 1->"Thứ Hai";case 2->"Thứ Ba";case 3->"Thứ Tư";case 4->"Thứ Năm";case 5->"Thứ Sáu";case 6->"Thứ Bảy";default->"Chủ nhật";};
    }

    public record ScheduleWindow(LocalDate effectiveFrom, Integer dayOfWeek, boolean closed,
            LocalTime openingTime, LocalTime closingTime) { }
}
