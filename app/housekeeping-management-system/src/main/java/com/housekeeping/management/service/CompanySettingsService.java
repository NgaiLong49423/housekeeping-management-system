package com.housekeeping.management.service;

import com.housekeeping.management.dto.request.PortalForms.CompanyLocationForm;
import com.housekeeping.management.dto.request.PortalForms.CompanyScheduleVersionForm;
import com.housekeeping.management.dto.request.PortalForms.CompanySettingsForm;
import com.housekeeping.management.dto.request.PortalForms.OperatingHoursForm;
import com.housekeeping.management.dto.response.LocationSuggestion;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.DayOfWeek;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public interface CompanySettingsService {
    record ServiceCenter(java.math.BigDecimal latitude, java.math.BigDecimal longitude, java.math.BigDecimal radiusKm) { }
    public record ScheduleWindow(LocalDate effectiveFrom, Integer dayOfWeek, boolean closed,
    LocalTime openingTime, LocalTime closingTime) { }
    public CompanySettingsForm get();
    ServiceCenter serviceCenter();
    public CompanySettingsForm forDate(LocalDate date);
    public CompanyScheduleVersionForm scheduleForEditor(LocalDate date);
    public List<LocalDate> versionDates();
    public List<ScheduleWindow> scheduleWindows();
    public List<OperatingHoursForm> operatingHoursForDate(LocalDate date);
    void lockSettingsForScheduling();
    default boolean acceptsInterval(LocalDate date, LocalTime start, LocalTime end) {
        return operatingHoursForDate(date).stream().anyMatch(window -> window.dayOfWeek() == date.getDayOfWeek().getValue()
                && !window.closed() && window.openingTime() != null && window.closingTime() != null
                && !start.isBefore(window.openingTime()) && !end.isAfter(window.closingTime()));
    }
    default boolean acceptsStart(LocalDate date, LocalTime start) {
        return operatingHoursForDate(date).stream().anyMatch(window -> window.dayOfWeek() == date.getDayOfWeek().getValue()
                && !window.closed() && window.openingTime() != null && window.closingTime() != null
                && !start.isBefore(window.openingTime()) && start.isBefore(window.closingTime()));
    }
    public boolean hasExactVersion(LocalDate date);
    public LocalDate today();
    public LocalDate firstSelectableWeekStart();
    public void saveLocation(Long adminId, CompanyLocationForm form);
    List<LocationSuggestion> locationAutocomplete(String query);
    public void saveSchedule(Long adminId, CompanyScheduleVersionForm form);
    public void saveSchedule(Long adminId, CompanyScheduleVersionForm form, boolean editingExisting);
    public void cancelSchedule(Long adminId, LocalDate effectiveFrom);
    public void validate(CompanySettingsForm form);
    public void validateSchedule(List<OperatingHoursForm> hours);}
