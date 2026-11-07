package com.housekeeping.management.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.housekeeping.management.dto.request.PortalForms.OperatingHoursForm;
import com.housekeeping.management.dto.response.BookingAvailabilityOptions;
import com.housekeeping.management.service.CompanySettingsService.ScheduleWindow;
import com.housekeeping.management.entity.Services;
import com.housekeeping.management.repository.BookingAvailabilityRepository;
import com.housekeeping.management.repository.BookingAvailabilityRepository.Interval;
import com.housekeeping.management.repository.ServicesRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BookingAvailabilityServiceTest {
    @Mock BookingAvailabilityRepository availability;
    @Mock CompanySettingsService companySettings;
    @Mock ServicesRepository services;
    BookingAvailabilityService service;
    LocalDate date;

    @BeforeEach
    void setUp() {
        service = new BookingAvailabilityService(availability,companySettings,services);
        date = LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh")).plusDays(3);
        int day = date.getDayOfWeek().getValue();
        when(services.findAllById(List.of(1L))).thenReturn(List.of(activeService(1L,60)));
        when(companySettings.operatingHoursForDate(date)).thenReturn(List.of(
                new OperatingHoursForm(day,false,LocalTime.of(8,0),LocalTime.of(12,0))));
        when(availability.activeStaff()).thenReturn(List.of(
                new BookingAvailabilityRepository.StaffOption(10L,"Full time","FULL_TIME"),
                new BookingAvailabilityRepository.StaffOption(11L,"Part time","PART_TIME")));
        when(availability.activeAvailability(date)).thenReturn(List.of(new Interval(11L,LocalTime.of(9,0),LocalTime.of(11,0))));
        when(availability.staffOnApprovedLeave(date)).thenReturn(List.of());
        when(availability.bookedIntervals(date)).thenReturn(List.of());
    }

    @Test
    void returnsAdminSlotsAndNarrowsThemToSelectedPartTimeAvailability() {
        var all = service.options(date,List.of(1L),null,null);
        assertThat(all.startTimes()).contains("08:00","11:00").doesNotContain("11:15");
        assertThat(all.staffOptions()).extracting(person -> person.id()).containsExactly(10L,11L);
        assertThat(all.timeOptions().stream().filter(option -> option.startTime().equals("09:00"))
                .findFirst().orElseThrow().staffOptions()).extracting(BookingAvailabilityOptions.StaffOption::name)
                .containsExactly("Full time", "Part time");

        var partTime = service.options(date,List.of(1L),11L,null);
        assertThat(partTime.startTimes()).containsExactly("09:00","09:15","09:30","09:45","10:00");
    }

    @Test
    void explainsWhenSelectedPartTimeStaffHasNotRegisteredThatDate() {
        when(availability.activeAvailability(date)).thenReturn(List.of());

        var result = service.options(date,List.of(1L),11L,null);

        assertThat(result.startTimes()).isEmpty();
        assertThat(result.message()).contains("chưa đăng ký giờ làm");
    }

    @Test
    void availableDatesOnlyReturnsDatesWithAnEligibleRegisteredStaffSlot() {
        LocalDate from=LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh"));
        LocalDate through=from.plusDays(30);
        when(services.findAllById(List.of(1L))).thenReturn(List.of(activeService(1L,60)));
        when(availability.activeStaff()).thenReturn(List.of(new BookingAvailabilityRepository.StaffOption(11L,"Part time","PART_TIME")));
        when(availability.activeAvailability(from,through)).thenReturn(List.of(
                new BookingAvailabilityRepository.DatedInterval(from.plusDays(1),11L,LocalTime.of(9,0),LocalTime.of(12,0))));
        when(availability.approvedLeaves(from,through)).thenReturn(List.of());
        when(availability.bookedIntervals(from,through)).thenReturn(List.of());
        when(companySettings.scheduleWindows()).thenReturn(java.util.stream.IntStream.rangeClosed(1,7)
                .mapToObj(day->new ScheduleWindow(LocalDate.of(1900,1,1),day,false,LocalTime.of(6,0),LocalTime.of(21,0))).toList());

        var result=service.availableDates(List.of(1L),null);

        assertThat(result.dates()).containsExactly(from.plusDays(1));
        service.options(date,List.of(1L),null,null); // Exercise the date-specific stubs from the shared fixture.
    }

    @Test
    void selectedTimeFiltersStaffAndBusyOrLeaveExcludesStaff() {
        when(availability.staffOnApprovedLeave(date)).thenReturn(List.of(10L));
        when(availability.bookedIntervals(date)).thenReturn(List.of(new Interval(11L,LocalTime.of(11,30),LocalTime.NOON)));
        var result = service.options(date,List.of(1L),11L,LocalTime.of(9,0));
        assertThat(result.staffOptions()).extracting(person -> person.id()).containsExactly(11L);
        assertThat(result.startTimes()).contains("09:00","09:15","09:30").doesNotContain("09:45","10:00");
    }

    private Services activeService(Long id,int duration) {
        Services item = new Services();
        item.setServiceId(id);
        item.setServiceName("Cleaning");
        item.setDurationMinutes(duration);
        item.setPrice(new BigDecimal("100000"));
        item.setStatus(Services.Status.ACTIVE);
        return item;
    }
}
