package com.housekeeping.management.integration.vnpay;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class VnpaySignatureTest {
    private static final String SECRET = "TESTSECRETKEY0123456789ABCDEFGHI";
    // HMAC-SHA512 of the canonical string below, computed independently with openssl.
    private static final String SIGNATURE =
            "d39363ab01ea872a5c329a308a5317ce80a08c6348f3fbaa4a8f9d78e8bfaf56f41b98a846f8592e698d47de1eb1542993f79daec32897b70cafb7f9ef9aeb9b";

    @Test
    void canonicalDataSortsFieldsEncodesValuesAndSkipsEmptyAndHashFields() {
        assertThat(VnpaySignature.hashData(params())).isEqualTo(
                "vnp_Amount=15000000&vnp_Command=pay&vnp_OrderInfo=Thanh+toan+don+1001&vnp_TxnRef=1001");
    }

    @Test
    void signsCanonicalDataWithHmacSha512() {
        assertThat(VnpaySignature.sign(params(), SECRET)).isEqualTo(SIGNATURE);
    }

    @Test
    void acceptsMatchingSignatureCaseInsensitivelyAndRejectsTamperedData() {
        Map<String, String> signed = params();
        signed.put("vnp_SecureHash", SIGNATURE.toUpperCase());
        assertThat(VnpaySignature.hasValidSignature(signed, SECRET)).isTrue();

        signed.put("vnp_Amount", "15000100");
        assertThat(VnpaySignature.hasValidSignature(signed, SECRET)).isFalse();
    }

    @Test
    void rejectsMissingSignature() {
        assertThat(VnpaySignature.hasValidSignature(params(), SECRET)).isFalse();
    }

    private static Map<String, String> params() {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("vnp_TxnRef", "1001");
        params.put("vnp_OrderInfo", "Thanh toan don 1001");
        params.put("vnp_BankCode", "");
        params.put("vnp_SecureHashType", "HmacSHA512");
        params.put("vnp_Command", "pay");
        params.put("vnp_Amount", "15000000");
        return params;
    }
}
