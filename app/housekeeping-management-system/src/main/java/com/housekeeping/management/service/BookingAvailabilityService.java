package com.housekeeping.management.service;

import com.housekeeping.management.dto.request.PortalForms.OperatingHoursForm;
import com.housekeeping.management.dto.response.BookingAvailabilityOptions;
import com.housekeeping.management.dto.response.BookingAvailabilityOptions.StaffOption;
import com.housekeeping.management.dto.response.BookingAvailabilityOptions.TimeOption;
import com.housekeeping.management.dto.response.BookingAvailabilityDates;
import com.housekeeping.management.entity.Services;
import com.housekeeping.management.entity.House;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

public interface BookingAvailabilityService {

    public BookingAvailabilityOptions options(LocalDate date, List<Long> serviceIds, Long selectedStaffId,
            LocalTime selectedTime, Long customerId, Long houseId);
    public BookingAvailabilityOptions optionsForStaffIds(LocalDate date, List<Long> serviceIds, List<Long> selectedStaffIds,
            LocalTime selectedTime, Long customerId, Long houseId);
    public BookingAvailabilityOptions optionsForStaffIds(LocalDate date, List<Long> serviceIds, List<Long> selectedStaffIds,
            LocalTime selectedTime, Long customerId, Long houseId, Long excludeBookingId);
    public BookingAvailabilityOptions optionsForStaffIds(LocalDate date, List<Long> serviceIds, List<Long> selectedStaffIds,
            LocalTime selectedTime, Long customerId, Long houseId, Long excludeBookingId, Integer durationOverride);
    public BookingAvailabilityDates availableDates(List<Long> serviceIds, Long selectedStaffId, Long customerId, Long houseId);
    public BookingAvailabilityDates availableDatesForStaffIds(List<Long> serviceIds, List<Long> selectedStaffIds,
            Long customerId, Long houseId);
    public BookingAvailabilityDates availableDatesForStaffIds(List<Long> serviceIds, List<Long> selectedStaffIds,
            Long customerId, Long houseId, Long excludeBookingId);
    public BookingAvailabilityDates availableDatesForStaffIds(List<Long> serviceIds, List<Long> selectedStaffIds,
            Long customerId, Long houseId, Long excludeBookingId, Integer durationOverride);
}
