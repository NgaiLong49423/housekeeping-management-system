package com.housekeeping.management.enums;

import com.housekeeping.management.web.PortalAdvice;

public enum UiText {
    INSTANCE;

    public String label(String value) {
        return PortalAdvice.cleanLabel(value);
    }

    public String initial(String value) {
        String cleaned = PortalAdvice.cleanLabel(value);
        return (cleaned == null || cleaned.isEmpty()) ? "?" : cleaned.substring(0, 1).toUpperCase();
    }
}
