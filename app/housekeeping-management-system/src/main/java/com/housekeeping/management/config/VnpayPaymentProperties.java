package com.housekeeping.management.config;

import java.net.URI;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Cấu hình VNPay đọc từ {@code app.payment.vnpay.*} (mã TMN và khóa bí mật lấy từ file .env). */
@Getter
@Setter
@ConfigurationProperties(prefix = "app.payment.vnpay")
public class VnpayPaymentProperties {
    private String tmnCode = "";
    private String hashSecret = "";
    private String payUrl = "https://sandbox.vnpayment.vn/paymentv2/vpcpay.html";
    private String apiUrl = "https://sandbox.vnpayment.vn/merchant_webapi/api/transaction";
    private String returnUrl = "http://localhost:8080/payments/vnpay/return";

    /** Đủ mã TMN, khóa bí mật và các URL hợp lệ thì mới cho thanh toán qua VNPay. */
    public boolean isConfigured() {
        return present(tmnCode) && present(hashSecret) && isHttps(payUrl) && isHttps(apiUrl) && isWebUrl(returnUrl);
    }

    private static boolean present(String value) {
        return value != null && !value.isBlank();
    }

    private static boolean isHttps(String value) {
        URI uri = parse(value);
        return uri != null && "https".equalsIgnoreCase(uri.getScheme()) && present(uri.getHost());
    }

    /** URL quay về có thể là http (chạy trên máy cá nhân) hoặc https. */
    private static boolean isWebUrl(String value) {
        URI uri = parse(value);
        if (uri == null || !present(uri.getHost())) return false;
        return "https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme());
    }

    private static URI parse(String value) {
        if (!present(value)) return null;
        try {
            return URI.create(value.trim());
        } catch (IllegalArgumentException invalid) {
            return null;
        }
    }
}
