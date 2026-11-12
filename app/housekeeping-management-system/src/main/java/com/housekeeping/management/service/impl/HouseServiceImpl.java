package com.housekeeping.management.service.impl;

import com.housekeeping.management.service.HouseService;
import com.housekeeping.management.integration.vietmap.VietmapClient;
import com.housekeeping.management.service.BookingQueryService;
import com.housekeeping.management.dto.response.LocationSuggestion;

import com.housekeeping.management.dto.request.PortalForms.HouseForm;
import com.housekeeping.management.dto.entity.HouseDTO;
import com.housekeeping.management.domain.HouseAddressType;
import com.housekeeping.management.entity.House;
import com.housekeeping.management.repository.HouseRepository;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class HouseServiceImpl implements HouseService {
    private final HouseRepository houses;
    private final BookingQueryService bookings;
    private final VietmapClient vietmap;

    public HouseServiceImpl(HouseRepository houses, BookingQueryService bookings, VietmapClient vietmap) {
        this.houses = houses;
        this.bookings = bookings;
        this.vietmap = vietmap;
    }

    @Transactional(readOnly = true)
    public List<House> findCustomerHouses(Long customerId) {
        return houses.findAllByCustomerIdAndDeletedFalseOrderByDefaultAddressDescHouseNameAsc(customerId);
    }

    @Transactional(readOnly = true)
    public House findCustomerHouse(Long customerId, Long houseId) {
        return houses.findByHouseIdAndCustomerIdAndDeletedFalse(houseId, customerId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy nhà thuộc tài khoản của bạn."));
    }

    @Transactional
    public House findCustomerHouseForUpdate(Long customerId, Long houseId) {
        return houses.findLockedByHouseIdAndCustomerIdAndDeletedFalse(houseId, customerId)
                .orElseThrow(() -> new IllegalArgumentException("Nhà không tồn tại, đã ẩn hoặc không thuộc tài khoản của bạn."));
    }

    @Transactional(readOnly = true)
    public House findHouse(Long houseId) {
        return houses.findById(houseId).orElseThrow(() -> new IllegalArgumentException("Không tìm thấy địa chỉ Booking."));
    }

    @Transactional
    public House save(Long customerId, Long houseId, HouseForm form) {
        if (form == null) throw new IllegalArgumentException("Thông tin địa chỉ không hợp lệ.");
        House house = houseId == null ? null
                : houses.findLockedByHouseIdAndCustomerIdAndDeletedFalse(houseId, customerId)
                        .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy nhà thuộc tài khoản của bạn."));
        boolean hasNewVietmapPlace = form.vietmapRefId() != null && !form.vietmapRefId().isBlank();
        String requestedAddress = form.address() == null ? "" : form.address().trim();
        boolean addressChanged = !requestedAddress.isBlank() && (house == null || !requestedAddress.equals(house.getAddress()));
        if (!hasNewVietmapPlace && addressChanged && requestedAddress.length() < 3) {
            throw new IllegalArgumentException("Địa chỉ cần ít nhất 3 ký tự.");
        }
        if (!hasNewVietmapPlace && !addressChanged && house == null) {
            throw new IllegalArgumentException("Hãy nhập địa chỉ nhà để hệ thống xác định tuyến VietMap.");
        }
        VietmapClient.Place place = hasNewVietmapPlace ? vietmap.place(form.vietmapRefId().trim())
                : addressChanged ? vietmap.geocodeAddress(requestedAddress) : null;
        String address = place == null ? house.getAddress() : place.display();
        BigDecimal latitude = place == null ? house.getLatitude() : place.latitude();
        BigDecimal longitude = place == null ? house.getLongitude() : place.longitude();
        String province = place == null ? house.getProvinceCity() : (place.city() == null ? "" : place.city());
        String ward = place == null ? house.getWardCommune() : (place.ward() == null ? "" : place.ward());
        String detail = place == null ? house.getAddressDetail() : (place.address() == null ? place.display() : place.address());
        String addressNote = form.addressNote() == null || form.addressNote().isBlank() ? null : form.addressNote().trim();
        HouseDTO dto = new HouseDTO(house == null ? null : house.getHouseId(), customerId,
                form.addressType().getLabel(), address, latitude, longitude,
                form.recipientName().trim(), form.recipientPhone().trim(), province, ward, detail,
                form.addressType(), form.defaultAddress(), addressNote, house != null && house.isDeleted());
        house = dto.convertDTOToEntity(house);
        if (form.defaultAddress()) {
            houses.clearDefaultAddress(customerId, houseId);
        }
        return houses.save(house);
    }

    @Override
    @Transactional
    public House ensureRouteLocation(Long customerId, Long houseId) {
        House house = findCustomerHouseForUpdate(customerId, houseId);
        if (house.getLatitude() == null || house.getLongitude() == null) {
            VietmapClient.Place place = vietmap.geocodeAddress(house.getAddress());
            house.setLatitude(place.latitude());
            house.setLongitude(place.longitude());
            if (house.getAddress() == null || house.getAddress().isBlank()) house.setAddress(place.display());
            houses.save(house);
        }
        return house;
    }

    public List<LocationSuggestion> autocomplete(String query) {
        if (query == null || query.trim().length() < 3 || query.length() > 200) return List.of();
        return vietmap.autocomplete(query.trim()).stream()
                .map(suggestion -> new LocationSuggestion(suggestion.refId(), suggestion.display())).toList();
    }

    @Transactional
    public void softDelete(Long customerId, Long houseId) {
        House house = houses.findLockedByHouseIdAndCustomerIdAndDeletedFalse(houseId, customerId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy nhà thuộc tài khoản của bạn."));
        if (bookings.hasActiveBookingForHouse(houseId)) {
            throw new IllegalStateException("Không thể xóa nhà đang có lịch đã xác nhận hoặc chờ thanh toán.");
        }
        house.setDeleted(true);
    }

}
