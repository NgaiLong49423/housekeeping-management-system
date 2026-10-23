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
import java.util.Map;
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
            throw new IllegalStateException("PayOS is only available for an unpaid completed booking");
        }
        if (payment.getAmount().signum() <= 0) {
            throw new IllegalStateException("PayOS requires a positive payment amount");
        }
        payment.setPaymentMethod(PaymentMethod.PAYOS);

        PaymentAttempt latest = attempts.findTopByPayment_PaymentIdOrderByCreatedAtDescAttemptIdDesc(payment.getPaymentId()).orElse(null);
        if (latest != null) {
            if (latest.getStatus() == PaymentAttemptStatus.INITIATING) {
                return new AttemptCheckout(latest.getAttemptId(), latest.getRequestId(),
                        Long.parseLong(latest.getProviderOrderId()), latest.getAmount().longValueExact(), null, true);
            }
            if (latest.getStatus() == PaymentAttemptStatus.PENDING) {
                if (latest.getCheckoutUrl() == null) throw new IllegalStateException("PayOS checkout is still pending. Please retry shortly.");
                return new AttemptCheckout(latest.getAttemptId(), latest.getRequestId(),
                        Long.parseLong(latest.getProviderOrderId()), latest.getAmount().longValueExact(), latest.getCheckoutUrl(), false);
            }
        }

        Long orderCode = attempts.nextPayosOrderCode();
        if (orderCode == null || orderCode < 1 || orderCode > Integer.MAX_VALUE) {
            throw new IllegalStateException("Could not allocate a PayOS order code");
        }
        String requestId = UUID.randomUUID().toString();
        OffsetDateTime now = OffsetDateTime.now(BUSINESS_OFFSET).withNano(0);
        PaymentAttempt attempt = new PaymentAttempt();
        attempt.setPayment(payment);
        attempt.setProviderOrderId(orderCode.toString());
        attempt.setRequestId(requestId);
        attempt.setAmount(payment.getAmount());
        attempt.setStatus(PaymentAttemptStatus.INITIATING);
        attempt.setCreatedAt(now);
        attempt.setUpdatedAt(now);
        attempts.saveAndFlush(attempt);
        return new AttemptCheckout(attempt.getAttemptId(), requestId, orderCode, payment.getAmount().longValueExact(), null, false);
    }

    @Transactional
    public void storeCheckout(Long attemptId, String checkoutUrl, String paymentLinkId) {
        PaymentAttempt attempt = attempts.findById(attemptId).orElseThrow();
        if (attempt.getStatus() != PaymentAttemptStatus.INITIATING) return;
        attempt.setCheckoutUrl(checkoutUrl);
        attempt.setProviderPaymentLinkId(paymentLinkId);
        attempt.setStatus(PaymentAttemptStatus.PENDING);
        attempt.setUpdatedAt(OffsetDateTime.now(BUSINESS_OFFSET).withNano(0));
    }

    @Transactional
    public void processWebhook(PayosWebhookRequest webhook, boolean signatureValid) {
        if (!signatureValid) throw new IllegalArgumentException("PayOS webhook signature is invalid");
        if (webhook == null || !Boolean.TRUE.equals(webhook.success()) || !"00".equals(webhook.code())
                || webhook.data() == null || !(webhook.data().get("orderCode") instanceof Number)
                || !(webhook.data().get("amount") instanceof Number)) {
            throw new IllegalArgumentException("PayOS webhook is missing required transaction data");
        }
        Map<String, Object> data = webhook.data();
        long orderCode = ((Number) data.get("orderCode")).longValue();
        long amount = ((Number) data.get("amount")).longValue();
        if (!"VND".equals(data.get("currency"))) {
            throw new IllegalArgumentException("PayOS webhook currency is invalid");
        }
        PaymentAttempt attempt = attempts.findByProviderOrderIdForUpdate(Long.toString(orderCode))
                .orElseThrow(() -> new IllegalArgumentException("PayOS payment attempt does not exist"));
        if (attempt.getAmount().compareTo(BigDecimal.valueOf(amount)) != 0) {
            throw new IllegalArgumentException("PayOS webhook does not match the stored payment request");
        }
        String paymentLinkId = value(data.get("paymentLinkId"));
        if (attempt.getProviderPaymentLinkId() != null && !attempt.getProviderPaymentLinkId().equals(paymentLinkId)) {
            throw new IllegalArgumentException("PayOS webhook payment-link ID does not match the stored request");
        }
        if (attempt.getStatus() == PaymentAttemptStatus.SUCCEEDED) return;

        Payment payment = payments.findByBookingIdForUpdate(attempt.getPayment().getBooking().getBookingId()).orElseThrow();
        if (payment.getPaymentMethod() != PaymentMethod.PAYOS) {
            throw new IllegalStateException("PayOS webhook does not belong to a PayOS payment");
        }
        if ("00".equals(data.get("code"))) {
            if (payment.getPaymentStatus() == PaymentStatus.PAID) {
                if (attempt.getStatus() == PaymentAttemptStatus.FAILED) return;
                throw new IllegalStateException("Payment was already completed through another transaction");
            }
            attempt.setStatus(PaymentAttemptStatus.SUCCEEDED);
            attempt.setProviderTransactionId(value(data.get("reference")));
            attempt.setProviderPaymentLinkId(paymentLinkId);
            payment.setPaymentStatus(PaymentStatus.PAID);
            payment.setVerifiedAt(OffsetDateTime.now(BUSINESS_OFFSET).withNano(0));
        } else {
            attempt.setStatus(PaymentAttemptStatus.FAILED);
        }
        attempt.setUpdatedAt(OffsetDateTime.now(BUSINESS_OFFSET).withNano(0));
    }

    @Transactional(readOnly = true)
    public Long bookingIdForOrder(long orderCode) {
        return attempts.findByProviderOrderId(Long.toString(orderCode))
                .map(attempt -> attempt.getPayment().getBooking().getBookingId())
                .orElseThrow(() -> new IllegalArgumentException("PayOS payment attempt does not exist"));
    }

    private static String value(Object value) { return value == null ? null : String.valueOf(value); }

    public record AttemptCheckout(Long attemptId, String requestId, long orderCode, long amount,
            String checkoutUrl, boolean needsRecovery) { }
}
