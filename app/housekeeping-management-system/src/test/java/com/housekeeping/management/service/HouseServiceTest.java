package com.housekeeping.management.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.housekeeping.management.entity.House;
import com.housekeeping.management.domain.HouseAddressType;
import com.housekeeping.management.dto.request.PortalForms.HouseForm;
import com.housekeeping.management.integration.vietmap.VietmapClient;
import com.housekeeping.management.repository.HouseRepository;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class HouseServiceTest {
    private final HouseRepository houses = mock(HouseRepository.class);
    private final BookingQueryService bookings = mock(BookingQueryService.class);
    private final HouseService service = new com.housekeeping.management.service.impl.HouseServiceImpl(
            houses, bookings, mock(VietmapClient.class));

    @Test
    void updatingContactDetailsWithoutNewVietmapReferencePreservesAddressAndCoordinates() {
        House existing = new House();
        existing.setHouseId(8L);
        existing.setCustomerId(3L);
        existing.setHouseName("Nhà riêng");
        existing.setAddress("Địa chỉ cũ");
        existing.setLatitude(new BigDecimal("10.1"));
        existing.setLongitude(new BigDecimal("106.2"));
        existing.setProvinceCity("TP.HCM");
        existing.setWardCommune("Phường 1");
        existing.setAddressDetail("Số 1");
        when(houses.findLockedByHouseIdAndCustomerIdAndDeletedFalse(8L, 3L)).thenReturn(Optional.of(existing));

        service.save(3L, 8L, new HouseForm("Người nhận", "0900000000", "", HouseAddressType.HOME, false, null));

        assertThat(existing.getAddress()).isEqualTo("Địa chỉ cũ");
        assertThat(existing.getLatitude()).isEqualByComparingTo("10.1");
        assertThat(existing.getLongitude()).isEqualByComparingTo("106.2");
        assertThat(existing.getProvinceCity()).isEqualTo("TP.HCM");
        assertThat(existing.getWardCommune()).isEqualTo("Phường 1");
        assertThat(existing.getAddressDetail()).isEqualTo("Số 1");
        verify(houses).save(existing);
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
