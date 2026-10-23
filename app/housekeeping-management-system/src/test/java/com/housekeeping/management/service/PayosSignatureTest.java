package com.housekeeping.management.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PayosSignatureTest {
    @Test
    void verifiesPayosDocumentedWebhookSignatureAndRejectsTampering() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("orderCode", 123);
        data.put("amount", 3000);
        data.put("description", "VQRIO123");
        data.put("accountNumber", "12345678");
        data.put("reference", "TF230204212323");
        data.put("transactionDateTime", "2023-02-04 18:25:00");
        data.put("currency", "VND");
        data.put("paymentLinkId", "124c33293c43417ab7879e14c8d9eb18");
        data.put("code", "00");
        data.put("desc", "Thành công");
        data.put("counterAccountBankId", "");
        data.put("counterAccountBankName", "");
        data.put("counterAccountName", "");
        data.put("counterAccountNumber", "");
        data.put("virtualAccountName", "");
        data.put("virtualAccountNumber", "");
        String documentedSignature = "8d8640d802576397a1ce45ebda7f835055768ac7ad2e0bfb77f9b8f12cca4c7f";
        String checksumKey = "1a54716c8f0efb2744fb28b6e38b25da7f67a925d98bc1c18bd8faaecadd7675";

        assertThat(PayosSignature.isValid(documentedSignature,
                PayosSignature.createDataSignature(data, checksumKey))).isTrue();
        data.put("amount", 3001);
        assertThat(PayosSignature.isValid(documentedSignature,
                PayosSignature.createDataSignature(data, checksumKey))).isFalse();
    }
}
