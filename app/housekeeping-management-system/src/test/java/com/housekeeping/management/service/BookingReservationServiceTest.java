package com.housekeeping.management.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.housekeeping.management.domain.StaffType;
import com.housekeeping.management.domain.UserRole;
import com.housekeeping.management.domain.UserStatus;
import com.housekeeping.management.entity.House;
import com.housekeeping.management.entity.Services;
import com.housekeeping.management.entity.Staff;
import com.housekeeping.management.entity.User;
import com.housekeeping.management.integration.vietmap.VietmapClient;
import com.housekeeping.management.repository.BookingPersistenceRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.List;
import org.junit.jupiter.api.Test;

class BookingReservationServiceTest {
    private final LocalDateTime now = LocalDateTime.parse("2026-11-01T08:00:00");
    private final BookingPersistenceRepository bookings = mock(BookingPersistenceRepository.class);
    private final HouseService houseService = mock(HouseService.class);
    private final AuthService auth = mock(AuthService.class);
    private final StaffManagementService staffManagement = mock(StaffManagementService.class);
    private final ServiceManagementService serviceManagement = mock(ServiceManagementService.class);
    private final VietmapClient vietmap = mock(VietmapClient.class);
    private final CompanySettingsService companySettings = mock(CompanySettingsService.class);
    private final com.housekeeping.management.repository.BookingDetailRepository bookingDetails =
            mock(com.housekeeping.management.repository.BookingDetailRepository.class);
    private final BookingReservationService service = new com.housekeeping.management.service.impl.BookingReservationServiceImpl(
            bookings, houseService, auth, staffManagement, serviceManagement, vietmap, companySettings, bookingDetails);

