package com.housekeeping.management.integration.vnpay;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.housekeeping.management.config.VnpayPaymentProperties;
import java.math.BigDecimal;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class VnpayGatewayClientTest {
    private static final String SECRET = "TESTSECRETKEY0123456789ABCDEFGHI";
    private static final String API_URL = "https://sandbox.vnpayment.vn/merchant_webapi/api/transaction";
    private static final LocalDateTime CREATED_AT = LocalDateTime.of(2026, 11, 8, 10, 15, 30);
    // HMAC-SHA512 values below were computed independently with openssl from hand-written canonical strings.
    private static final String PAYMENT_URL_SIGNATURE =
            "85891609d053f924f2ab6abd614a077b2b8d7b50171301c27773067e12fccf445064bd77a16d2ff452ec53d8ba7cd94ca40b925345e8b3edf1e6976e37e01e38";
    private static final String QUERY_REQUEST_SIGNATURE =
            "3ae3ce64c7dd705b5581c06ced90ac30833dcece244be4c5bdf47c7e2a502531063537190f11551c6ec2c746a36f147ac5fe3525f02c67da7abfba0eb705231d";
    private static final String QUERY_RESPONSE_SIGNATURE =
            "33f04d6fc59d7471db3e53ea761b359d31c65d57f49da3cddae043a73f49240ec6521794f1c1a6916996fddb81a3144ec9ae2389adb63d2f668fc39a459cf2a1";

    private VnpayPaymentProperties properties;
    private RestClient.Builder restClient;
    private MockRestServiceServer vnpay;
    private VnpayGatewayClient client;

    @BeforeEach
    void setUp() {
        properties = new VnpayPaymentProperties();
        properties.setTmnCode("TESTTMN1");
        properties.setHashSecret(SECRET);
        restClient = RestClient.builder();
        vnpay = MockRestServiceServer.bindTo(restClient).build();
        Clock queryTime = Clock.fixed(Instant.parse("2026-11-08T04:00:00Z"), ZoneOffset.ofHours(7));
        client = new VnpayGatewayClient(properties, restClient, queryTime, () -> "req0001");
    }

    @Test
    void paymentUrlCarriesOrderAmountTimesReturnUrlAndSignature() {
        String url = client.paymentUrl(1001L, 150000L, CREATED_AT, "127.0.0.1");

        assertThat(url).startsWith("https://sandbox.vnpayment.vn/paymentv2/vpcpay.html?");
        Map<String, String> expected = new LinkedHashMap<>();
        expected.put("vnp_Amount", "15000000");
        expected.put("vnp_Command", "pay");
        expected.put("vnp_CreateDate", "20261108101530");
        expected.put("vnp_CurrCode", "VND");
        expected.put("vnp_ExpireDate", "20261108103030");
        expected.put("vnp_IpAddr", "127.0.0.1");
        expected.put("vnp_Locale", "vn");
        expected.put("vnp_OrderInfo", "Thanh toan don dat lich 1001");
        expected.put("vnp_OrderType", "other");
        expected.put("vnp_ReturnUrl", "http://localhost:8080/payments/vnpay/return");
        expected.put("vnp_TmnCode", "TESTTMN1");
        expected.put("vnp_TxnRef", "1001");
        expected.put("vnp_Version", "2.1.0");
        expected.put("vnp_SecureHash", PAYMENT_URL_SIGNATURE);
        assertThat(queryParams(url)).containsExactlyInAnyOrderEntriesOf(expected);
    }

    @Test
    void signedReturnParamsAreVerifiedWithTheMerchantSecret() {
        Map<String, String> returned = queryParams(client.paymentUrl(1001L, 150000L, CREATED_AT, "127.0.0.1"));
        assertThat(client.hasValidSignature(returned)).isTrue();

        properties.setHashSecret("ANOTHERSECRET0123456789ABCDEFGHI");
        assertThat(client.hasValidSignature(returned)).isFalse();
    }

    @Test
    void queryPostsSignedQuerydrRequestAndReturnsVerifiedPaidResult() {
        vnpay.expect(requestTo(API_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().json("""
                        {"vnp_RequestId":"req0001","vnp_Version":"2.1.0","vnp_Command":"querydr",
                         "vnp_TmnCode":"TESTTMN1","vnp_TxnRef":"1001","vnp_OrderInfo":"Kiem tra giao dich 1001",
                         "vnp_TransactionDate":"20261108101530","vnp_CreateDate":"20261108110000",
                         "vnp_IpAddr":"127.0.0.1","vnp_SecureHash":"%s"}""".formatted(QUERY_REQUEST_SIGNATURE)))
                .andRespond(withSuccess(queryResponse("15000000", "1001", QUERY_RESPONSE_SIGNATURE), MediaType.APPLICATION_JSON));

        VnpayGatewayClient.QueryResult result = client.query(1001L, CREATED_AT);

        vnpay.verify();
        assertThat(result.paid()).isTrue();
        assertThat(result.amount()).isEqualByComparingTo(new BigDecimal("150000"));
    }

    @Test
    void queryRejectsResponseWhoseChecksumDoesNotMatchItsData() {
        vnpay.expect(requestTo(API_URL))
                .andRespond(withSuccess(queryResponse("1500000", "1001", QUERY_RESPONSE_SIGNATURE), MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.query(1001L, CREATED_AT)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void queryRejectsResponseForAnotherOrder() {
        String signature = VnpaySignature.hmacSha512(SECRET,
                "resp0001|querydr|00|QueryDR Success|TESTTMN1|1002|15000000|NCB|20261108102000|14567890|01|00|Thanh toan don dat lich 1001||");
        vnpay.expect(requestTo(API_URL))
                .andRespond(withSuccess(queryResponse("15000000", "1002", signature), MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.query(1001L, CREATED_AT)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void unsignedQueryErrorFromVnpayIsReportedAsUnpaidInsteadOfFailing() {
        // Shape captured from the real sandbox: error responses carry no checksum.
        vnpay.expect(requestTo(API_URL))
                .andRespond(withSuccess("{\"vnp_ResponseCode\":\"99\",\"vnp_Message\":\"Unexpected error\"}",
                        MediaType.APPLICATION_JSON));

        VnpayGatewayClient.QueryResult result = client.query(1001L, CREATED_AT);

        assertThat(result.paid()).isFalse();
        assertThat(result.responseCode()).isEqualTo("99");
    }

    @Test
    void successResponseWithoutChecksumIsRejected() {
        vnpay.expect(requestTo(API_URL))
                .andRespond(withSuccess("{\"vnp_ResponseCode\":\"00\",\"vnp_TxnRef\":\"1001\",\"vnp_TmnCode\":\"TESTTMN1\","
                        + "\"vnp_TransactionStatus\":\"00\",\"vnp_Amount\":\"15000000\"}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.query(1001L, CREATED_AT)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void checkoutStaysOpenForFifteenMinutesAfterTheAttemptIsCreated() {
        // The test clock reads 11:00:00 in Vietnam.
        assertThat(client.checkoutOpen(LocalDateTime.of(2026, 11, 8, 10, 45, 1))).isTrue();
        assertThat(client.checkoutOpen(LocalDateTime.of(2026, 11, 8, 10, 45, 0))).isFalse();
    }

    @Test
    void configurationNeedsMerchantCodesAndHttpsVnpayEndpoints() {
        assertThat(properties.isConfigured()).isTrue();

        properties.setHashSecret(" ");
        assertThat(properties.isConfigured()).isFalse();

        properties.setHashSecret(SECRET);
        properties.setApiUrl("http://sandbox.vnpayment.vn/merchant_webapi/api/transaction");
        assertThat(properties.isConfigured()).isFalse();
    }

    private static String queryResponse(String amount, String txnRef, String signature) {
        return """
                {"vnp_ResponseId":"resp0001","vnp_Command":"querydr","vnp_ResponseCode":"00",
                 "vnp_Message":"QueryDR Success","vnp_TmnCode":"TESTTMN1","vnp_TxnRef":"%s","vnp_Amount":"%s",
                 "vnp_OrderInfo":"Thanh toan don dat lich 1001","vnp_BankCode":"NCB","vnp_PayDate":"20261108102000",
                 "vnp_TransactionNo":"14567890","vnp_TransactionType":"01","vnp_TransactionStatus":"00",
                 "vnp_SecureHash":"%s"}""".formatted(txnRef, amount, signature);
    }

    private static Map<String, String> queryParams(String url) {
        Map<String, String> params = new LinkedHashMap<>();
        for (String pair : URI.create(url).getRawQuery().split("&")) {
            String[] parts = pair.split("=", 2);
            params.put(URLDecoder.decode(parts[0], StandardCharsets.US_ASCII),
                    URLDecoder.decode(parts[1], StandardCharsets.US_ASCII));
        }
        return params;
    }
}
