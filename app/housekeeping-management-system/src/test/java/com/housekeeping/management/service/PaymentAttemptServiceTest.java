package com.housekeeping.management.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import com.housekeeping.management.domain.BookingStatus;
import com.housekeeping.management.domain.PaymentAttemptStatus;
import com.housekeeping.management.domain.PaymentMethod;
import com.housekeeping.management.domain.PaymentStatus;
import com.housekeeping.management.domain.entity.Booking;
import com.housekeeping.management.domain.entity.Customer;
import com.housekeeping.management.domain.entity.Payment;
import com.housekeeping.management.domain.entity.PaymentAttempt;
import com.housekeeping.management.repository.PaymentAttemptRepository;
import com.housekeeping.management.repository.PaymentRepository;
import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PaymentAttemptServiceTest {
    private PaymentRepository payments;
    private PaymentAttemptRepository attempts;
    private PaymentAttemptService service;
    private Payment payment;
    private PaymentAttempt attempt;

    @BeforeEach
    void setUp() {
        payments = mock(PaymentRepository.class);
        attempts = mock(PaymentAttemptRepository.class);
        service = new PaymentAttemptService(payments, attempts);
        Customer customer = new Customer();
        customer.setCustomerId(7L);
        Booking booking = new Booking();
        booking.setBookingId(42L);
        booking.setCustomer(customer);
        booking.setBookingStatus(BookingStatus.COMPLETED);
        payment = new Payment();
        payment.setPaymentId(11L);
        payment.setBooking(booking);
        payment.setPaymentStatus(PaymentStatus.UNPAID);
        payment.setPaymentMethod(PaymentMethod.PAYOS);
        payment.setAmount(new BigDecimal("150000"));
        attempt = new PaymentAttempt();
        attempt.setAttemptId(19L);
        attempt.setPayment(payment);
        attempt.setProviderOrderId("100000001");
        attempt.setRequestId("attempt-request-1");
        attempt.setProviderPaymentLinkId("payment-link-1");
        attempt.setAmount(new BigDecimal("150000"));
        attempt.setStatus(PaymentAttemptStatus.PENDING);
    }

    @Test
    void verifiedSuccessfulWebhookMarksPaymentPaidAndStoresProviderReference() {
        when(attempts.findByProviderOrderIdForUpdate("100000001")).thenReturn(Optional.of(attempt));
        when(payments.findByBookingIdForUpdate(42L)).thenReturn(Optional.of(payment));

        service.processWebhook(webhook(150000L, "00", "transaction-123"), true);

        assertThat(payment.getPaymentMethod()).isEqualTo(PaymentMethod.PAYOS);
        assertThat(payment.getPaymentStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(payment.getVerifiedAt()).isNotNull();
        assertThat(attempt.getStatus()).isEqualTo(PaymentAttemptStatus.SUCCEEDED);
        assertThat(attempt.getProviderTransactionId()).isEqualTo("transaction-123");
    }

    @Test
    void invalidSignatureDoesNotReadOrChangePayment() {
        assertThatThrownBy(() -> service.processWebhook(webhook(150000L, "00", "transaction-123"), false))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("PayOS webhook signature is invalid");
        verifyNoInteractions(attempts, payments);
    }

    @Test
    void mismatchedAmountIsRejectedWithoutChangingPayment() {
        when(attempts.findByProviderOrderIdForUpdate("100000001")).thenReturn(Optional.of(attempt));

        assertThatThrownBy(() -> service.processWebhook(webhook(150001L, "00", "transaction-123"), true))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("PayOS webhook does not match the stored payment request");
        verifyNoInteractions(payments);
        assertThat(payment.getPaymentStatus()).isEqualTo(PaymentStatus.UNPAID);
    }

    @Test
    void mismatchedPaymentLinkIdIsRejected() {
        when(attempts.findByProviderOrderIdForUpdate("100000001")).thenReturn(Optional.of(attempt));

        assertThatThrownBy(() -> service.processWebhook(webhook(150000L, "00", "transaction-123", "other-link"), true))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("PayOS webhook payment-link ID does not match the stored request");
        verifyNoInteractions(payments);
    }

    @Test
    void repeatedSuccessfulWebhookIsIdempotent() {
        attempt.setStatus(PaymentAttemptStatus.SUCCEEDED);
        payment.setPaymentStatus(PaymentStatus.PAID);
        when(attempts.findByProviderOrderIdForUpdate("100000001")).thenReturn(Optional.of(attempt));

        service.processWebhook(webhook(150000L, "00", "transaction-123"), true);

        verifyNoInteractions(payments);
    }

    @Test
    void failedWebhookLeavesPaymentUnpaidAndAllowsLaterRetry() {
        when(attempts.findByProviderOrderIdForUpdate("100000001")).thenReturn(Optional.of(attempt));
        when(payments.findByBookingIdForUpdate(42L)).thenReturn(Optional.of(payment));

        service.processWebhook(webhook(150000L, "01", "transaction-123"), true);

        assertThat(attempt.getStatus()).isEqualTo(PaymentAttemptStatus.FAILED);
        assertThat(payment.getPaymentStatus()).isEqualTo(PaymentStatus.UNPAID);
        assertThat(payment.getVerifiedAt()).isNull();
    }

    private PayosWebhookRequest webhook(long amount, String transactionCode, String reference) {
        return webhook(amount, transactionCode, reference, "payment-link-1");
    }

    private PayosWebhookRequest webhook(long amount, String transactionCode, String reference, String linkId) {
        Map<String, Object> data = Map.of("orderCode", 100000001L, "amount", amount, "currency", "VND",
                "code", transactionCode, "paymentLinkId", linkId, "reference", reference);
        return new PayosWebhookRequest("00", "success", true, data, "signature");
    }
}