    @Test
    void rejectsDuplicateServiceIdsBeforeReadingServicesOrWritingBooking() {
        User customer = new User();
        customer.setUserId(12L);
        customer.setRole(UserRole.CUSTOMER);
        customer.setStatus(UserStatus.ACTIVE);
        when(auth.findActiveCustomerForUpdate(12L)).thenReturn(customer);
        when(houseService.findCustomerHouseForUpdate(12L, 31L)).thenReturn(new House());
        when(staffManagement.findActiveForBookingUpdate(7L)).thenReturn(new Staff());
        LocalDateTime start = LocalDate.now(ZoneOffset.ofHours(7)).plusDays(3).atTime(10, 0);

        assertThatThrownBy(() -> service.createBooking(12L, 7L, 31L, start, List.of(5L, 5L), null))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("dịch vụ");

        verify(serviceManagement, org.mockito.Mockito.never()).findByIds(org.mockito.ArgumentMatchers.anyList());
        verify(bookings, org.mockito.Mockito.never()).insertBooking(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void rejectsAreaNoteLongerThanSingleDayFormLimitAtServiceBoundary() {
        String tooLong = "x".repeat(2001);
        LocalDateTime futureStart = LocalDate.now(ZoneOffset.ofHours(7)).plusDays(5).atTime(10, 0);
        assertThatThrownBy(() -> service.createBooking(12L, 7L, 31L, futureStart, List.of(5L), tooLong))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("2.000");
        verifyNoInteractions(bookings, houseService, staffManagement, serviceManagement);
    }

    @Test
    void acceptsExactMinimumLeadTimeAndMaximumHorizon() {
        assertThatCode(() -> BookingReservationService.validateRequestedStart(now.plusMinutes(120), now))
                .doesNotThrowAnyException();
        assertThatCode(() -> BookingReservationService.validateRequestedStart(now.plusDays(30), now))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsShortLeadTimeOffSlotAndBeyondHorizon() {
        assertThatThrownBy(() -> BookingReservationService.validateRequestedStart(now.plusMinutes(119), now))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("120 phút");
        assertThatThrownBy(() -> BookingReservationService.validateRequestedStart(now.plusMinutes(136), now))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("00, 15, 30 hoặc 45");
        assertThatThrownBy(() -> BookingReservationService.validateRequestedStart(now.plusDays(30).plusMinutes(30), now))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("30 ngày");
    }

    @Test
    void acceptsOnlyQuarterHourStartMinutes() {
        assertThatCode(() -> BookingReservationService.validateRequestedStart(now.plusHours(2).withMinute(15), now)).doesNotThrowAnyException();
        assertThatThrownBy(() -> BookingReservationService.validateRequestedStart(now.plusHours(2).withMinute(1), now))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("00, 15, 30 hoặc 45");
    }

    @Test
    void rejectsBookingWhenCompanyIsClosedAtRequestedStart() {
        User customer = new User();
        customer.setRole(UserRole.CUSTOMER);
        customer.setStatus(UserStatus.ACTIVE);
        when(auth.findActiveCustomerForUpdate(12L)).thenReturn(customer);

        House house = new House();
        house.setAddressDetail("12 Nguyễn Trãi");
        house.setLatitude(new BigDecimal("10.0000000"));
        house.setLongitude(new BigDecimal("106.0000000"));
        when(houseService.findCustomerHouseForUpdate(12L, 31L)).thenReturn(house);

        Staff staff = new Staff();
        staff.setStaffId(7L);
        staff.setStaffType(StaffType.FULL_TIME);
        when(staffManagement.findActiveForBookingUpdate(7L)).thenReturn(staff);
        LocalDateTime closedStart = LocalDate.now(ZoneOffset.ofHours(7)).plusDays(1).atTime(20, 0);
        when(companySettings.acceptsInterval(org.mockito.ArgumentMatchers.eq(closedStart.toLocalDate()),
                org.mockito.ArgumentMatchers.eq(closedStart.toLocalTime()), org.mockito.ArgumentMatchers.any())).thenReturn(false);
        Services active = service(5L, "Dọn", "10.00", 60);
        when(serviceManagement.findByIds(java.util.List.of(5L))).thenReturn(java.util.List.of(active));

        assertThatThrownBy(() -> service.createBooking(12L, 7L, 31L, closedStart, java.util.List.of(5L), null))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("khung giờ hoạt động");

        verify(companySettings).acceptsInterval(org.mockito.ArgumentMatchers.eq(closedStart.toLocalDate()), org.mockito.ArgumentMatchers.eq(closedStart.toLocalTime()), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void rejectsInactiveServiceBeforeWritingBookingOrDetails() {
        User customer = new User();
        customer.setUserId(12L);
        customer.setRole(UserRole.CUSTOMER);
        customer.setStatus(UserStatus.ACTIVE);
        when(auth.findActiveCustomerForUpdate(12L)).thenReturn(customer);

        House house = new House();
        house.setAddressDetail("12 Nguyễn Trãi");
        house.setHouseId(31L);
        house.setAddress("12 Đường Mẫu, Quận 1");
        house.setLatitude(new BigDecimal("10.0000000"));
        house.setLongitude(new BigDecimal("106.0000000"));
        when(houseService.findCustomerHouseForUpdate(12L, 31L)).thenReturn(house);

        Staff staff = new Staff();
        staff.setStaffId(7L);
        staff.setStaffType(StaffType.FULL_TIME);
        when(staffManagement.findActiveForBookingUpdate(7L)).thenReturn(staff);

        LocalDateTime start = LocalDateTime.now(ZoneOffset.ofHours(7)).plusDays(3)
                .withHour(10).withMinute(0).withSecond(0).withNano(0);
        when(companySettings.acceptsInterval(start.toLocalDate(), start.toLocalTime(), start.plusMinutes(60).toLocalTime())).thenReturn(true);
        when(houseService.isWithinServiceRadius(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any())).thenReturn(true);
        when(staffManagement.isOnApprovedLeave(7L, start.toLocalDate())).thenReturn(false);
        Services inactive = service(5L, "Dọn phòng", "100.00", 60);
        inactive.setStatus(Services.Status.INACTIVE);
        when(serviceManagement.findByIds(java.util.List.of(5L))).thenReturn(java.util.List.of(inactive));

        assertThatThrownBy(() -> service.createBooking(12L, 7L, 31L, start, java.util.List.of(5L), null))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("ngừng hoạt động");

        verify(bookings, org.mockito.Mockito.never()).hasConflictingBooking(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyInt());
        verify(bookings, org.mockito.Mockito.never()).insertBooking(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
        verify(bookingDetails, org.mockito.Mockito.never()).saveAll(org.mockito.ArgumentMatchers.anyIterable());
    }

    @Test
    void sumsServiceSnapshotsOnlyWhenWorkEndsInsideCompanyWindow() {
        User customer = new User();
        customer.setUserId(12L);
        customer.setRole(UserRole.CUSTOMER);
        customer.setStatus(UserStatus.ACTIVE);
        when(auth.findActiveCustomerForUpdate(12L)).thenReturn(customer);

        House house = new House();
        house.setAddressDetail("12 Nguyễn Trãi");
        house.setHouseId(31L);
        house.setAddress("12 Đường Mẫu, Quận 1");
        house.setLatitude(new BigDecimal("10.0000000"));
        house.setLongitude(new BigDecimal("106.0000000"));
        when(houseService.findCustomerHouseForUpdate(12L, 31L)).thenReturn(house);

        Staff staff = new Staff();
        staff.setStaffId(7L);
        staff.setStaffType(StaffType.FULL_TIME);
        when(staffManagement.findActiveForBookingUpdate(7L)).thenReturn(staff);

        Services first = service(5L, "Dọn phòng", "100.00", 90);
        Services second = service(6L, "Lau bếp", "200.00", 90);
        LocalDateTime start = LocalDate.now(ZoneOffset.ofHours(7)).plusDays(1).atTime(17, 30);
        LocalTime companyClosingTime = LocalTime.of(18, 0);
        assertThat(start.toLocalTime()).isBefore(companyClosingTime);
        assertThat(start.plusMinutes(180).toLocalTime()).isAfter(companyClosingTime);
        when(serviceManagement.findByIds(java.util.List.of(5L, 6L))).thenReturn(java.util.List.of(first, second));
        when(companySettings.acceptsInterval(start.toLocalDate(), LocalTime.of(17, 30), LocalTime.of(20, 30))).thenReturn(false);
        when(staffManagement.isOnApprovedLeave(7L, start.toLocalDate())).thenReturn(false);
        when(bookings.hasConflictingBooking(org.mockito.ArgumentMatchers.eq(7L), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyInt())).thenReturn(false);
        when(bookings.insertBooking(org.mockito.ArgumentMatchers.eq(12L), org.mockito.ArgumentMatchers.eq(7L),
                org.mockito.ArgumentMatchers.eq(31L), org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(180),
                org.mockito.ArgumentMatchers.eq(new BigDecimal("300.00")), org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.any())).thenReturn(77L);

        assertThatThrownBy(() -> service.createBooking(12L, 7L, 31L, start, java.util.List.of(5L, 6L), null))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("kết thúc trong cùng");
        verify(staffManagement, org.mockito.Mockito.never())
                .acceptsAvailabilityInterval(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void partTimeStaffMustBeAvailableForTheEntireServiceDuration() {
        User customer = new User();
        customer.setUserId(12L);
        customer.setRole(UserRole.CUSTOMER);
        customer.setStatus(UserStatus.ACTIVE);
        when(auth.findActiveCustomerForUpdate(12L)).thenReturn(customer);

        House house = new House();
        house.setAddressDetail("12 Nguyễn Trãi");
        house.setHouseId(31L);
        house.setAddress("12 Đường Mẫu, Quận 1");
        house.setLatitude(new BigDecimal("10.0000000"));
        house.setLongitude(new BigDecimal("106.0000000"));
        when(houseService.findCustomerHouseForUpdate(12L, 31L)).thenReturn(house);

        Staff staff = new Staff();
        staff.setStaffId(7L);
        staff.setStaffType(StaffType.PART_TIME);
        when(staffManagement.findActiveForBookingUpdate(7L)).thenReturn(staff);

        LocalDateTime start = LocalDate.now(ZoneOffset.ofHours(7)).plusDays(1).atTime(17, 30);
        LocalTime availabilityEnd = LocalTime.of(18, 0);
        assertThat(start.toLocalTime()).isBefore(availabilityEnd);
        assertThat(start.plusMinutes(180).toLocalTime()).isAfter(availabilityEnd);
        when(companySettings.acceptsInterval(start.toLocalDate(), start.toLocalTime(), start.plusMinutes(180).toLocalTime())).thenReturn(true);
        when(houseService.isWithinServiceRadius(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any())).thenReturn(true);
        when(staffManagement.isOnApprovedLeave(7L, start.toLocalDate())).thenReturn(false);
        when(staffManagement.acceptsAvailabilityInterval(7L, start.toLocalDate(), start.toLocalTime(), start.plusMinutes(180).toLocalTime())).thenReturn(false);
        Services cleaning = service(5L, "Dọn phòng", "100.00", 180);
        when(serviceManagement.findByIds(java.util.List.of(5L))).thenReturn(java.util.List.of(cleaning));

        assertThatThrownBy(() -> service.createBooking(12L, 7L, 31L, start, java.util.List.of(5L), null))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("khoảng sẵn sàng đang hiệu lực");

        when(staffManagement.acceptsAvailabilityInterval(7L, start.toLocalDate(), start.toLocalTime(), start.plusMinutes(180).toLocalTime())).thenReturn(true);
        house.setAddressDetail("Phường Bến Thành");
        assertThatThrownBy(() -> service.createBooking(12L, 7L, 31L, start, java.util.List.of(5L), null))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("số nhà");
        house.setAddressDetail("12 Nguyễn Trãi");
        when(bookings.adjacentAppointments(7L, start, start.plusMinutes(180), null)).thenReturn(java.util.List.of());
        when(houseService.serviceCenter()).thenReturn(new HouseService.ServiceCenter(new BigDecimal("10.0"),new BigDecimal("106.0"),new BigDecimal("25")));
        when(vietmap.motorcycleMinutesBeforeArrival(new BigDecimal("10.0"),new BigDecimal("106.0"),
                house.getLatitude(),house.getLongitude(),start)).thenReturn(1);
        when(bookings.insertBooking(org.mockito.ArgumentMatchers.eq(12L), org.mockito.ArgumentMatchers.eq(7L),
                org.mockito.ArgumentMatchers.eq(31L), org.mockito.ArgumentMatchers.eq("12 Nguyễn Trãi, 12 Đường Mẫu, Quận 1"),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.eq(start), org.mockito.ArgumentMatchers.eq(start.plusMinutes(180)),
                org.mockito.ArgumentMatchers.eq(180), org.mockito.ArgumentMatchers.eq(new BigDecimal("100.00")),
                org.mockito.ArgumentMatchers.isNull(), org.mockito.ArgumentMatchers.any())).thenReturn(78L);

        assertThat(service.createBooking(12L, 7L, 31L, start, java.util.List.of(5L), null)).isEqualTo(78L);
        verify(vietmap).motorcycleMinutesBeforeArrival(new BigDecimal("10.0"),new BigDecimal("106.0"),
                house.getLatitude(),house.getLongitude(),start);
    }

    @Test
    void travelGapUsesRouteEtaPlusExactlyFifteenMinutes() {
        LocalDateTime start=LocalDateTime.of(2026,11,10,10,35);
        LocalDateTime previousEnd=start.minusMinutes(35);
        var previous=new BookingPersistenceRepository.AppointmentPoint(44L,previousEnd.minusHours(1),previousEnd,
                new BigDecimal("10.0"),new BigDecimal("106.0"));
        when(bookings.adjacentAppointments(7L,start,start.plusHours(1),null)).thenReturn(List.of(previous));
        when(houseService.serviceCenter()).thenReturn(new HouseService.ServiceCenter(new BigDecimal("10.0"),new BigDecimal("106.0"),new BigDecimal("25")));
        when(vietmap.motorcycleRoute(previous.latitude(),previous.longitude(),new BigDecimal("10.1"),
                new BigDecimal("106.1"),previousEnd)).thenReturn(new VietmapClient.MotorcycleRoute(new BigDecimal("25000"),20));

        assertThat(service.hasInsufficientTravelGap(7L,start,start.plusHours(1),new BigDecimal("10.1"),new BigDecimal("106.1"),null)).isFalse();

        LocalDateTime tooEarly=start.minusMinutes(1);
        when(bookings.adjacentAppointments(7L,tooEarly,tooEarly.plusHours(1),null)).thenReturn(List.of(previous));
        when(vietmap.motorcycleRoute(previous.latitude(),previous.longitude(),new BigDecimal("10.1"),
                new BigDecimal("106.1"),previousEnd)).thenReturn(new VietmapClient.MotorcycleRoute(new BigDecimal("25000"),20));
        assertThat(service.hasInsufficientTravelGap(7L,tooEarly,tooEarly.plusHours(1),new BigDecimal("10.1"),new BigDecimal("106.1"),null)).isTrue();
    }

    @Test
    void sameLocationSkipsRouteLookupButKeepsFifteenMinuteRestBuffer() {
        LocalDateTime previousEnd=LocalDateTime.of(2026,11,10,10,0);
        var previous=new BookingPersistenceRepository.AppointmentPoint(44L,previousEnd.minusHours(2),previousEnd,
                new BigDecimal("10.0"),new BigDecimal("106.000"));
        LocalDateTime exactEndStart=previousEnd;
        when(bookings.adjacentAppointments(7L,exactEndStart,exactEndStart.plusHours(2),null)).thenReturn(List.of(previous));

        var tooEarly=service.assessTravel(7L,exactEndStart,exactEndStart.plusHours(2),
                new BigDecimal("10.000"),new BigDecimal("106.0"),null);

        assertThat(tooEarly.status()).isEqualTo(BookingReservationService.TravelStatus.TOO_EARLY);
        assertThat(tooEarly.earliestStart()).isEqualTo(previousEnd.plusMinutes(15));
        assertThat(tooEarly.routeMinutes()).isZero();
        assertThat(tooEarly.requiredGapMinutes()).isEqualTo(15);
        assertThat(tooEarly.message()).contains("Booking trước kết thúc lúc 10:00", "nghỉ 15 phút", "10:15")
                .doesNotContain("#");

        LocalDateTime restCompleteStart=previousEnd.plusMinutes(15);
        when(bookings.adjacentAppointments(7L,restCompleteStart,restCompleteStart.plusHours(2),null)).thenReturn(List.of(previous));
        var feasible=service.assessTravel(7L,restCompleteStart,restCompleteStart.plusHours(2),
                new BigDecimal("10.000"),new BigDecimal("106.0"),null);

        assertThat(feasible.status()).isEqualTo(BookingReservationService.TravelStatus.FEASIBLE);
        org.mockito.Mockito.verifyNoInteractions(vietmap);
    }

    @Test
    void acceptsRouteDistanceAtLimitAndRejectsDistanceAboveLimitFromPreviousBooking() {
        LocalDateTime start=LocalDateTime.of(2026,11,10,10,0);
        LocalDateTime end=start.plusHours(1);
        LocalDateTime previousEnd=start.minusMinutes(35);
        var previous=new BookingPersistenceRepository.AppointmentPoint(44L,previousEnd.minusHours(1),previousEnd,
                new BigDecimal("10.0"),new BigDecimal("106.0"));
        when(bookings.adjacentAppointments(7L,start,end,null)).thenReturn(List.of(previous));
        when(houseService.serviceCenter()).thenReturn(new HouseService.ServiceCenter(new BigDecimal("10.0"),new BigDecimal("106.0"),new BigDecimal("25.00")));
        when(vietmap.motorcycleRoute(previous.latitude(),previous.longitude(),new BigDecimal("10.1"),
                new BigDecimal("106.1"),previousEnd)).thenReturn(new VietmapClient.MotorcycleRoute(new BigDecimal("25000"),20));

        var atLimit=service.assessTravel(7L,start,end,new BigDecimal("10.1"),new BigDecimal("106.1"),null);

        assertThat(atLimit.status()).isEqualTo(BookingReservationService.TravelStatus.FEASIBLE);
        when(vietmap.motorcycleRoute(previous.latitude(),previous.longitude(),new BigDecimal("10.1"),
                new BigDecimal("106.1"),previousEnd)).thenReturn(new VietmapClient.MotorcycleRoute(new BigDecimal("25001"),20));

        var overLimit=service.assessTravel(7L,start,end,new BigDecimal("10.1"),new BigDecimal("106.1"),null);

        assertThat(overLimit.status()).isEqualTo(BookingReservationService.TravelStatus.DISTANCE_LIMIT_EXCEEDED);
        assertThat(overLimit.message()).contains("booking trước", "25.01 km", "25.00 km").doesNotContain("#");
    }

    @Test
    void rejectsRouteDistanceAboveLimitToTheNextBooking() {
        LocalDateTime start=LocalDateTime.of(2026,11,10,10,0);
        LocalDateTime end=start.plusMinutes(30);
        var previous=new BookingPersistenceRepository.AppointmentPoint(43L,start.minusHours(2),start.minusHours(1),
                new BigDecimal("10.0"),new BigDecimal("106.0"));
        var next=new BookingPersistenceRepository.AppointmentPoint(45L,end.plusHours(1),end.plusHours(2),
                new BigDecimal("10.2"),new BigDecimal("106.2"));
        when(bookings.adjacentAppointments(7L,start,end,null)).thenReturn(List.of(previous,next));
        when(houseService.serviceCenter()).thenReturn(new HouseService.ServiceCenter(new BigDecimal("10.0"),new BigDecimal("106.0"),new BigDecimal("25.00")));
        when(vietmap.motorcycleRoute(previous.latitude(),previous.longitude(),new BigDecimal("10.1"),
                new BigDecimal("106.1"),previous.end())).thenReturn(new VietmapClient.MotorcycleRoute(new BigDecimal("1000"),10));
        when(vietmap.motorcycleRoute(new BigDecimal("10.1"),new BigDecimal("106.1"),next.latitude(),next.longitude(),end))
                .thenReturn(new VietmapClient.MotorcycleRoute(new BigDecimal("26000"),20));

        var result=service.assessTravel(7L,start,end,new BigDecimal("10.1"),new BigDecimal("106.1"),null);

        assertThat(result.status()).isEqualTo(BookingReservationService.TravelStatus.DISTANCE_LIMIT_EXCEEDED);
        assertThat(result.message()).contains("booking kế tiếp", "26.00 km", "25.00 km").doesNotContain("#");
    }

    @Test
    void reportsLatestStartWhenThereIsNotEnoughTimeToReachTheNextBooking() {
        LocalDateTime start=LocalDateTime.of(2026,11,10,12,30);
        LocalDateTime end=start.plusHours(1);
        var next=new BookingPersistenceRepository.AppointmentPoint(45L,end.plusMinutes(30),end.plusMinutes(150),
                new BigDecimal("10.2"),new BigDecimal("106.2"));
        when(bookings.adjacentAppointments(7L,start,end,null)).thenReturn(List.of(next));
        when(vietmap.motorcycleRoute(new BigDecimal("10.1"),new BigDecimal("106.1"),next.latitude(),next.longitude(),end))
                .thenReturn(new VietmapClient.MotorcycleRoute(new BigDecimal("20000"),20));
        when(houseService.serviceCenter()).thenReturn(new HouseService.ServiceCenter(new BigDecimal("10.0"),new BigDecimal("106.0"),new BigDecimal("25")));
        when(vietmap.motorcycleMinutesBeforeArrival(new BigDecimal("10.0"),new BigDecimal("106.0"),
                new BigDecimal("10.1"),new BigDecimal("106.1"),start)).thenReturn(1);

        var result=service.assessTravel(7L,start,end,new BigDecimal("10.1"),new BigDecimal("106.1"),null);

        assertThat(result.status()).isEqualTo(BookingReservationService.TravelStatus.TOO_LATE);
        assertThat(result.latestStart()).isEqualTo(LocalDateTime.of(2026,11,10,12,25));
        assertThat(result.message()).contains("Booking kế tiếp","20 phút","15 phút","30 phút","12:25","12:15")
                .doesNotContain("#");
    }

    @Test
    void reportsRouteUnavailableSeparatelyFromInsufficientTravelTime() {
        LocalDateTime start=LocalDateTime.of(2026,11,10,10,0);
        LocalDateTime end=start.plusHours(1);
        var next=new BookingPersistenceRepository.AppointmentPoint(46L,end.plusHours(2),end.plusHours(3),
                new BigDecimal("10.2"),new BigDecimal("106.2"));
        when(bookings.adjacentAppointments(7L,start,end,null)).thenReturn(List.of(next));
        when(houseService.serviceCenter()).thenReturn(new HouseService.ServiceCenter(new BigDecimal("10.0"),new BigDecimal("106.0"),new BigDecimal("25")));
        when(vietmap.motorcycleRoute(new BigDecimal("10.1"),new BigDecimal("106.1"),next.latitude(),next.longitude(),end))
                .thenThrow(new IllegalStateException("Không tìm thấy tuyến đường khả dụng."));

        var result=service.assessTravel(7L,start,end,new BigDecimal("10.1"),new BigDecimal("106.1"),null);

        assertThat(result.status()).isEqualTo(BookingReservationService.TravelStatus.ROUTE_UNAVAILABLE);
        assertThat(result.message()).contains("Chưa xác minh được tuyến","booking kế tiếp").doesNotContain("#");
    }

    private Services service(Long id, String name, String price, int durationMinutes) {
        Services service = new Services();
        service.setServiceId(id);
        service.setServiceName(name);
        service.setPrice(new BigDecimal(price));
        service.setDurationMinutes(durationMinutes);
        service.setStatus(Services.Status.ACTIVE);
        return service;
    }
}
