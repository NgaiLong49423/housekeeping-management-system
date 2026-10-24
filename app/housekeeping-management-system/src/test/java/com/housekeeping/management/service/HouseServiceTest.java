package com.housekeeping.management.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.housekeeping.management.entity.House;
import com.housekeeping.management.repository.HouseRepository;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class HouseServiceTest {
    private final HouseRepository houses = mock(HouseRepository.class);
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final HouseService service = new HouseService(houses, jdbc);

    @Test
    void haversineMeasuresDistanceBetweenCoordinatesInKilometers() {
        assertThat(HouseService.distanceKm(10.0, 106.0, 10.0, 106.0)).isZero();
        assertThat(HouseService.distanceKm(0.0, 0.0, 0.0, 1.0)).isBetween(111.0, 111.3);
    }

    @Test
    void softDeleteKeepsHouseWhenItHasAnActiveBooking() {
        House house = new House();
        when(houses.findLockedByHouseIdAndCustomerIdAndDeletedFalse(8L, 3L)).thenReturn(Optional.of(house));
        when(jdbc.queryForObject(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.eq(Integer.class),
                org.mockito.ArgumentMatchers.eq(8L))).thenReturn(1);

        assertThatThrownBy(() -> service.softDelete(3L, 8L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("đang có lịch");
        assertThat(house.isDeleted()).isFalse();
        verify(houses).findLockedByHouseIdAndCustomerIdAndDeletedFalse(8L, 3L);
    }
}
