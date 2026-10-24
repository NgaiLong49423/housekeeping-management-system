package com.housekeeping.management.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.housekeeping.management.domain.UserRole;
import com.housekeeping.management.domain.UserStatus;
import com.housekeeping.management.entity.House;
import com.housekeeping.management.entity.User;
import com.housekeeping.management.repository.BookingPersistenceRepository;
import com.housekeeping.management.repository.HouseRepository;
import com.housekeeping.management.repository.ServicesRepository;
import com.housekeeping.management.repository.StaffAccountRepository;
import com.housekeeping.management.repository.UserRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
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
}
