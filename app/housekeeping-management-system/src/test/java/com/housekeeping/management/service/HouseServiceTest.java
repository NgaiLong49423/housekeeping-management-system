package com.housekeeping.management.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.housekeeping.management.entity.House;
import com.housekeeping.management.repository.HouseRepository;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.RowMapper;
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
    void acceptsHouseExactlyOnConfiguredServiceRadius() throws Exception {
        double boundaryDistance = HouseService.distanceKm(0.0, 0.0, 0.0, 1.0);
        configureCompanyLocation(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.valueOf(boundaryDistance));

        assertThat(service.isWithinServiceRadius(BigDecimal.ZERO, BigDecimal.ONE)).isTrue();
    }

    @Test
    void rejectsHouseJustOutsideConfiguredServiceRadius() throws Exception {
        double boundaryDistance = HouseService.distanceKm(0.0, 0.0, 0.0, 1.0);
        configureCompanyLocation(BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.valueOf(boundaryDistance).subtract(new BigDecimal("0.001")));

        assertThat(service.isWithinServiceRadius(BigDecimal.ZERO, BigDecimal.ONE)).isFalse();
    }

    @Test
    void failsClosedWhenCompanyLocationIsMissingOrDuplicated() {
        when(jdbc.query(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any(RowMapper.class)))
                .thenReturn(List.of());
        assertThatThrownBy(() -> service.isWithinServiceRadius(BigDecimal.ZERO, BigDecimal.ZERO))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("chính xác một");

        when(jdbc.query(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any(RowMapper.class)))
                .thenReturn(List.of(new Object(), new Object()));
        assertThatThrownBy(() -> service.isWithinServiceRadius(BigDecimal.ZERO, BigDecimal.ZERO))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("chính xác một");
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

    @Test
    void softDeleteAllowsHouseWhenItHasNoActiveBookings() {
        House house = new House();
        when(houses.findLockedByHouseIdAndCustomerIdAndDeletedFalse(8L, 3L)).thenReturn(Optional.of(house));
        when(jdbc.queryForObject(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.eq(Integer.class),
                org.mockito.ArgumentMatchers.eq(8L))).thenReturn(0);

        service.softDelete(3L, 8L);

        assertThat(house.isDeleted()).isTrue();
        verify(houses).findLockedByHouseIdAndCustomerIdAndDeletedFalse(8L, 3L);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void configureCompanyLocation(BigDecimal latitude, BigDecimal longitude, BigDecimal radius) throws Exception {
        when(jdbc.query(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any(RowMapper.class)))
                .thenAnswer(invocation -> {
                    RowMapper mapper = invocation.getArgument(1);
                    ResultSet resultSet = mock(ResultSet.class);
                    when(resultSet.getBigDecimal("latitude")).thenReturn(latitude);
                    when(resultSet.getBigDecimal("longitude")).thenReturn(longitude);
                    when(resultSet.getBigDecimal("service_radius_km")).thenReturn(radius);
                    return List.of(mapper.mapRow(resultSet, 0));
                });
    }
}
