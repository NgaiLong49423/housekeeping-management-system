package com.housekeeping.management.domain;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Địa chỉ nhà phải đủ cụ thể để Staff tìm tới tận cửa: có số nhà (hoặc số hẻm/căn hộ) và tên đường/tòa nhà.
 * Chỉ ghi phường, quận hay thành phố ("Quận 1", "Phường Bến Thành") là chưa đủ.
 */
public final class SpecificAddressRules {
    public static final int MIN_LENGTH = 5;
    public static final int MAX_LENGTH = 200;
    public static final String REQUIREMENT_MESSAGE = "Hãy nhập địa chỉ cụ thể gồm số nhà (hoặc số hẻm, số căn hộ) và tên đường, "
            + "ví dụ: \"12/3 hẻm 45 Nguyễn Trãi\" hoặc \"Căn hộ 12.05, 45 Nguyễn Thị Minh Khai\".";

    // Số đứng sau tên đơn vị hành chính ("Quận 1", "P.5", "TP. Thủ Đức" không có số) không phải số nhà.
    private static final Pattern ADMINISTRATIVE_NUMBER = Pattern.compile(
            "(?iu)(?<![\\p{L}])(quận|huyện|phường|xã|thị xã|thị trấn|tỉnh|thành phố|tp|q|p)\\.?\\s*\\d+");
    private static final Pattern DIGIT = Pattern.compile("\\d");
    private static final Pattern WORD = Pattern.compile("(?u)\\p{L}{2,}");

    private SpecificAddressRules() { }

    public static boolean isSpecific(String address) {
        String normalized = normalize(address);
        if (normalized.length() < MIN_LENGTH || normalized.length() > MAX_LENGTH) return false;
        String withoutAdministrativeNumbers = ADMINISTRATIVE_NUMBER.matcher(normalized.toLowerCase(Locale.ROOT)).replaceAll(" ");
        return DIGIT.matcher(withoutAdministrativeNumbers).find() && WORD.matcher(withoutAdministrativeNumbers).find();
    }

    /** Trả về địa chỉ đã bỏ khoảng trắng thừa; báo lỗi nếu chưa đủ cụ thể. */
    public static String requireSpecific(String address) {
        if (!isSpecific(address)) throw new IllegalArgumentException(REQUIREMENT_MESSAGE);
        return normalize(address);
    }

    private static String normalize(String address) {
        return address == null ? "" : address.strip().replaceAll("\\s+", " ");
    }
}
