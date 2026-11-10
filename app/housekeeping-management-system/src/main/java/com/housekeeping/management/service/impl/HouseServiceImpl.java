package com.housekeeping.management.service.impl;

import com.housekeeping.management.service.HouseService;
import com.housekeeping.management.service.VietmapClient;

import com.housekeeping.management.dto.request.PortalForms.HouseForm;
import com.housekeeping.management.dto.entity.HouseDTO;
import com.housekeeping.management.domain.HouseAddressType;
import com.housekeeping.management.entity.House;
import com.housekeeping.management.repository.HouseRepository;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class HouseServiceImpl implements HouseService {
    private static final double EARTH_RADIUS_KM = 6371.0088;
    private final HouseRepository houses;
    private final JdbcTemplate jdbc;
    private final VietmapClient vietmap;

    public HouseServiceImpl(HouseRepository houses, JdbcTemplate jdbc, VietmapClient vietmap) {
        this.houses = houses;
        this.jdbc = jdbc;
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
        if (form == null || form.vietmapRefId() == null || form.vietmapRefId().isBlank() || form.vietmapRefId().length() > 500) {
            throw new IllegalArgumentException("Hãy chọn một kết quả địa chỉ VietMap hợp lệ.");
        }
        House house = houseId == null ? null
                : houses.findLockedByHouseIdAndCustomerIdAndDeletedFalse(houseId, customerId)
                        .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy nhà thuộc tài khoản của bạn."));
        VietmapClient.Place place = vietmap.place(form.vietmapRefId());
        String province = place.city() == null ? "" : place.city();
        String ward = place.ward() == null ? "" : place.ward();
        String detail = place.address() == null ? place.display() : place.address();
        String addressNote = form.addressNote() == null || form.addressNote().isBlank() ? null : form.addressNote().trim();
        HouseDTO dto = new HouseDTO(house == null ? null : house.getHouseId(), customerId,
                form.addressType().getLabel(), place.display(), place.latitude(), place.longitude(),
                form.recipientName().trim(), form.recipientPhone().trim(), province, ward, detail,
                form.addressType(), form.defaultAddress(), addressNote, house != null && house.isDeleted());
        house = dto.convertDTOToEntity(house);
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

    public List<VietmapClient.Suggestion> autocomplete(String query) {
        if (query == null || query.trim().length() < 3 || query.length() > 200) return List.of();
        return vietmap.autocomplete(query.trim());
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
        return serviceCenter().contains(latitude, longitude);
    }

    @Transactional(readOnly = true)
    public ServiceCenter serviceCenter() {
        List<CompanyLocation> configuredCompanies = jdbc.query("""
                SELECT latitude, longitude, service_radius_km
                FROM dbo.Company_Settings ORDER BY company_id
                """, (rs, row) -> new CompanyLocation(
                    rs.getBigDecimal("latitude"), rs.getBigDecimal("longitude"), rs.getBigDecimal("service_radius_km")));
        if (configuredCompanies.size() != 1) {
            throw new IllegalStateException("Cần cấu hình chính xác một vị trí và bán kính phục vụ của công ty.");
        }
        CompanyLocation company = configuredCompanies.getFirst();
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

    private record CompanyLocation(BigDecimal latitude, BigDecimal longitude, BigDecimal radiusKm) { }

}
