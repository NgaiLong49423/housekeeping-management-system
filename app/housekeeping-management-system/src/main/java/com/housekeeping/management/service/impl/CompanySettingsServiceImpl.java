package com.housekeeping.management.service.impl;

import com.housekeeping.management.integration.vietmap.VietmapClient;
import com.housekeeping.management.service.CompanySettingsService;

import com.housekeeping.management.dto.request.PortalForms.CompanyLocationForm;
import com.housekeeping.management.dto.request.PortalForms.CompanyScheduleVersionForm;
import com.housekeeping.management.dto.request.PortalForms.CompanySettingsForm;
import com.housekeeping.management.dto.request.PortalForms.OperatingHoursForm;
import com.housekeeping.management.repository.CompanySettingsRepository;
import com.housekeeping.management.service.BookingQueryService;
import com.housekeeping.management.service.CompanyOperatingHoursService;
import com.housekeeping.management.service.StaffAvailabilityService;
import com.housekeeping.management.dto.response.LocationSuggestion;
import com.housekeeping.management.entity.CompanySettings;
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
public class CompanySettingsServiceImpl implements CompanySettingsService {
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private final CompanySettingsRepository settings;
    private final CompanyOperatingHoursService hours;
    private final BookingQueryService bookings;
    private final StaffAvailabilityService availability;
    private final VietmapClient vietmap;

    public CompanySettingsServiceImpl(CompanySettingsRepository settings, CompanyOperatingHoursService hours,
            BookingQueryService bookings, StaffAvailabilityService availability, VietmapClient vietmap) {
        this.settings = settings;
        this.hours = hours;
        this.bookings = bookings;
        this.availability = availability;
        this.vietmap = vietmap;
    }

    @Transactional(readOnly = true)
    public CompanySettingsForm get() {
        CompanySettingsForm current = settingsForDate(today())
                .orElse(new CompanySettingsForm("", null, null, null, List.of()));
        return withCompleteWeek(current);
    }

    private java.util.Optional<CompanySettingsForm> settingsForDate(LocalDate date) {
        List<CompanySettings> companies = settings.findAllByOrderByCompanyIdAsc();
        if (companies.size() > 1) throw new IllegalStateException("Phát hiện nhiều hơn một cấu hình công ty.");
        return companies.stream().findFirst().map(company -> new CompanySettingsForm(company.getCompanyAddress(),
                company.getLatitude(), company.getLongitude(), company.getServiceRadiusKm(), hours.forDate(date)));
    }

    @Override
    @Transactional(readOnly = true)
    public ServiceCenter serviceCenter() {
        CompanySettingsForm form = settingsForDate(today())
                .orElseThrow(() -> new IllegalStateException("Cần cấu hình chính xác một vị trí và bán kính phục vụ của công ty."));
        if (form.latitude() == null || form.longitude() == null || form.serviceRadiusKm() == null) {
            throw new IllegalStateException("Admin chưa cấu hình tâm và bán kính phục vụ của công ty.");
        }
        return new ServiceCenter(form.latitude(), form.longitude(), form.serviceRadiusKm());
    }

    @Transactional(readOnly = true)
    public CompanySettingsForm forDate(LocalDate date) {
        CompanySettingsForm current = settingsForDate(date)
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
        List<OperatingHoursForm> schedule = hours.forDate(date);
        if (schedule.isEmpty()) schedule = closedWeek();
        return new CompanyScheduleVersionForm(date, completeWeek(schedule));
    }

    @Transactional(readOnly = true)
    public List<LocalDate> versionDates() {
        return hours.versionDates();
    }

