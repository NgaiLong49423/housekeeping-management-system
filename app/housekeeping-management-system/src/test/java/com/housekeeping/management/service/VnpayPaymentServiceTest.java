package com.housekeeping.management.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

import com.housekeeping.management.repository.PaymentPersistenceRepository.PaymentState;
import com.housekeeping.management.web.PortalMessages;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class VnpayPaymentServiceTest {
    private static final LocalDateTime CREATED_AT = LocalDateTime.of(2026, 11, 8, 10, 15, 30);
    private static final String CHECKOUT_URL = "https://sandbox.vnpayment.vn/paymentv2/vpcpay.html?vnp_TxnRef=1001";

    private PaymentService payments;
    private VnpayGatewayClient gateway;
    private VnpayPaymentService service;

    @BeforeEach
    void setUp() {
        payments = mock(PaymentService.class);
        gateway = mock(VnpayGatewayClient.class);
        service = new VnpayPaymentService(payments, gateway);
        when(gateway.isConfigured()).thenReturn(true);
    }

    @Test
    void checkoutWithoutVnpayCodesExplainsTheMissingSettingsAndCreatesNoAttempt() {
        when(gateway.isConfigured()).thenReturn(false);

        IllegalStateException failure = catchThrowableOfType(IllegalStateException.class,
                () -> service.checkout(42L, 7L, "127.0.0.1"));

        assertThat(PortalMessages.business(failure)).contains("VNPAY_TMN_CODE").contains("VNPAY_HASH_SECRET");
        verifyNoInteractions(payments);
    }

    @Test
    void checkoutRedirectsNewAttemptToSignedVnpayUrl() {
        when(payments.prepareOnlineCheckout(42L, 7L))
                .thenReturn(new PaymentService.OnlineCheckout(1001L, 150000L, CREATED_AT, false));
        when(gateway.paymentUrl(1001L, 150000L, CREATED_AT, "127.0.0.1")).thenReturn(CHECKOUT_URL);

        VnpayPaymentService.Checkout checkout = service.checkout(42L, 7L, "127.0.0.1");

        assertThat(checkout).isEqualTo(new VnpayPaymentService.Checkout(CHECKOUT_URL, false));
        verify(gateway, never()).query(anyLong(), any());
    }

    @Test
    void checkoutConfirmsPendingAttemptThatVnpayAlreadyChargedInsteadOfChargingAgain() {
        when(payments.prepareOnlineCheckout(42L, 7L))
                .thenReturn(new PaymentService.OnlineCheckout(1001L, 150000L, CREATED_AT, true));
        when(gateway.query(1001L, CREATED_AT)).thenReturn(paid("150000"));

        VnpayPaymentService.Checkout checkout = service.checkout(42L, 7L, "127.0.0.1");

        assertThat(checkout.alreadyPaid()).isTrue();
        verify(payments).confirmOnlinePayment(1001L, new BigDecimal("150000"));
        verify(gateway, never()).paymentUrl(anyLong(), anyLong(), any(), any());
    }

    @Test
    void checkoutReplacesUnpaidPendingAttemptWithNewOrder() {
        LocalDateTime retriedAt = CREATED_AT.plusMinutes(20);
        when(payments.prepareOnlineCheckout(42L, 7L))
                .thenReturn(new PaymentService.OnlineCheckout(1001L, 150000L, CREATED_AT, true))
                .thenReturn(new PaymentService.OnlineCheckout(1002L, 150000L, retriedAt, false));
        when(gateway.query(1001L, CREATED_AT)).thenReturn(new VnpayGatewayClient.QueryResult("91", "", null));
        when(gateway.paymentUrl(1002L, 150000L, retriedAt, "127.0.0.1")).thenReturn(CHECKOUT_URL + "2");

        VnpayPaymentService.Checkout checkout = service.checkout(42L, 7L, "127.0.0.1");

        assertThat(checkout).isEqualTo(new VnpayPaymentService.Checkout(CHECKOUT_URL + "2", false));
        verify(payments).failOnlinePayment(1001L, new BigDecimal("150000"));
        verify(payments, never()).confirmOnlinePayment(anyLong(), any());
    }

    @Test
    void checkoutResumesAttemptThatIsStillOpenAtVnpay() {
        when(payments.prepareOnlineCheckout(42L, 7L))
                .thenReturn(new PaymentService.OnlineCheckout(1001L, 150000L, CREATED_AT, true));
        when(gateway.query(1001L, CREATED_AT)).thenReturn(new VnpayGatewayClient.QueryResult("00", "01", new BigDecimal("150000")));
        when(gateway.checkoutOpen(CREATED_AT)).thenReturn(true);
        when(gateway.paymentUrl(1001L, 150000L, CREATED_AT, "127.0.0.1")).thenReturn(CHECKOUT_URL);

        assertThat(service.checkout(42L, 7L, "127.0.0.1")).isEqualTo(new VnpayPaymentService.Checkout(CHECKOUT_URL, false));
        verify(payments, never()).failOnlinePayment(anyLong(), any());
        verify(payments, times(1)).prepareOnlineCheckout(42L, 7L);
    }

    @Test
    void checkoutReplacesUnfinishedAttemptWhoseCheckoutExpired() {
        LocalDateTime retriedAt = CREATED_AT.plusMinutes(20);
        when(payments.prepareOnlineCheckout(42L, 7L))
                .thenReturn(new PaymentService.OnlineCheckout(1001L, 150000L, CREATED_AT, true))
                .thenReturn(new PaymentService.OnlineCheckout(1002L, 150000L, retriedAt, false));
        when(gateway.query(1001L, CREATED_AT)).thenReturn(new VnpayGatewayClient.QueryResult("00", "01", new BigDecimal("150000")));
        when(gateway.checkoutOpen(CREATED_AT)).thenReturn(false);
        when(gateway.paymentUrl(1002L, 150000L, retriedAt, "127.0.0.1")).thenReturn(CHECKOUT_URL + "2");

        assertThat(service.checkout(42L, 7L, "127.0.0.1").redirectUrl()).isEqualTo(CHECKOUT_URL + "2");
        verify(payments).failOnlinePayment(1001L, new BigDecimal("150000"));
    }

    @Test
    void checkoutKeepsPendingAttemptWhenVnpayCannotReportItsStatus() {
        when(payments.prepareOnlineCheckout(42L, 7L))
                .thenReturn(new PaymentService.OnlineCheckout(1001L, 150000L, CREATED_AT, true));
        when(gateway.query(1001L, CREATED_AT)).thenReturn(new VnpayGatewayClient.QueryResult("99", "", null));

        IllegalStateException failure = catchThrowableOfType(IllegalStateException.class,
                () -> service.checkout(42L, 7L, "127.0.0.1"));

        assertThat(PortalMessages.business(failure)).contains("VNPay").contains("thử lại");
        verify(payments, never()).failOnlinePayment(anyLong(), any());
        verify(gateway, never()).paymentUrl(anyLong(), anyLong(), any(), any());
    }

    @Test
    void returnConfirmsPaymentOnlyAfterVnpayQueryReportsItPaid() {
        Map<String, String> returned = returned("00");
        when(gateway.hasValidSignature(returned)).thenReturn(true);
        when(payments.findOnlinePayment(1001L)).thenReturn(Optional.of(attempt("PENDING")));
        when(gateway.query(1001L, CREATED_AT)).thenReturn(paid("150000"));

        assertThat(service.handleReturn(returned))
                .isEqualTo(new VnpayPaymentService.ReturnOutcome(42L, VnpayPaymentService.ReturnStatus.PAID));
        verify(payments).confirmOnlinePayment(1001L, new BigDecimal("150000"));
    }

    @Test
    void returnFromCancelledCheckoutMarksAttemptFailed() {
        Map<String, String> returned = returned("24");
        when(gateway.hasValidSignature(returned)).thenReturn(true);
        when(payments.findOnlinePayment(1001L)).thenReturn(Optional.of(attempt("PENDING")));
        when(gateway.query(1001L, CREATED_AT)).thenReturn(new VnpayGatewayClient.QueryResult("00", "02", new BigDecimal("150000")));

        assertThat(service.handleReturn(returned).status()).isEqualTo(VnpayPaymentService.ReturnStatus.FAILED);
        verify(payments).failOnlinePayment(1001L, new BigDecimal("150000.00"));
        verify(payments, never()).confirmOnlinePayment(anyLong(), any());
    }

    @Test
    void returnClaimingSuccessThatVnpayHasNotConfirmedLeavesAttemptPending() {
        Map<String, String> returned = returned("00");
        when(gateway.hasValidSignature(returned)).thenReturn(true);
        when(payments.findOnlinePayment(1001L)).thenReturn(Optional.of(attempt("PENDING")));
        when(gateway.query(1001L, CREATED_AT)).thenReturn(new VnpayGatewayClient.QueryResult("00", "01", new BigDecimal("150000")));

        assertThat(service.handleReturn(returned).status()).isEqualTo(VnpayPaymentService.ReturnStatus.PENDING);
        verify(payments, never()).confirmOnlinePayment(anyLong(), any());
        verify(payments, never()).failOnlinePayment(anyLong(), any());
    }

    @Test
    void returnForAttemptAlreadyPaidReportsPaidWithoutQueryingAgain() {
        Map<String, String> returned = returned("00");
        when(gateway.hasValidSignature(returned)).thenReturn(true);
        when(payments.findOnlinePayment(1001L)).thenReturn(Optional.of(attempt("SUCCESS")));

        assertThat(service.handleReturn(returned).status()).isEqualTo(VnpayPaymentService.ReturnStatus.PAID);
        verify(gateway, never()).query(anyLong(), any());
    }

    @Test
    void returnWithInvalidSignatureChangesNothing() {
        Map<String, String> returned = returned("00");
        when(gateway.hasValidSignature(returned)).thenReturn(false);

        assertThatThrownBy(() -> service.handleReturn(returned)).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(payments);
        verify(gateway, never()).query(anyLong(), any());
    }

    @Test
    void ipnWithInvalidChecksumIsRejected() {
        Map<String, String> ipn = ipn("00", "00", "15000000");
        when(gateway.hasValidSignature(ipn)).thenReturn(false);

        assertThat(service.handleIpn(ipn).rspCode()).isEqualTo("97");
        verifyNoInteractions(payments);
    }

    @Test
    void ipnForUnknownOrderIsReportedNotFound() {
        Map<String, String> ipn = ipn("00", "00", "15000000");
        when(gateway.hasValidSignature(ipn)).thenReturn(true);
        when(payments.findOnlinePayment(1001L)).thenReturn(Optional.empty());

        assertThat(service.handleIpn(ipn).rspCode()).isEqualTo("01");
    }

    @Test
    void ipnWithDifferentAmountIsRejectedWithoutConfirming() {
        Map<String, String> ipn = ipn("00", "00", "15000100");
        when(gateway.hasValidSignature(ipn)).thenReturn(true);
        when(payments.findOnlinePayment(1001L)).thenReturn(Optional.of(attempt("PENDING")));

        assertThat(service.handleIpn(ipn).rspCode()).isEqualTo("04");
        verify(payments, never()).confirmOnlinePayment(anyLong(), any());
    }

    @Test
    void successfulIpnConfirmsPayment() {
        Map<String, String> ipn = ipn("00", "00", "15000000");
        when(gateway.hasValidSignature(ipn)).thenReturn(true);
        when(payments.findOnlinePayment(1001L)).thenReturn(Optional.of(attempt("PENDING")));

        assertThat(service.handleIpn(ipn)).isEqualTo(new VnpayPaymentService.IpnResponse("00", "Confirm Success"));
        verify(payments).confirmOnlinePayment(1001L, new BigDecimal("150000.00"));
    }

    @Test
    void ipnForAlreadyPaidOrderIsAcknowledgedWithoutConfirmingAgain() {
        Map<String, String> ipn = ipn("00", "00", "15000000");
        when(gateway.hasValidSignature(ipn)).thenReturn(true);
        when(payments.findOnlinePayment(1001L)).thenReturn(Optional.of(attempt("SUCCESS")));

        assertThat(service.handleIpn(ipn).rspCode()).isEqualTo("02");
        verify(payments, never()).confirmOnlinePayment(anyLong(), any());
    }

    @Test
    void failedIpnMarksAttemptFailed() {
        Map<String, String> ipn = ipn("24", "02", "15000000");
        when(gateway.hasValidSignature(ipn)).thenReturn(true);
        when(payments.findOnlinePayment(1001L)).thenReturn(Optional.of(attempt("PENDING")));

        assertThat(service.handleIpn(ipn).rspCode()).isEqualTo("00");
        verify(payments).failOnlinePayment(1001L, new BigDecimal("150000.00"));
        verify(payments, never()).confirmOnlinePayment(anyLong(), any());
    }

    @Test
    void ipnThatCannotBeRecordedAsksVnpayToRetry() {
        Map<String, String> ipn = ipn("00", "00", "15000000");
        when(gateway.hasValidSignature(ipn)).thenReturn(true);
        when(payments.findOnlinePayment(1001L)).thenReturn(Optional.of(attempt("PENDING")));
        doThrow(new IllegalStateException("Lịch hẹn không còn chờ thanh toán trực tuyến."))
                .when(payments).confirmOnlinePayment(anyLong(), any());

        assertThat(service.handleIpn(ipn).rspCode()).isEqualTo("99");
    }

    private static VnpayGatewayClient.QueryResult paid(String amount) {
        return new VnpayGatewayClient.QueryResult("00", "00", new BigDecimal(amount));
    }

    private static PaymentState attempt(String status) {
        return new PaymentState(19L, 42L, new BigDecimal("150000.00"), "ONLINE", status, "1001", CREATED_AT);
    }

    private static Map<String, String> returned(String responseCode) {
        Map<String, String> params = new HashMap<>();
        params.put("vnp_TxnRef", "1001");
        params.put("vnp_Amount", "15000000");
        params.put("vnp_ResponseCode", responseCode);
        params.put("vnp_SecureHash", "signature");
        return params;
    }

    private static Map<String, String> ipn(String responseCode, String transactionStatus, String amount) {
        Map<String, String> params = returned(responseCode);
        params.put("vnp_TransactionStatus", transactionStatus);
        params.put("vnp_Amount", amount);
        return params;
    }
}
