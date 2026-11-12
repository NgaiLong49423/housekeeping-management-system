package com.housekeeping.management.service;

import com.housekeeping.management.dto.request.PortalForms.HouseForm;
import com.housekeeping.management.dto.response.LocationSuggestion;
import com.housekeeping.management.domain.HouseAddressType;
import com.housekeeping.management.entity.House;
import java.util.List;

public interface HouseService {
    public List<House> findCustomerHouses(Long customerId);
    public House findCustomerHouse(Long customerId, Long houseId);
    House ensureRouteLocation(Long customerId, Long houseId);
    House findCustomerHouseForUpdate(Long customerId, Long houseId);
    public House findHouse(Long houseId);
    public House save(Long customerId, Long houseId, HouseForm form);
    List<LocationSuggestion> autocomplete(String query);
    public void softDelete(Long customerId, Long houseId);
}
