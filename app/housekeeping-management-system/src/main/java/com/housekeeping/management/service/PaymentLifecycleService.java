package com.housekeeping.management.service;

import com.housekeeping.management.domain.BookingStatus;
import com.housekeeping.management.domain.PaymentMethod;
import com.housekeeping.management.domain.PaymentStatus;
import com.housekeeping.management.domain.PaymentAttemptStatus;
import com.housekeeping.management.domain.entity.Payment;
import com.housekeeping.management.repository.PaymentRepository;
import com.housekeeping.management.repository.PaymentAttemptRepository;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PaymentLifecycleService {
    private static final ZoneOffset BUSINESS_OFFSET = ZoneOffset.ofHours(7);
    private final PaymentRepository paymentRepository;
    private final PaymentAttemptRepository attempts;

    public PaymentLifecycleService(PaymentRepository paymentRepository, PaymentAttemptRepository attempts) {
        this.paymentRepository = paymentRepository;
        this.attempts = attempts;
    }

    @Transactional
    public Payment chooseMethod(Long bookingId, PaymentMethod paymentMethod) {
        if (paymentMethod == null) {
            throw new IllegalArgumentException("A payment method must be selected");
        }
        if (paymentMethod == PaymentMethod.PAYOS || paymentMethod == PaymentMethod.MOMO) {
            throw new IllegalArgumentException("Use the PayOS checkout flow for online payments");
        }
        Payment payment = paymentRepository.findByBookingIdForUpdate(bookingId)
                .orElseThrow(() -> new IllegalArgumentException("Payment does not exist"));
        if (payment.getBooking().getBookingStatus() != BookingStatus.COMPLETED
                || payment.getPaymentStatus() != PaymentStatus.UNPAID) {
            throw new IllegalStateException("A method can only be selected for an unpaid completed booking");
        }
        if (payment.getPaymentMethod() == PaymentMethod.PAYOS || payment.getPaymentMethod() == PaymentMethod.MOMO
                || attempts.findTopByPayment_PaymentIdOrderByCreatedAtDescAttemptIdDesc(payment.getPaymentId())
                        .filter(attempt -> attempt.getStatus() == PaymentAttemptStatus.INITIATING
                                || attempt.getStatus() == PaymentAttemptStatus.PENDING)
                        .isPresent()) {
            throw new IllegalStateException("An online checkout has already started; complete or retry it before choosing a manual method");
        }
        payment.setPaymentMethod(paymentMethod);
        return paymentRepository.saveAndFlush(payment);
    }

    @Transactional
    public Payment confirmReceivedByAdmin(Long bookingId, boolean receiptVerified, OffsetDateTime verifiedAt) {
        if (!receiptVerified) {
            throw new IllegalArgumentException("Receipt must be verified before confirming payment");
        }
        Payment payment = paymentRepository.findByBookingIdForUpdate(bookingId)
                .orElseThrow(() -> new IllegalArgumentException("Payment does not exist"));
        if (payment.getPaymentStatus() != PaymentStatus.UNPAID || payment.getPaymentMethod() == null) {
            throw new IllegalStateException("Only an unpaid payment with a selected method can be confirmed");
        }
        if (payment.getPaymentMethod() == PaymentMethod.PAYOS || payment.getPaymentMethod() == PaymentMethod.MOMO) {
            throw new IllegalStateException("Online payments are confirmed automatically after a verified PayOS webhook");
        }
        payment.setPaymentStatus(PaymentStatus.PAID);
        payment.setVerifiedAt(verifiedAt.withOffsetSameInstant(BUSINESS_OFFSET).withNano(0));
        return paymentRepository.saveAndFlush(payment);
    }
}
