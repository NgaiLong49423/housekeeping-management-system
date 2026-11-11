package com.housekeeping.management.service;

import com.housekeeping.management.domain.StaffType;
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

    public void save(Long userId, Long availabilityId, AvailabilityForm form);
    public int saveForDate(Long userId, LocalDate workDate, List<LocalTime> startTimes, List<LocalTime> endTimes);
    public int saveBatch(Long userId, AvailabilityBatchForm form);
    public void replace(Long userId, Long availabilityId, AvailabilityForm form);
    public void delete(Long userId, Long availabilityId);
    void deactivateOutsideCompanyHours(LocalDate from, LocalDate until, List<OperatingHoursForm> proposedHours);
}
