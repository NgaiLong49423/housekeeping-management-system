package com.housekeeping.management.service.impl;

import com.housekeeping.management.integration.vietmap.VietmapClient;
import com.housekeeping.management.service.CompanySettingsService;
import com.housekeeping.management.service.AccountAuthorizationService;

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
    private final AccountAuthorizationService authorization;

    public CompanySettingsServiceImpl(CompanySettingsRepository settings, CompanyOperatingHoursService hours,
            BookingQueryService bookings, StaffAvailabilityService availability, VietmapClient vietmap,
            AccountAuthorizationService authorization) {
        this.settings = settings;
        this.hours = hours;
        this.bookings = bookings;
        this.availability = availability;
        this.vietmap = vietmap;
        this.authorization = authorization;
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
    public void saveLocation(Long adminId, CompanyLocationForm form) {
        authorization.requireActiveAdmin(adminId);
        validateLocation(form);
        List<CompanySettings> companyRows = settings.findAllForUpdate();
        if (companyRows.size() > 1) throw new IllegalStateException("Phát hiện nhiều hơn một cấu hình công ty.");
        CompanySettings existing = companyRows.isEmpty() ? null : companyRows.getFirst();
        boolean selectedNewAddress = form.vietmapRefId() != null && !form.vietmapRefId().isBlank();
        VietmapClient.Place place = selectedNewAddress ? vietmap.place(form.vietmapRefId().trim()) : null;
        if (!selectedNewAddress && (existing == null || form.companyAddress() == null
                || !existing.getCompanyAddress().equals(form.companyAddress().trim()))) {
            throw new IllegalArgumentException("Địa chỉ tâm hiện tại đã được giữ nguyên. Nếu đổi địa chỉ, hãy chọn một kết quả VietMap.");
        }
        java.math.BigDecimal latitude = selectedNewAddress ? place.latitude() : existing.getLatitude();
        java.math.BigDecimal longitude = selectedNewAddress ? place.longitude() : existing.getLongitude();
        String address = selectedNewAddress ? place.display() : existing.getCompanyAddress();
        List<Long> conflicts = bookings.futureActiveBookingsWithLocation().stream()
                .filter(booking -> distanceKm(booking.latitude(), booking.longitude(),
                        latitude, longitude) > form.serviceRadiusKm().doubleValue())
                .map(BookingQueryService.BookingLocation::bookingId).toList();
        if (!conflicts.isEmpty()) {
            throw new IllegalStateException("Chưa thể đổi vị trí/bán kính. Hãy xử lý Booking: " + conflicts);
        }
        CompanySettings company = existing == null ? new CompanySettings() : existing;
        company.setCompanyAddress(address);
        company.setLatitude(latitude);
        company.setLongitude(longitude);
        company.setServiceRadiusKm(form.serviceRadiusKm());
        settings.saveAndFlush(company);
    }

    public List<LocationSuggestion> locationAutocomplete(String query) {
        if (query == null || query.trim().length() < 3 || query.length() > 200) return List.of();
        return vietmap.autocomplete(query.trim()).stream()
                .map(suggestion -> new LocationSuggestion(suggestion.refId(), suggestion.display())).toList();
    }

    @Transactional
    public void saveSchedule(Long adminId, CompanyScheduleVersionForm form) {
        saveSchedule(adminId, form, false);
    }

    @Transactional
    public void saveSchedule(Long adminId, CompanyScheduleVersionForm form, boolean editingExisting) {
        authorization.requireActiveAdmin(adminId);
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
        List<Long> conflicts = bookings.activeBookingWindowsInInterval(form.effectiveFrom(), until).stream()
                .filter(booking -> !coveredByProposedHours(booking, form.operatingHours()))
                .map(BookingQueryService.ActiveBookingWindow::bookingId).toList();
        if (!conflicts.isEmpty()) {
            throw new IllegalStateException("Giờ hoạt động mới không bao phủ lịch của các booking đang hoạt động: " + conflicts
                    + ". Hãy giữ khung giờ phù hợp hoặc xử lý các booking này.");
        }

        hours.saveVersion(companyRows.getFirst().getCompanyId(), form.effectiveFrom(), form.operatingHours());
        availability.deactivateOutsideCompanyHours(form.effectiveFrom(), until, form.operatingHours());
    }

    @Override
    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.SERIALIZABLE)
    public void saveScheduleDay(Long adminId, CompanyScheduleVersionForm form, int dayOfWeek, boolean editingExisting) {
        authorization.requireActiveAdmin(adminId);
        if (form == null || form.effectiveFrom() == null || dayOfWeek < 1 || dayOfWeek > 7) {
            throw new IllegalArgumentException("Ngày cần lưu không hợp lệ.");
        }
        validateSchedule(form.operatingHours());

        LocalDate today = today();
        LocalDate weekStart = form.effectiveFrom();
        if (weekStart.getDayOfWeek() != DayOfWeek.MONDAY) {
            throw new IllegalArgumentException("Phiên bản lịch phải bắt đầu vào Thứ Hai.");
        }
        LocalDate workDate = weekStart.plusDays(dayOfWeek - 1L);
        if (workDate.isBefore(today)) throw new IllegalArgumentException("Không thể chỉnh ngày đã qua.");
        if (weekStart.isBefore(today) && !weekStart.equals(today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)))) {
            throw new IllegalArgumentException("Chỉ được chỉnh lịch tuần hiện tại hoặc tương lai.");
        }

        List<CompanySettings> companyRows = lockCompanySettings("Hãy lưu chính xác một cấu hình công ty trước khi tạo lịch.");
        boolean exactVersionExists = hours.exactVersionDate(weekStart).isPresent();
        if (exactVersionExists != editingExisting) {
            throw new IllegalStateException("Phiên bản lịch vừa thay đổi. Hãy tải lại trang trước khi lưu.");
        }
        if (!hours.hasAnySchedule() && !weekStart.equals(firstSelectableWeekStart())) {
            throw new IllegalArgumentException("Hãy tạo phiên bản giờ hoạt động đầu tiên từ tuần có thể chọn gần nhất.");
        }

        List<OperatingHoursForm> existingWeek = completeWeek(hours.forDate(weekStart));
        List<OperatingHoursForm> proposedDay = form.operatingHours().stream()
                .filter(row -> row.dayOfWeek() == dayOfWeek).toList();
        List<OperatingHoursForm> existingDay = existingWeek.stream()
                .filter(row -> row.dayOfWeek() == dayOfWeek).toList();
        if (workDate.equals(today)) validateTodayChanges(existingDay, proposedDay, LocalTime.now(BUSINESS_ZONE));

        List<OperatingHoursForm> mergedWeek = new ArrayList<>(existingWeek.stream()
                .filter(row -> row.dayOfWeek() != dayOfWeek).toList());
        mergedWeek.addAll(proposedDay);
        validateSchedule(mergedWeek);

        List<Long> conflicts = bookings.activeBookingWindowsInInterval(workDate, workDate.plusDays(1)).stream()
                .filter(booking -> !coveredByProposedHours(booking, proposedDay))
                .map(BookingQueryService.ActiveBookingWindow::bookingId).toList();
        if (!conflicts.isEmpty()) {
            throw new IllegalStateException("Giờ mới không bao phủ booking đang hoạt động trong ngày "
                    + workDate + ": " + conflicts + ". Hãy giữ khung giờ phù hợp hoặc xử lý booking.");
        }

        LocalDate until = hours.nextVersionDate(weekStart);
        hours.saveVersion(companyRows.getFirst().getCompanyId(), weekStart, mergedWeek);
        availability.deactivateOutsideCompanyHours(weekStart, until, mergedWeek);
    }

    private static void validateTodayChanges(List<OperatingHoursForm> existing, List<OperatingHoursForm> proposed,
            LocalTime now) {
        List<OperatingHoursForm> started = existing.stream()
                .filter(row -> !row.closed() && row.openingTime() != null && !row.openingTime().isAfter(now)).toList();
        List<OperatingHoursForm> proposedStarted = proposed.stream()
                .filter(row -> !row.closed() && row.openingTime() != null && !row.openingTime().isAfter(now)).toList();
        if (!canonicalDay(started).equals(canonicalDay(proposedStarted))) {
            throw new IllegalStateException("Không thể sửa, xóa hoặc đóng khung giờ đã bắt đầu hôm nay.");
        }
        List<String> existingCanonical = canonicalDay(existing);
        for (OperatingHoursForm row : proposed) {
            String canonical = row.closed() ? "CLOSED" : row.openingTime() + "-" + row.closingTime();
            if (!existingCanonical.contains(canonical) && !row.closed() && row.closingTime() != null
                    && row.closingTime().isBefore(now)) {
                throw new IllegalStateException("Giờ kết thúc mới không được nằm trước thời điểm hiện tại.");
            }
        }
    }

    @Transactional
    public void cancelSchedule(Long adminId, LocalDate effectiveFrom) {
        authorization.requireActiveAdmin(adminId);
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
                || (form.vietmapRefId() != null && form.vietmapRefId().length() > 500)
                || (form.companyAddress() != null && form.companyAddress().length() > 500)) {
            throw new IllegalArgumentException("Hãy nhập bán kính phục vụ dương và địa chỉ hợp lệ.");
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

    private static boolean coveredByProposedHours(BookingQueryService.ActiveBookingWindow booking,
            List<OperatingHoursForm> proposedHours) {
        if (!booking.start().toLocalDate().equals(booking.end().toLocalDate())) return false;
        int dayOfWeek=booking.start().getDayOfWeek().getValue();
        LocalTime start=booking.start().toLocalTime();
        LocalTime end=booking.end().toLocalTime();
        return proposedHours.stream().anyMatch(window -> window.dayOfWeek()==dayOfWeek && !window.closed()
                && window.openingTime()!=null && window.closingTime()!=null
                && !start.isBefore(window.openingTime()) && !end.isAfter(window.closingTime()));
    }

    private static String dayName(int day) {
        return switch(day){case 1->"Thứ Hai";case 2->"Thứ Ba";case 3->"Thứ Tư";case 4->"Thứ Năm";case 5->"Thứ Sáu";case 6->"Thứ Bảy";default->"Chủ nhật";};
    }
}
