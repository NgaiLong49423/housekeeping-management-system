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
import java.time.OffsetDateTime;
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
        payment.setPaymentMethod(PaymentMethod.MOMO);
        payment.setAmount(new BigDecimal("150000"));
        attempt = new PaymentAttempt();
        attempt.setAttemptId(19L);
        attempt.setPayment(payment);
        attempt.setProviderOrderId("GN-order-1");
        attempt.setRequestId("request-1");
        attempt.setAmount(new BigDecimal("150000"));
        attempt.setStatus(PaymentAttemptStatus.PENDING);
    }

    @Test
    void verifiedSuccessfulIpnMarksPaymentPaidAndStoresProviderTransaction() {
        when(attempts.findByProviderOrderIdForUpdate("GN-order-1")).thenReturn(Optional.of(attempt));
        when(payments.findByBookingIdForUpdate(42L)).thenReturn(Optional.of(payment));

        service.processIpn(ipn(150000L, 0, 123456789L), true, "PARTNER");

        assertThat(payment.getPaymentMethod()).isEqualTo(PaymentMethod.MOMO);
        assertThat(payment.getPaymentStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(payment.getVerifiedAt()).isNotNull();
        assertThat(attempt.getStatus()).isEqualTo(PaymentAttemptStatus.SUCCEEDED);
        assertThat(attempt.getProviderTransactionId()).isEqualTo(123456789L);
    }

    @Test
    void invalidSignatureDoesNotReadOrChangePayment() {
        assertThatThrownBy(() -> service.processIpn(ipn(150000L, 0, 123456789L), false, "PARTNER"))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("MoMo IPN signature is invalid");
        verifyNoInteractions(attempts, payments);
    }

    @Test
    void mismatchedAmountIsRejectedWithoutChangingPayment() {
        when(attempts.findByProviderOrderIdForUpdate("GN-order-1")).thenReturn(Optional.of(attempt));

        assertThatThrownBy(() -> service.processIpn(ipn(150001L, 0, 123456789L), true, "PARTNER"))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("MoMo IPN does not match the stored payment request");
        verifyNoInteractions(payments);
        assertThat(payment.getPaymentStatus()).isEqualTo(PaymentStatus.UNPAID);
    }

    @Test
    void repeatedSuccessfulIpnIsIdempotent() {
        attempt.setStatus(PaymentAttemptStatus.SUCCEEDED);
        when(attempts.findByProviderOrderIdForUpdate("GN-order-1")).thenReturn(Optional.of(attempt));

        service.processIpn(ipn(150000L, 0, 123456789L), true, "PARTNER");

        verifyNoInteractions(payments);
        assertThat(payment.getPaymentStatus()).isEqualTo(PaymentStatus.UNPAID);
    }

    @Test
    void failedIpnLeavesPaymentUnpaidAndAllowsLaterRetry() {
        when(attempts.findByProviderOrderIdForUpdate("GN-order-1")).thenReturn(Optional.of(attempt));
        when(payments.findByBookingIdForUpdate(42L)).thenReturn(Optional.of(payment));

        service.processIpn(ipn(150000L, 1006, null), true, "PARTNER");

        assertThat(attempt.getStatus()).isEqualTo(PaymentAttemptStatus.FAILED);
        assertThat(payment.getPaymentStatus()).isEqualTo(PaymentStatus.UNPAID);
        assertThat(payment.getVerifiedAt()).isNull();
    }

    private MomoIpnRequest ipn(long amount, int resultCode, Long transactionId) {
        return new MomoIpnRequest("PARTNER", "request-1", "GN-order-1", amount, "Payment test",
                "momo_wallet", "", transactionId, resultCode, resultCode == 0 ? "Successful." : "Failed",
                "qr", OffsetDateTime.now().toInstant().toEpochMilli(), "", "signature");
    }
}
