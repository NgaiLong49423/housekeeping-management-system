package com.housekeeping.management.service;

import com.housekeeping.management.dto.request.PortalForms.HouseForm;
import com.housekeeping.management.domain.HouseAddressType;
import com.housekeeping.management.entity.House;
import com.housekeeping.management.repository.HouseRepository;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class HouseService {
    private static final double EARTH_RADIUS_KM = 6371.0088;
    private final HouseRepository houses;
    private final JdbcTemplate jdbc;

    public HouseService(HouseRepository houses, JdbcTemplate jdbc) {
        this.houses = houses;
        this.jdbc = jdbc;
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
    public House save(Long customerId, Long houseId, HouseForm form) {
        House house = houseId == null
                ? new House()
                : houses.findLockedByHouseIdAndCustomerIdAndDeletedFalse(houseId, customerId)
                        .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy nhà thuộc tài khoản của bạn."));
        if (houseId == null) {
            house.setCustomerId(customerId);
        }
        house.setHouseName(form.addressType().getLabel());
        house.setRecipientName(form.recipientName().trim());
        house.setRecipientPhone(form.recipientPhone().trim());
        house.setProvinceCity(form.provinceCity().trim());
        house.setWardCommune(form.wardCommune().trim());
        house.setAddressDetail(form.addressDetail().trim());
        house.setAddress(String.join(", ", form.addressDetail().trim(), form.wardCommune().trim(), form.provinceCity().trim()));
        house.setAddressType(form.addressType());
        house.setDefaultAddress(form.defaultAddress());
        house.setAddressNote(form.addressNote() == null || form.addressNote().isBlank() ? null : form.addressNote().trim());
        if (form.defaultAddress()) {
            if (houseId == null) {
                jdbc.update("UPDATE dbo.Houses SET is_default=0 WHERE customer_id=? AND is_deleted=0 AND is_default=1",
                        customerId);
            } else {
                jdbc.update("UPDATE dbo.Houses SET is_default=0 WHERE customer_id=? AND is_deleted=0 AND is_default=1 AND house_id<>?",
                        customerId, houseId);
            }
        }
        return houses.save(house);
    }

    @Transactional
    public void softDelete(Long customerId, Long houseId) {
        House house = houses.findLockedByHouseIdAndCustomerIdAndDeletedFalse(houseId, customerId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy nhà thuộc tài khoản của bạn."));
        Integer activeBookings = jdbc.queryForObject("""
                SELECT COUNT(*) FROM dbo.Bookings
                WHERE house_id=? AND status IN ('CONFIRMED', 'AWAITING_PAYMENT')
                """, Integer.class, houseId);
        if (activeBookings != null && activeBookings > 0) {
            throw new IllegalStateException("Không thể xóa nhà đang có lịch đã xác nhận hoặc chờ thanh toán.");
        }
        house.setDeleted(true);
    }

    @Transactional(readOnly = true)
    public boolean isWithinServiceRadius(BigDecimal latitude, BigDecimal longitude) {
        List<CompanyLocation> configuredCompanies = jdbc.query("""
                SELECT latitude, longitude, service_radius_km
                FROM dbo.Company_Settings ORDER BY company_id
                """, (rs, row) -> new CompanyLocation(
                    rs.getBigDecimal("latitude"), rs.getBigDecimal("longitude"), rs.getBigDecimal("service_radius_km")));
        if (configuredCompanies.size() != 1) {
            throw new IllegalStateException("Cần cấu hình chính xác một vị trí và bán kính phục vụ của công ty.");
        }
        CompanyLocation company = configuredCompanies.getFirst();
        double distance = distanceKm(latitude.doubleValue(), longitude.doubleValue(),
                company.latitude().doubleValue(), company.longitude().doubleValue());
        return distance <= company.radiusKm().doubleValue();
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

    private record CompanyLocation(BigDecimal latitude, BigDecimal longitude, BigDecimal radiusKm) { }

}
