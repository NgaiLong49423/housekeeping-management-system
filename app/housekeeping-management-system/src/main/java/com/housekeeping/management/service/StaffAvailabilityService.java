package com.housekeeping.management.service;

import com.housekeeping.management.enums.StaffType;
import com.housekeeping.management.dto.request.PortalForms.AvailabilityForm;
import com.housekeeping.management.dto.request.PortalForms.AvailabilityBatchForm;
import com.housekeeping.management.dto.request.PortalForms.OperatingHoursForm;
import com.housekeeping.management.entity.Staff;
import java.time.LocalTime;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.springframework.security.access.AccessDeniedException;

public interface StaffAvailabilityService {
    record AvailabilityWindow(LocalTime start, LocalTime end) { }
    record AvailabilityFormRules(java.time.LocalDateTime earliestToday, int minimumServiceDuration, int firstAvailableMinute) { }

    AvailabilityFormRules formRules(List<Integer> serviceDurations, java.time.LocalDateTime currentTime);
    boolean canRegisterToday(LocalDate today, List<AvailabilityWindow> windows, AvailabilityFormRules rules);

    public void save(Long userId, Long availabilityId, AvailabilityForm form);
    public int saveForDate(Long userId, LocalDate workDate, List<LocalTime> startTimes, List<LocalTime> endTimes);
    public int saveBatch(Long userId, AvailabilityBatchForm form);
    public void replace(Long userId, Long availabilityId, AvailabilityForm form);
    public void delete(Long userId, Long availabilityId);
    void deactivateOutsideCompanyHours(LocalDate from, LocalDate until, List<OperatingHoursForm> proposedHours);
}
