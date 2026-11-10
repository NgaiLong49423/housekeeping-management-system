package com.housekeeping.management.service;

import com.housekeeping.management.dto.request.PortalForms.CompanyLocationForm;
import com.housekeeping.management.dto.request.PortalForms.CompanyScheduleVersionForm;
import com.housekeeping.management.dto.request.PortalForms.CompanySettingsForm;
import com.housekeeping.management.dto.request.PortalForms.OperatingHoursForm;
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
    public record ScheduleWindow(LocalDate effectiveFrom, Integer dayOfWeek, boolean closed,
    LocalTime openingTime, LocalTime closingTime) { }
    public CompanySettingsForm get();
    public CompanySettingsForm forDate(LocalDate date);
    public CompanyScheduleVersionForm scheduleForEditor(LocalDate date);
    public List<LocalDate> versionDates();
    public List<ScheduleWindow> scheduleWindows();
    public List<OperatingHoursForm> operatingHoursForDate(LocalDate date);
    void lockSettingsForScheduling();
    public boolean hasExactVersion(LocalDate date);
    public LocalDate today();
    public LocalDate firstSelectableWeekStart();
    public void saveLocation(CompanyLocationForm form);
    public List<VietmapClient.Suggestion> locationAutocomplete(String query);
    public void saveSchedule(CompanyScheduleVersionForm form);
    public void saveSchedule(CompanyScheduleVersionForm form, boolean editingExisting);
    public void cancelSchedule(LocalDate effectiveFrom);
    public void validate(CompanySettingsForm form);
    public void validateSchedule(List<OperatingHoursForm> hours);}