    @Transactional(readOnly = true)
    public List<ScheduleWindow> scheduleWindows() {
        return hours.versionDates().stream()
                .flatMap(date -> hours.forDate(date).stream()
                        .map(hour -> new ScheduleWindow(date, hour.dayOfWeek(), hour.closed(),
                                hour.openingTime(), hour.closingTime())))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<OperatingHoursForm> operatingHoursForDate(LocalDate date) {
        return hours.forDate(date);
    }

    @Override
    @Transactional
    public void lockSettingsForScheduling() {
        lockCompanySettings("Cần cấu hình chính xác một vị trí và giờ hoạt động của công ty.");
    }

    @Transactional(readOnly = true)
    public boolean hasExactVersion(LocalDate date) {
        return hours.exactVersionDate(date).isPresent();
    }

    @Transactional(readOnly = true)
    public LocalDate today() {
        return LocalDate.now(BUSINESS_ZONE);
    }

    @Transactional(readOnly = true)
    public LocalDate firstSelectableWeekStart() {
        return today().with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY));
    }

    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.SERIALIZABLE)
    public void saveLocation(CompanyLocationForm form) {
        validateLocation(form);
        VietmapClient.Place place = vietmap.place(form.vietmapRefId().trim());
        if (place.display() == null || place.display().isBlank() || place.display().length() > 500
                || place.latitude().abs().compareTo(new java.math.BigDecimal("90")) > 0
                || place.longitude().abs().compareTo(new java.math.BigDecimal("180")) > 0) {
            throw new IllegalArgumentException("Địa chỉ VietMap không có thông tin vị trí hợp lệ.");
        }
        List<CompanySettings> companyRows = settings.findAllForUpdate();
        if (companyRows.size() > 1) throw new IllegalStateException("Phát hiện nhiều hơn một cấu hình công ty.");
        List<Long> conflicts = bookings.futureActiveBookingsWithLocation().stream()
                .filter(booking -> distanceKm(booking.latitude(), booking.longitude(),
                        place.latitude(), place.longitude()) > form.serviceRadiusKm().doubleValue())
                .map(BookingQueryService.BookingLocation::bookingId).toList();
        if (!conflicts.isEmpty()) {
            throw new IllegalStateException("Chưa thể đổi vị trí/bán kính. Hãy xử lý Booking: " + conflicts);
        }
        CompanySettings company = companyRows.isEmpty() ? new CompanySettings() : companyRows.getFirst();
        company.setCompanyAddress(place.display());
        company.setLatitude(place.latitude());
        company.setLongitude(place.longitude());
        company.setServiceRadiusKm(form.serviceRadiusKm());
        settings.saveAndFlush(company);
    }

    public List<LocationSuggestion> locationAutocomplete(String query) {
        if (query == null || query.trim().length() < 3 || query.length() > 200) return List.of();
        return vietmap.autocomplete(query.trim()).stream()
                .map(suggestion -> new LocationSuggestion(suggestion.refId(), suggestion.display())).toList();
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

        List<CompanySettings> companyRows = lockCompanySettings("Hãy lưu chính xác một cấu hình công ty trước khi tạo lịch.");
        boolean exactVersionExists = hours.exactVersionDate(form.effectiveFrom()).isPresent();
        if (exactVersionExists != editingExisting) {
            throw new IllegalStateException(exactVersionExists
                    ? "Tuần này đã có lịch. Chọn tuần chưa được cấu hình hoặc mở phiên bản hiện có để chỉnh sửa."
                    : "Phiên bản lịch vừa thay đổi. Hãy tải lại trang trước khi lưu.");
        }
        if (form.effectiveFrom().isBefore(today)) {
            LocalDate currentVersion = hours.versionDates().stream().filter(date -> !date.isAfter(today))
                    .max(LocalDate::compareTo).orElse(null);
            if (!currentWeek && (!editingExisting || !form.effectiveFrom().equals(currentVersion))) {
                throw new IllegalArgumentException("Chỉ được chỉnh lịch tuần hiện tại hoặc tương lai; tuần cũ chỉ xem được.");
            }
        }
        if (currentWeek && today.getDayOfWeek().getValue() > 1 && hours.hasAnySchedule()) {
            for (int day = 1; day < today.getDayOfWeek().getValue(); day++) {
                final int dayNumber=day;
                LocalDate pastDate=form.effectiveFrom().plusDays(day-1L);
                List<OperatingHoursForm> existing=java.util.Optional.ofNullable(hours.forDate(pastDate)).orElse(List.of()).stream()
                        .filter(hour->hour.dayOfWeek()==dayNumber).toList();
                List<OperatingHoursForm> proposed=form.operatingHours().stream()
                        .filter(hour->hour.dayOfWeek()==dayNumber).toList();
                if (!canonicalDay(existing).equals(canonicalDay(proposed))) {
                    throw new IllegalStateException("Không thể chỉnh " + dayName(day) + " đã qua trong tuần hiện tại. Hãy giữ nguyên ngày đó.");
                }
            }
        }
        if (!hours.hasAnySchedule() && !currentWeek && !form.effectiveFrom().equals(firstSelectableWeekStart())) {
            throw new IllegalArgumentException("Hãy tạo phiên bản giờ hoạt động đầu tiên từ tuần có thể chọn gần nhất.");
        }

        LocalDate until = hours.nextVersionDate(form.effectiveFrom());
        List<Long> conflicts = bookings.activeBookingIdsInInterval(form.effectiveFrom(), until);
        if (!conflicts.isEmpty()) {
            throw new IllegalStateException("Không thể tạo hoặc sửa phiên bản lịch đang có booking hoạt động trong khoảng hiệu lực. Hãy xử lý Booking: " + conflicts);
        }

        hours.saveVersion(companyRows.getFirst().getCompanyId(), form.effectiveFrom(), form.operatingHours());
        availability.deactivateOutsideCompanyHours(form.effectiveFrom(), until, form.operatingHours());
    }

    @Transactional
    public void cancelSchedule(LocalDate effectiveFrom) {
        if (effectiveFrom == null || !effectiveFrom.isAfter(today())) {
            throw new IllegalArgumentException("Chỉ có thể hủy phiên bản lịch trong tương lai.");
        }
        List<CompanySettings> companyRows = lockCompanySettings("Cần đúng một cấu hình công ty để hủy phiên bản lịch.");
        if (hours.exactVersionDate(effectiveFrom).isEmpty()) {
            throw new IllegalArgumentException("Không tìm thấy phiên bản lịch.");
        }

        LocalDate until = hours.nextVersionDate(effectiveFrom);
        List<Long> conflicts = bookings.activeBookingIdsInInterval(effectiveFrom, until);
        if (!conflicts.isEmpty()) {
            throw new IllegalStateException("Không thể hủy phiên bản lịch đang có booking hoạt động trong khoảng hiệu lực. Hãy xử lý Booking: " + conflicts);
        }

        hours.deleteVersion(companyRows.getFirst().getCompanyId(), effectiveFrom);
        availability.deactivateOutsideCompanyHours(effectiveFrom, until, null);
    }

    public void validate(CompanySettingsForm form) {
        if (form == null || form.companyAddress() == null || form.companyAddress().isBlank()
                || form.latitude() == null || form.latitude().abs().compareTo(new java.math.BigDecimal("90")) > 0
                || form.longitude() == null || form.longitude().abs().compareTo(new java.math.BigDecimal("180")) > 0
                || form.serviceRadiusKm() == null || form.serviceRadiusKm().signum() <= 0) {
            throw new IllegalArgumentException("Địa chỉ, tọa độ cơ sở và bán kính dương là bắt buộc.");
        }
        validateSchedule(form.operatingHours());
    }

    private void validateLocation(CompanyLocationForm form) {
        if (form == null || form.serviceRadiusKm() == null || form.serviceRadiusKm().signum() <= 0
                || form.vietmapRefId() == null || form.vietmapRefId().isBlank() || form.vietmapRefId().length() > 500) {
            throw new IllegalArgumentException("Hãy chọn địa chỉ VietMap và nhập bán kính phục vụ dương.");
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

    private List<CompanySettings> lockCompanySettings(String errorMessage) {
        List<CompanySettings> companyRows = settings.findAllForUpdate();
        if (companyRows.size() != 1) throw new IllegalStateException(errorMessage);
        return companyRows;
    }

    private static double distanceKm(java.math.BigDecimal latitudeA, java.math.BigDecimal longitudeA,
            java.math.BigDecimal latitudeB, java.math.BigDecimal longitudeB) {
        if (latitudeA == null || longitudeA == null) return Double.POSITIVE_INFINITY;
        double firstLatitude = Math.toRadians(latitudeA.doubleValue());
        double secondLatitude = Math.toRadians(latitudeB.doubleValue());
        double latitudeDelta = secondLatitude - firstLatitude;
        double longitudeDelta = Math.toRadians(longitudeB.doubleValue() - longitudeA.doubleValue());
        double haversine = Math.pow(Math.sin(latitudeDelta / 2), 2)
                + Math.cos(firstLatitude) * Math.cos(secondLatitude) * Math.pow(Math.sin(longitudeDelta / 2), 2);
        return 12742.0176 * Math.asin(Math.sqrt(Math.min(1, haversine)));
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
}
