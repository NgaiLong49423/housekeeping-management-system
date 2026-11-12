package com.housekeeping.management.dto.entity;

import com.housekeeping.management.enums.HouseAddressType;
import com.housekeeping.management.entity.House;
import java.math.BigDecimal;

public record HouseDTO(Long houseId, Long customerId, String houseName, String address, BigDecimal latitude,
        BigDecimal longitude, String recipientName, String recipientPhone, String provinceCity, String wardCommune,
        String addressDetail, HouseAddressType addressType, boolean defaultAddress, String addressNote, boolean deleted) {
    public static HouseDTO convertEntityToDTO(House entity) {
        if (entity == null) return null;
        return new HouseDTO(entity.getHouseId(), entity.getCustomerId(), entity.getHouseName(), entity.getAddress(),
                entity.getLatitude(), entity.getLongitude(), entity.getRecipientName(), entity.getRecipientPhone(),
                entity.getProvinceCity(), entity.getWardCommune(), entity.getAddressDetail(), entity.getAddressType(),
                entity.isDefaultAddress(), entity.getAddressNote(), entity.isDeleted());
    }

    public House convertDTOToEntity() {
        return convertDTOToEntity(new House());
    }

    public House convertDTOToEntity(House entity) {
        if (entity == null) entity = new House();
        entity.setHouseId(houseId);
        entity.setCustomerId(customerId);
        entity.setHouseName(houseName);
        entity.setAddress(address);
        entity.setLatitude(latitude);
        entity.setLongitude(longitude);
        entity.setRecipientName(recipientName);
        entity.setRecipientPhone(recipientPhone);
        entity.setProvinceCity(provinceCity);
        entity.setWardCommune(wardCommune);
        entity.setAddressDetail(addressDetail);
        entity.setAddressType(addressType);
        entity.setDefaultAddress(defaultAddress);
        entity.setAddressNote(addressNote);
        entity.setDeleted(deleted);
        return entity;
    }
}
