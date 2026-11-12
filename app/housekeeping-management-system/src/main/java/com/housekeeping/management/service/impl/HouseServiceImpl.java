package com.housekeeping.management.service.impl;

import com.housekeeping.management.service.HouseService;
import com.housekeeping.management.integration.vietmap.VietmapClient;
import com.housekeeping.management.service.CompanySettingsService;
import com.housekeeping.management.service.BookingQueryService;
import com.housekeeping.management.dto.response.LocationSuggestion;

import com.housekeeping.management.dto.request.PortalForms.HouseForm;
import com.housekeeping.management.dto.entity.HouseDTO;
import com.housekeeping.management.domain.HouseAddressType;
import com.housekeeping.management.domain.SpecificAddressRules;
import com.housekeeping.management.entity.House;
import com.housekeeping.management.repository.HouseRepository;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class HouseServiceImpl implements HouseService {
    private static final double EARTH_RADIUS_KM = 6371.0088;
    private final HouseRepository houses;
    private final CompanySettingsService companySettings;
    private final BookingQueryService bookings;
    private final VietmapClient vietmap;

    public HouseServiceImpl(HouseRepository houses, CompanySettingsService companySettings,
            BookingQueryService bookings, VietmapClient vietmap) {
        this.houses = houses;
        this.companySettings = companySettings;
        this.bookings = bookings;
        this.vietmap = vietmap;
    }

    @Transactional(readOnly = true)
    public List<House> findCustomerHouses(Long customerId) {
        return houses.findAllByCustomerIdAndDeletedFalseOrderByDefaultAddressDescHouseNameAsc(customerId);
    }

    @Override
    @Transactional(readOnly = true)
    public java.util.Map<Long, Long> activeHouseCountsByCustomer() {
        java.util.Map<Long, Long> counts = new java.util.HashMap<>();
        for (Object[] row : houses.countActiveHousesByCustomer()) {
            counts.put(((Number) row[0]).longValue(), ((Number) row[1]).longValue());
        }
        return counts;
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
        String streetAddress = SpecificAddressRules.requireSpecific(form.streetAddress());
        House house = houseId == null ? null
                : houses.findLockedByHouseIdAndCustomerIdAndDeletedFalse(houseId, customerId)
                        .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy nhà thuộc tài khoản của bạn."));
        boolean hasNewVietmapPlace = form.vietmapRefId() != null && !form.vietmapRefId().isBlank();
        if (!hasNewVietmapPlace && house == null) throw new IllegalArgumentException("Hãy chọn một kết quả địa chỉ VietMap hợp lệ.");
        VietmapClient.Place place = hasNewVietmapPlace ? vietmap.place(form.vietmapRefId().trim()) : null;
        String address = place == null ? house.getAddress() : place.display();
        BigDecimal latitude = place == null ? house.getLatitude() : place.latitude();
        BigDecimal longitude = place == null ? house.getLongitude() : place.longitude();
        String province = place == null ? house.getProvinceCity() : (place.city() == null ? "" : place.city());
        String ward = place == null ? house.getWardCommune() : (place.ward() == null ? "" : place.ward());
        // address_detail giữ số nhà/tên đường do khách tự nhập; address giữ địa chỉ VietMap dùng để định vị.
        String detail = streetAddress;
        if (detail.length() + 2 + address.length() > 500) {
            throw new IllegalArgumentException("Địa chỉ quá dài. Hãy rút gọn phần số nhà, tên đường.");
        }
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

    @Transactional(readOnly = true)
    public boolean isWithinServiceRadius(BigDecimal latitude, BigDecimal longitude) {
        return serviceCenter().contains(latitude, longitude);
    }

    @Transactional(readOnly = true)
    public ServiceAreaAssessment assessCustomerHouseServiceArea(Long customerId, Long houseId) {
        House house = findCustomerHouse(customerId, houseId);
        if (house.getLatitude() == null || house.getLongitude() == null) {
            return new ServiceAreaAssessment(false, "Địa chỉ nhà chưa được xác minh tọa độ VietMap. Hãy cập nhật địa chỉ trước khi đặt.");
        }
        if (!isWithinServiceRadius(house.getLatitude(), house.getLongitude())) {
            return new ServiceAreaAssessment(false, "Địa chỉ nhà này nằm ngoài bán kính phục vụ của công ty. Hãy chọn địa chỉ khác hoặc liên hệ Admin.");
        }
        return new ServiceAreaAssessment(true, "Địa chỉ nằm trong bán kính phục vụ.");
    }

    @Transactional(readOnly = true)
    public ServiceCenter serviceCenter() {
        CompanySettingsService.ServiceCenter company = companySettings.serviceCenter();
        if (company.latitude()==null || company.longitude()==null || company.radiusKm()==null)
            throw new IllegalStateException("Admin chưa cấu hình tâm và bán kính phục vụ của công ty.");
        return new ServiceCenter(company.latitude(),company.longitude(),company.radiusKm());
    }

    static double distanceKm(double lat1, double lon1, double lat2, double lon2) {
        double latitudeDelta = Math.toRadians(lat2 - lat1);
        double longitudeDelta = Math.toRadians(lon2 - lon1);
        double startLatitude = Math.toRadians(lat1);
        double endLatitude = Math.toRadians(lat2);
        double haversine = Math.pow(Math.sin(latitudeDelta / 2), 2)
                + Math.cos(startLatitude) * Math.cos(endLatitude) * Math.pow(Math.sin(longitudeDelta / 2), 2);
        return 2 * EARTH_RADIUS_KM * Math.asin(Math.sqrt(Math.min(1, haversine)));
    }

}
