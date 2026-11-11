package com.housekeeping.management.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.housekeeping.management.entity.House;
import com.housekeeping.management.integration.vietmap.VietmapClient;
import com.housekeeping.management.repository.HouseRepository;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class HouseServiceTest {
    private final HouseRepository houses = mock(HouseRepository.class);
    private final CompanySettingsService companySettings = mock(CompanySettingsService.class);
    private final BookingQueryService bookings = mock(BookingQueryService.class);
    private final HouseService service = new com.housekeeping.management.service.impl.HouseServiceImpl(
            houses, companySettings, bookings, mock(VietmapClient.class));

    @Test
    void haversineMeasuresDistanceBetweenCoordinatesInKilometers() {
        assertThat(HouseService.distanceKm(10.0, 106.0, 10.0, 106.0)).isZero();
        assertThat(HouseService.distanceKm(0.0, 0.0, 0.0, 1.0)).isBetween(111.0, 111.3);
    }

    @Test
    void acceptsHouseExactlyOnConfiguredServiceRadius() {
        double boundaryDistance = HouseService.distanceKm(0.0, 0.0, 0.0, 1.0);
        when(companySettings.serviceCenter()).thenReturn(new CompanySettingsService.ServiceCenter(
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.valueOf(boundaryDistance)));

        assertThat(service.isWithinServiceRadius(BigDecimal.ZERO, BigDecimal.ONE)).isTrue();
    }

    @Test
    void rejectsHouseJustOutsideConfiguredServiceRadius() {
        double boundaryDistance = HouseService.distanceKm(0.0, 0.0, 0.0, 1.0);
        when(companySettings.serviceCenter()).thenReturn(new CompanySettingsService.ServiceCenter(BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.valueOf(boundaryDistance).subtract(new BigDecimal("0.001"))));

        assertThat(service.isWithinServiceRadius(BigDecimal.ZERO, BigDecimal.ONE)).isFalse();
    }

    @Test
    void failsClosedWhenCompanyLocationIsMissingOrDuplicated() {
        when(companySettings.serviceCenter()).thenThrow(new IllegalStateException("Cần cấu hình chính xác một vị trí"));
        assertThatThrownBy(() -> service.isWithinServiceRadius(BigDecimal.ZERO, BigDecimal.ZERO))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("chính xác một");
    }

    @Test
    void softDeleteKeepsHouseWhenItHasAnActiveBooking() {
        House house = new House();
        when(houses.findLockedByHouseIdAndCustomerIdAndDeletedFalse(8L, 3L)).thenReturn(Optional.of(house));
        when(bookings.hasActiveBookingForHouse(8L)).thenReturn(true);

        assertThatThrownBy(() -> service.softDelete(3L, 8L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("đang có lịch");
        assertThat(house.isDeleted()).isFalse();
        verify(houses).findLockedByHouseIdAndCustomerIdAndDeletedFalse(8L, 3L);
    }

    @Test
    void softDeleteAllowsHouseWhenItHasNoActiveBookings() {
        House house = new House();
        when(houses.findLockedByHouseIdAndCustomerIdAndDeletedFalse(8L, 3L)).thenReturn(Optional.of(house));
        when(bookings.hasActiveBookingForHouse(8L)).thenReturn(false);

        service.softDelete(3L, 8L);

        assertThat(house.isDeleted()).isTrue();
        verify(houses).findLockedByHouseIdAndCustomerIdAndDeletedFalse(8L, 3L);
    }

}
