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
import com.housekeeping.management.repository.BookingPersistenceRepository;
import com.housekeeping.management.repository.HouseRepository;
import com.housekeeping.management.repository.ServicesRepository;
import com.housekeeping.management.repository.StaffAccountRepository;
import com.housekeeping.management.repository.UserRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class BookingReservationServiceTest {
    private final LocalDateTime now = LocalDateTime.parse("2026-11-01T08:00:00");
    private final UserRepository users = mock(UserRepository.class);
    private final StaffAccountRepository staffAccounts = mock(StaffAccountRepository.class);
    private final HouseRepository houses = mock(HouseRepository.class);
    private final ServicesRepository services = mock(ServicesRepository.class);
    private final HouseService houseService = mock(HouseService.class);
    private final BookingPersistenceRepository bookings = mock(BookingPersistenceRepository.class);
    private final BookingReservationService service = new BookingReservationService(users, staffAccounts, houses,
            services, houseService, bookings);

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
        assertThatThrownBy(() -> BookingReservationService.validateRequestedStart(now.plusMinutes(135), now))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("30 phút");
        assertThatThrownBy(() -> BookingReservationService.validateRequestedStart(now.plusDays(30).plusMinutes(30), now))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("30 ngày");
    }

    @Test
    void rejectsBookingOutsideCompanyRadiusBeforeLoadingStaffOrServices() {
        User customer = new User();
        customer.setRole(UserRole.CUSTOMER);
        customer.setStatus(UserStatus.ACTIVE);
        when(users.findById(12L)).thenReturn(Optional.of(customer));

        House house = new House();
        house.setLatitude(new BigDecimal("10.0000000"));
        house.setLongitude(new BigDecimal("106.0000000"));
        when(houses.findLockedByHouseIdAndCustomerIdAndDeletedFalse(31L, 12L)).thenReturn(Optional.of(house));
        when(houseService.isWithinServiceRadius(house.getLatitude(), house.getLongitude())).thenReturn(false);
        LocalDateTime futureSlot = LocalDateTime.now(ZoneOffset.ofHours(7)).plusHours(4)
                .withMinute(0).withSecond(0).withNano(0);

        assertThatThrownBy(() -> service.createBooking(12L, 7L, 31L, futureSlot, java.util.List.of(5L), null))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("ngoài bán kính");

        verify(houseService).isWithinServiceRadius(house.getLatitude(), house.getLongitude());
        verifyNoInteractions(staffAccounts, services, bookings);
    }

    @Test
    void rejectsBookingWhenCompanyIsClosedAtRequestedStart() {
        User customer = new User();
        customer.setRole(UserRole.CUSTOMER);
        customer.setStatus(UserStatus.ACTIVE);
        when(users.findById(12L)).thenReturn(Optional.of(customer));

        House house = new House();
        house.setLatitude(new BigDecimal("10.0000000"));
        house.setLongitude(new BigDecimal("106.0000000"));
        when(houses.findLockedByHouseIdAndCustomerIdAndDeletedFalse(31L, 12L)).thenReturn(Optional.of(house));
        when(houseService.isWithinServiceRadius(house.getLatitude(), house.getLongitude())).thenReturn(true);

        Staff staff = new Staff();
        staff.setStaffId(7L);
        staff.setStaffType(StaffType.FULL_TIME);
        when(staffAccounts.findActiveByStaffIdForUpdate(7L)).thenReturn(Optional.of(staff));
        LocalDateTime closedStart = LocalDate.now(ZoneOffset.ofHours(7)).plusDays(1).atTime(20, 0);
        when(bookings.companyAcceptsStart(closedStart.toLocalDate(), closedStart.toLocalTime())).thenReturn(false);

        assertThatThrownBy(() -> service.createBooking(12L, 7L, 31L, closedStart, java.util.List.of(5L), null))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("ngoài giờ hoạt động");

        verify(bookings).companyAcceptsStart(closedStart.toLocalDate(), closedStart.toLocalTime());
        verifyNoInteractions(services);
    }

    @Test
    void rejectsInactiveServiceBeforeWritingBookingOrDetails() {
        User customer = new User();
        customer.setUserId(12L);
        customer.setRole(UserRole.CUSTOMER);
        customer.setStatus(UserStatus.ACTIVE);
        when(users.findById(12L)).thenReturn(Optional.of(customer));

        House house = new House();
        house.setHouseId(31L);
        house.setAddress("12 Đường Mẫu, Quận 1");
        house.setLatitude(new BigDecimal("10.0000000"));
        house.setLongitude(new BigDecimal("106.0000000"));
        when(houses.findLockedByHouseIdAndCustomerIdAndDeletedFalse(31L, 12L)).thenReturn(Optional.of(house));
        when(houseService.isWithinServiceRadius(house.getLatitude(), house.getLongitude())).thenReturn(true);

        Staff staff = new Staff();
        staff.setStaffId(7L);
        staff.setStaffType(StaffType.FULL_TIME);
        when(staffAccounts.findActiveByStaffIdForUpdate(7L)).thenReturn(Optional.of(staff));

        LocalDateTime start = LocalDateTime.now(ZoneOffset.ofHours(7)).plusDays(3)
                .withHour(10).withMinute(0).withSecond(0).withNano(0);
        when(bookings.companyAcceptsStart(start.toLocalDate(), start.toLocalTime())).thenReturn(true);
        when(bookings.isOnApprovedLeave(7L, start.toLocalDate())).thenReturn(false);
        Services inactive = service(5L, "Dọn phòng", "100.00", 60);
        inactive.setStatus(Services.Status.INACTIVE);
        when(services.findAllById(java.util.List.of(5L))).thenReturn(java.util.List.of(inactive));

        assertThatThrownBy(() -> service.createBooking(12L, 7L, 31L, start, java.util.List.of(5L), null))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("ngừng hoạt động");

        verify(bookings, org.mockito.Mockito.never()).hasConflictingBooking(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
        verify(bookings, org.mockito.Mockito.never()).insertBooking(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
        verify(bookings, org.mockito.Mockito.never()).insertDetails(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyList());
    }

    @Test
    void sumsServiceSnapshotsAndAllowsWorkToEndAfterCompanyClosingTime() {
        User customer = new User();
        customer.setUserId(12L);
        customer.setRole(UserRole.CUSTOMER);
        customer.setStatus(UserStatus.ACTIVE);
        when(users.findById(12L)).thenReturn(Optional.of(customer));

        House house = new House();
        house.setHouseId(31L);
        house.setAddress("12 Đường Mẫu, Quận 1");
        house.setLatitude(new BigDecimal("10.0000000"));
        house.setLongitude(new BigDecimal("106.0000000"));
        when(houses.findLockedByHouseIdAndCustomerIdAndDeletedFalse(31L, 12L)).thenReturn(Optional.of(house));
        when(houseService.isWithinServiceRadius(house.getLatitude(), house.getLongitude())).thenReturn(true);

        Staff staff = new Staff();
        staff.setStaffId(7L);
        staff.setStaffType(StaffType.FULL_TIME);
        when(staffAccounts.findActiveByStaffIdForUpdate(7L)).thenReturn(Optional.of(staff));

        Services first = service(5L, "Dọn phòng", "100.00", 90);
        Services second = service(6L, "Lau bếp", "200.00", 90);
        LocalDateTime start = LocalDate.now(ZoneOffset.ofHours(7)).plusDays(1).atTime(17, 30);
        LocalTime companyClosingTime = LocalTime.of(18, 0);
        assertThat(start.toLocalTime()).isBefore(companyClosingTime);
        assertThat(start.plusMinutes(180).toLocalTime()).isAfter(companyClosingTime);
        when(services.findAllById(java.util.List.of(5L, 6L))).thenReturn(java.util.List.of(first, second));
        when(bookings.companyAcceptsStart(org.mockito.ArgumentMatchers.eq(start.toLocalDate()),
                org.mockito.ArgumentMatchers.eq(LocalTime.of(17, 30)))).thenReturn(true);
        when(bookings.isOnApprovedLeave(7L, start.toLocalDate())).thenReturn(false);
        when(bookings.hasConflictingBooking(org.mockito.ArgumentMatchers.eq(7L), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any())).thenReturn(false);
        when(bookings.insertBooking(org.mockito.ArgumentMatchers.eq(12L), org.mockito.ArgumentMatchers.eq(7L),
                org.mockito.ArgumentMatchers.eq(31L), org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(180),
                org.mockito.ArgumentMatchers.eq(new BigDecimal("300.00")), org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.any())).thenReturn(77L);

        assertThat(service.createBooking(12L, 7L, 31L, start, java.util.List.of(5L, 6L), null)).isEqualTo(77L);

        verify(bookings).insertBooking(org.mockito.ArgumentMatchers.eq(12L), org.mockito.ArgumentMatchers.eq(7L),
                org.mockito.ArgumentMatchers.eq(31L), org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.eq(house.getLatitude()), org.mockito.ArgumentMatchers.eq(house.getLongitude()),
                org.mockito.ArgumentMatchers.isNull(), org.mockito.ArgumentMatchers.eq(start),
                org.mockito.ArgumentMatchers.eq(start.plusMinutes(180)), org.mockito.ArgumentMatchers.eq(180),
                org.mockito.ArgumentMatchers.eq(new BigDecimal("300.00")), org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.any());
        verify(bookings).insertDetails(77L, java.util.List.of(
                new BookingPersistenceRepository.ServiceSnapshot(5L, "Dọn phòng", new BigDecimal("100.00"), 90),
                new BookingPersistenceRepository.ServiceSnapshot(6L, "Lau bếp", new BigDecimal("200.00"), 90)));
        verify(bookings, org.mockito.Mockito.never())
                .hasPartTimeAvailability(7L, start.toLocalDate(), start.toLocalTime());
    }

    @Test
    void partTimeStaffNeedsAvailabilityAtStartButNotForTheEntireServiceDuration() {
        User customer = new User();
        customer.setUserId(12L);
        customer.setRole(UserRole.CUSTOMER);
        customer.setStatus(UserStatus.ACTIVE);
        when(users.findById(12L)).thenReturn(Optional.of(customer));

        House house = new House();
        house.setHouseId(31L);
        house.setAddress("12 Đường Mẫu, Quận 1");
        house.setLatitude(new BigDecimal("10.0000000"));
        house.setLongitude(new BigDecimal("106.0000000"));
        when(houses.findLockedByHouseIdAndCustomerIdAndDeletedFalse(31L, 12L)).thenReturn(Optional.of(house));
        when(houseService.isWithinServiceRadius(house.getLatitude(), house.getLongitude())).thenReturn(true);

        Staff staff = new Staff();
        staff.setStaffId(7L);
        staff.setStaffType(StaffType.PART_TIME);
        when(staffAccounts.findActiveByStaffIdForUpdate(7L)).thenReturn(Optional.of(staff));

        LocalDateTime start = LocalDate.now(ZoneOffset.ofHours(7)).plusDays(1).atTime(17, 30);
        LocalTime availabilityEnd = LocalTime.of(18, 0);
        assertThat(start.toLocalTime()).isBefore(availabilityEnd);
        assertThat(start.plusMinutes(180).toLocalTime()).isAfter(availabilityEnd);
        when(bookings.companyAcceptsStart(start.toLocalDate(), start.toLocalTime())).thenReturn(true);
        when(bookings.isOnApprovedLeave(7L, start.toLocalDate())).thenReturn(false);
        when(bookings.hasPartTimeAvailability(7L, start.toLocalDate(), start.toLocalTime())).thenReturn(false);

        assertThatThrownBy(() -> service.createBooking(12L, 7L, 31L, start, java.util.List.of(5L), null))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("chưa đăng ký khoảng sẵn sàng");

        when(bookings.hasPartTimeAvailability(7L, start.toLocalDate(), start.toLocalTime())).thenReturn(true);
        Services cleaning = service(5L, "Dọn phòng", "100.00", 180);
        when(services.findAllById(java.util.List.of(5L))).thenReturn(java.util.List.of(cleaning));
        when(bookings.hasConflictingBooking(7L, start, start.plusMinutes(180))).thenReturn(false);
        when(bookings.insertBooking(org.mockito.ArgumentMatchers.eq(12L), org.mockito.ArgumentMatchers.eq(7L),
                org.mockito.ArgumentMatchers.eq(31L), org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.eq(start), org.mockito.ArgumentMatchers.eq(start.plusMinutes(180)),
                org.mockito.ArgumentMatchers.eq(180), org.mockito.ArgumentMatchers.eq(new BigDecimal("100.00")),
                org.mockito.ArgumentMatchers.isNull(), org.mockito.ArgumentMatchers.any())).thenReturn(78L);

        assertThat(service.createBooking(12L, 7L, 31L, start, java.util.List.of(5L), null)).isEqualTo(78L);
        verify(bookings, org.mockito.Mockito.times(2))
                .hasPartTimeAvailability(7L, start.toLocalDate(), start.toLocalTime());
        verify(bookings).insertBooking(org.mockito.ArgumentMatchers.eq(12L), org.mockito.ArgumentMatchers.eq(7L),
                org.mockito.ArgumentMatchers.eq(31L), org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.eq(start), org.mockito.ArgumentMatchers.eq(start.plusMinutes(180)),
                org.mockito.ArgumentMatchers.eq(180), org.mockito.ArgumentMatchers.eq(new BigDecimal("100.00")),
                org.mockito.ArgumentMatchers.isNull(), org.mockito.ArgumentMatchers.any());
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
