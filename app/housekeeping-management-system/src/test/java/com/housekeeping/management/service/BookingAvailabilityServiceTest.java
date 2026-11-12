package com.housekeeping.management.service;

import com.housekeeping.management.enums.ServiceStatus;
import com.housekeeping.management.enums.TravelStatus;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.housekeeping.management.dto.request.PortalForms.OperatingHoursForm;
import com.housekeeping.management.dto.response.BookingAvailabilityOptions;
import com.housekeeping.management.service.CompanySettingsService.ScheduleWindow;
import com.housekeeping.management.entity.Services;
import com.housekeeping.management.entity.House;
import com.housekeeping.management.repository.BookingAvailabilityRepository;
import com.housekeeping.management.repository.BookingAvailabilityRepository.Interval;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
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
    @Mock ServiceManagementService services;
    @Mock HouseService houses;
    @Mock BookingReservationService reservations;
    BookingAvailabilityService service;
    LocalDate date;

    @BeforeEach
    void setUp() {
        service = new com.housekeeping.management.service.impl.BookingAvailabilityServiceImpl(availability,companySettings,services,houses,reservations);
        date = LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh")).plusDays(3);
        int day = date.getDayOfWeek().getValue();
        org.mockito.Mockito.lenient().when(services.findActiveByIds(List.of(1L))).thenReturn(List.of(activeService(1L,60)));
        org.mockito.Mockito.lenient().when(companySettings.operatingHoursForDate(date)).thenReturn(List.of(
                new OperatingHoursForm(day,false,LocalTime.of(8,0),LocalTime.of(12,0))));
        org.mockito.Mockito.lenient().when(availability.activeStaff()).thenReturn(List.of(
                new BookingAvailabilityRepository.StaffOption(10L,"Full time","FULL_TIME"),
                new BookingAvailabilityRepository.StaffOption(11L,"Part time","PART_TIME")));
        org.mockito.Mockito.lenient().when(availability.activeAvailability(date)).thenReturn(List.of(new Interval(11L,LocalTime.of(9,0),LocalTime.of(11,0))));
        org.mockito.Mockito.lenient().when(availability.staffOnApprovedLeave(date)).thenReturn(List.of());
        org.mockito.Mockito.lenient().when(availability.bookedIntervals(date)).thenReturn(List.of());
        House house=new House();house.setLatitude(new BigDecimal("10.0"));house.setLongitude(new BigDecimal("106.0"));
        org.mockito.Mockito.lenient().when(houses.findCustomerHouse(99L,100L)).thenReturn(house);
        org.mockito.Mockito.lenient().when(houses.ensureRouteLocation(99L,100L)).thenReturn(house);
        org.mockito.Mockito.lenient().when(reservations.assessTravel(org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.isNull())).thenReturn(new BookingReservationService.TravelAssessment(
                        TravelStatus.FEASIBLE,"",null,null,0,0,0,null));
    }

    @Test
    void returnsAdminSlotsAndNarrowsThemToSelectedPartTimeAvailability() {
        var all = service.options(date,List.of(1L),null,null,99L,100L);
        assertThat(all.startTimes()).contains("08:00","11:00").doesNotContain("11:15");
        assertThat(all.staffOptions()).extracting(person -> person.id()).containsExactly(10L,11L);
        assertThat(all.timeOptions().stream().filter(option -> option.startTime().equals("09:00"))
                .findFirst().orElseThrow().staffOptions()).extracting(BookingAvailabilityOptions.StaffOption::name)
                .containsExactly("Full time", "Part time");

        var partTime = service.options(date,List.of(1L),11L,null,99L,100L);
        assertThat(partTime.startTimes()).containsExactly("09:00","09:30","10:00");
    }

    @Test
    void explainsWhenSelectedPartTimeStaffHasNotRegisteredThatDate() {
        when(availability.activeAvailability(date)).thenReturn(List.of());

        var result = service.options(date,List.of(1L),11L,null,99L,100L);

        assertThat(result.startTimes()).isEmpty();
        assertThat(result.message()).contains("chưa đăng ký giờ làm");
    }

    @Test
    void availableDatesOnlyReturnsDatesWithAnEligibleRegisteredStaffSlot() {
        LocalDate from=LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh"));
        LocalDate through=from.plusDays(30);
        when(services.findActiveByIds(List.of(1L))).thenReturn(List.of(activeService(1L,60)));
        when(availability.activeStaff()).thenReturn(List.of(new BookingAvailabilityRepository.StaffOption(11L,"Part time","PART_TIME")));
        when(availability.activeAvailability(from,through)).thenReturn(List.of(
                new BookingAvailabilityRepository.DatedInterval(from.plusDays(1),11L,LocalTime.of(9,0),LocalTime.of(12,0))));
        when(availability.approvedLeaves(from,through)).thenReturn(List.of());
        when(availability.bookedIntervals(from,through)).thenReturn(List.of());
        when(companySettings.scheduleWindows()).thenReturn(java.util.stream.IntStream.rangeClosed(1,7)
                .mapToObj(day->new ScheduleWindow(LocalDate.of(1900,1,1),day,false,LocalTime.of(6,0),LocalTime.of(21,0))).toList());

        var result=service.availableDates(List.of(1L),null,99L,100L);

        assertThat(result.dates()).containsExactly(from.plusDays(1));
        service.options(date,List.of(1L),null,null,99L,100L); // Exercise the date-specific stubs from the shared fixture.
    }

    @Test
    void selectedTimeFiltersStaffAndBusyOrLeaveExcludesStaff() {
        when(availability.staffOnApprovedLeave(date)).thenReturn(List.of(10L));
        when(availability.bookedIntervals(date)).thenReturn(List.of(new Interval(11L,LocalTime.of(11,30),LocalTime.NOON)));
        var result = service.options(date,List.of(1L),11L,LocalTime.of(9,0),99L,100L);
        assertThat(result.staffOptions()).extracting(person -> person.id()).containsExactly(11L);
        assertThat(result.startTimes()).contains("09:00","09:30","10:00");
    }

    @Test
    void selectedTravelTimeHidesEarlierQuarterSlotsAndExplainsExactEarliestArrival() {
        LocalTime selected=LocalTime.of(9,0);
        LocalDateTime earliest=date.atTime(10,35);
        when(availability.activeAvailability(date)).thenReturn(List.of(new Interval(11L,LocalTime.of(9,0),LocalTime.of(12,0))));
        when(reservations.assessTravel(11L,date.atTime(selected),date.atTime(selected).plusMinutes(60),
                new BigDecimal("10.0"),new BigDecimal("106.0"),null)).thenReturn(new BookingReservationService.TravelAssessment(
                        TravelStatus.TOO_EARLY,
                        "Booking trước #44 kết thúc lúc 10:00. Tuyến mất 20 phút, cần 35 phút. Đủ từ 10:35; mốc đặt đầu là 10:45.",
                        earliest,null,20,0,35,44L));

        var result=service.options(date,List.of(1L),11L,selected,99L,100L);

        assertThat(result.startTimes()).containsExactly("11:00");
        assertThat(result.message()).contains("Booking trước #44","10:35","10:45");
    }

    @Test
    void selectedOverlapIsRemovedWithTheConflictingBookingIdentified() {
        when(reservations.assessTravel(11L,date.atTime(10,0),date.atTime(11,0),
                new BigDecimal("10.0"),new BigDecimal("106.0"),null)).thenReturn(new BookingReservationService.TravelAssessment(
                        TravelStatus.OVERLAP,"Giờ đã chọn bị trùng booking #55 (10:30–11:30).",
                        null,null,0,0,0,55L));

        var result=service.options(date,List.of(1L),11L,LocalTime.of(10,0),99L,100L);

        assertThat(result.startTimes()).doesNotContain("10:00");
        assertThat(result.message()).contains("booking #55","10:30–11:30");
    }

    @Test
    void availabilityRequiresHouseOwnedByTheSignedInCustomer() {
        when(houses.ensureRouteLocation(99L,100L)).thenThrow(new IllegalArgumentException("Nhà không thuộc tài khoản của bạn."));

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.options(date,List.of(1L),null,null,99L,100L))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("không thuộc tài khoản");
    }

    private Services activeService(Long id,int duration) {
        Services item = new Services();
        item.setServiceId(id);
        item.setServiceName("Cleaning");
        item.setDurationMinutes(duration);
        item.setPrice(new BigDecimal("100000"));
        item.setStatus(ServiceStatus.ACTIVE);
        return item;
    }
}
