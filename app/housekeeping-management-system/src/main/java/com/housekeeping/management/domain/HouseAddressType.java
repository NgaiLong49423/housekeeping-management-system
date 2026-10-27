package com.housekeeping.management.domain;

public enum HouseAddressType {
    HOME("Nhà riêng"),
    OFFICE("Văn phòng");

    private final String label;

    HouseAddressType(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
