package com.housekeeping.management.dto;

import java.time.LocalDateTime;
import java.time.ZoneId;

/** Quy tắc chung khi khóa hoặc mở khóa tài khoản Customer, Staff và Admin. */
public final class AccountLockRules {
    public static final int MIN_REASON_LENGTH = 5;
    public static final int MAX_REASON_LENGTH = 500;
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private AccountLockRules() { }

    /** Trả về lý do đã bỏ khoảng trắng thừa; báo lỗi nếu thiếu hoặc quá ngắn/dài. */
    public static String requireReason(String reason) {
        String normalized = reason == null ? "" : reason.strip().replaceAll("\\s+", " ");
        if (normalized.length() < MIN_REASON_LENGTH || normalized.length() > MAX_REASON_LENGTH) {
            throw new IllegalArgumentException("Vui lòng nhập lý do khóa tài khoản (từ "
                    + MIN_REASON_LENGTH + " đến " + MAX_REASON_LENGTH + " ký tự).");
        }
        return normalized;
    }

    public static LocalDateTime now() {
        return LocalDateTime.now(BUSINESS_ZONE).withNano(0);
    }
}
