package com.housekeeping.management.service;

import com.housekeeping.management.domain.BookingStatus;
import com.housekeeping.management.domain.PaymentAttemptStatus;
import com.housekeeping.management.domain.PaymentMethod;
import com.housekeeping.management.domain.PaymentStatus;
import com.housekeeping.management.domain.entity.Payment;
import com.housekeeping.management.domain.entity.PaymentAttempt;
import com.housekeeping.management.repository.PaymentAttemptRepository;
import com.housekeeping.management.repository.PaymentRepository;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PaymentAttemptService {
    private static final ZoneOffset BUSINESS_OFFSET = ZoneOffset.ofHours(7);
    private final PaymentRepository payments;
    private final PaymentAttemptRepository attempts;

    public PaymentAttemptService(PaymentRepository payments, PaymentAttemptRepository attempts) {
        this.payments = payments;
        this.attempts = attempts;
    }

    @Transactional
    public AttemptCheckout prepare(Long bookingId, Long customerId) {
        Payment payment = payments.findByBookingIdForUpdate(bookingId)
                .orElseThrow(() -> new IllegalArgumentException("Payment does not exist"));
        if (!payment.getBooking().getCustomer().getCustomerId().equals(customerId)) {
            throw new AccessDeniedException("Customer does not own this booking");
        }
        if (payment.getBooking().getBookingStatus() != BookingStatus.COMPLETED
                || payment.getPaymentStatus() != PaymentStatus.UNPAID) {
            throw new IllegalStateException("MoMo is only available for an unpaid completed booking");
        }
        if (payment.getAmount().compareTo(BigDecimal.valueOf(1000)) < 0) {
            throw new IllegalStateException("MoMo requires a payment of at least 1,000 VND");
        }
        payment.setPaymentMethod(PaymentMethod.MOMO);

        PaymentAttempt latest = attempts.findTopByPayment_PaymentIdOrderByCreatedAtDescAttemptIdDesc(payment.getPaymentId()).orElse(null);
        if (latest != null) {
            if (latest.getStatus() == PaymentAttemptStatus.INITIATING) {
                return new AttemptCheckout(latest.getAttemptId(), latest.getRequestId(), latest.getProviderOrderId(), latest.getAmount().longValueExact(), null);
            }
            if (latest.getStatus() == PaymentAttemptStatus.PENDING) {
                if (latest.getCheckoutUrl() == null) throw new IllegalStateException("MoMo checkout is still pending. Please retry shortly.");
                return new AttemptCheckout(latest.getAttemptId(), latest.getRequestId(), latest.getProviderOrderId(), latest.getAmount().longValueExact(), latest.getCheckoutUrl());
            }
        }

        String orderId = "GN" + UUID.randomUUID().toString().replace("-", "");
        String requestId = UUID.randomUUID().toString();
        OffsetDateTime now = OffsetDateTime.now(BUSINESS_OFFSET).withNano(0);
        PaymentAttempt attempt = new PaymentAttempt();
        attempt.setPayment(payment);
        attempt.setProviderOrderId(orderId);
        attempt.setRequestId(requestId);
        attempt.setAmount(payment.getAmount());
        attempt.setStatus(PaymentAttemptStatus.INITIATING);
        attempt.setCreatedAt(now);
        attempt.setUpdatedAt(now);
        attempts.saveAndFlush(attempt);
        return new AttemptCheckout(attempt.getAttemptId(), requestId, orderId, payment.getAmount().longValueExact(), null);
    }

    @Transactional
    public void storeCheckoutUrl(Long attemptId, String checkoutUrl) {
        PaymentAttempt attempt = attempts.findById(attemptId).orElseThrow();
        if (attempt.getStatus() != PaymentAttemptStatus.INITIATING) return;
        attempt.setCheckoutUrl(checkoutUrl);
        attempt.setStatus(PaymentAttemptStatus.PENDING);
        attempt.setUpdatedAt(OffsetDateTime.now(BUSINESS_OFFSET).withNano(0));
    }

    @Transactional
    public void processIpn(MomoIpnRequest ipn, boolean signatureValid, String partnerCode) {
        if (!signatureValid) throw new IllegalArgumentException("MoMo IPN signature is invalid");
        if (ipn.orderId() == null || ipn.amount() == null || ipn.resultCode() == null
                || !partnerCode.equals(ipn.partnerCode())) {
            throw new IllegalArgumentException("MoMo IPN is missing required transaction data");
        }
        PaymentAttempt attempt = attempts.findByProviderOrderIdForUpdate(ipn.orderId())
                .orElseThrow(() -> new IllegalArgumentException("MoMo payment attempt does not exist"));
        if (!attempt.getRequestId().equals(ipn.requestId())
                || attempt.getAmount().compareTo(BigDecimal.valueOf(ipn.amount())) != 0) {
            throw new IllegalArgumentException("MoMo IPN does not match the stored payment request");
        }
        if (attempt.getStatus() == PaymentAttemptStatus.SUCCEEDED) return;

        Payment payment = payments.findByBookingIdForUpdate(attempt.getPayment().getBooking().getBookingId()).orElseThrow();
        if (payment.getPaymentMethod() != PaymentMethod.MOMO) {
            throw new IllegalStateException("MoMo callback does not belong to a MoMo payment");
        }
        if (ipn.resultCode() == 0) {
            if (payment.getPaymentStatus() == PaymentStatus.PAID) {
                if (attempt.getStatus() == PaymentAttemptStatus.FAILED) return;
                throw new IllegalStateException("Payment was already completed through another transaction");
            }
            attempt.setStatus(PaymentAttemptStatus.SUCCEEDED);
            attempt.setProviderTransactionId(ipn.transId());
            payment.setPaymentStatus(PaymentStatus.PAID);
            payment.setVerifiedAt(OffsetDateTime.now(BUSINESS_OFFSET).withNano(0));
        } else {
            attempt.setStatus(PaymentAttemptStatus.FAILED);
        }
        attempt.setUpdatedAt(OffsetDateTime.now(BUSINESS_OFFSET).withNano(0));
    }

    @Transactional(readOnly = true)
    public Long bookingIdForOrder(String orderId) {
        return attempts.findByProviderOrderId(orderId)
                .map(attempt -> attempt.getPayment().getBooking().getBookingId())
                .orElseThrow(() -> new IllegalArgumentException("MoMo payment attempt does not exist"));
    }

    public record AttemptCheckout(Long attemptId, String requestId, String orderId, long amount, String checkoutUrl) { }
}
