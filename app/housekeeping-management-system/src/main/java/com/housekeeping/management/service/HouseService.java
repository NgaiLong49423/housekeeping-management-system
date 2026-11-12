package com.housekeeping.management.service;

import com.housekeeping.management.dto.request.PortalForms.HouseForm;
import com.housekeeping.management.dto.response.LocationSuggestion;
import com.housekeeping.management.enums.HouseAddressType;
import com.housekeeping.management.entity.House;
import java.math.BigDecimal;
import java.util.List;

public interface HouseService {
    public record ServiceAreaAssessment(boolean eligible, String message) { }
    public record ServiceCenter(BigDecimal latitude,BigDecimal longitude,BigDecimal radiusKm) {
        public boolean contains(BigDecimal latitude,BigDecimal longitude) {
            return distanceKm(latitude.doubleValue(),longitude.doubleValue(),this.latitude.doubleValue(),this.longitude.doubleValue())<=radiusKm.doubleValue();
        }
    }
    public List<House> findCustomerHouses(Long customerId);
    public House findCustomerHouse(Long customerId, Long houseId);
    House findCustomerHouseForUpdate(Long customerId, Long houseId);
    public House findHouse(Long houseId);
    public House save(Long customerId, Long houseId, HouseForm form);
    List<LocationSuggestion> autocomplete(String query);
    public void softDelete(Long customerId, Long houseId);
    public boolean isWithinServiceRadius(BigDecimal latitude, BigDecimal longitude);
    ServiceAreaAssessment assessCustomerHouseServiceArea(Long customerId, Long houseId);
    public ServiceCenter serviceCenter();
    static double distanceKm(double lat1,double lon1,double lat2,double lon2) {
        double latitudeDelta=Math.toRadians(lat2-lat1), longitudeDelta=Math.toRadians(lon2-lon1);
        double startLatitude=Math.toRadians(lat1), endLatitude=Math.toRadians(lat2);
        double haversine=Math.pow(Math.sin(latitudeDelta/2),2)+Math.cos(startLatitude)*Math.cos(endLatitude)*Math.pow(Math.sin(longitudeDelta/2),2);
        return 2*6371.0088*Math.asin(Math.sqrt(Math.min(1,haversine)));
    }
}
