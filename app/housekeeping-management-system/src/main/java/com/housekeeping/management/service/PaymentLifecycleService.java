package com.housekeeping.management.service;

import com.housekeeping.management.domain.BookingStatus;
import com.housekeeping.management.domain.PaymentMethod;
import com.housekeeping.management.domain.PaymentStatus;
import com.housekeeping.management.domain.entity.Payment;
import com.housekeeping.management.repository.PaymentRepository;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PaymentLifecycleService {
    private static final ZoneOffset BUSINESS_OFFSET = ZoneOffset.ofHours(7);
    private final PaymentRepository paymentRepository;

    public PaymentLifecycleService(PaymentRepository paymentRepository) {
        this.paymentRepository = paymentRepository;
    }

    @Transactional
    public Payment chooseMethod(Long bookingId, PaymentMethod paymentMethod) {
        if (paymentMethod == null) {
            throw new IllegalArgumentException("A payment method must be selected");
        }
        Payment payment = paymentRepository.findByBookingIdForUpdate(bookingId)
                .orElseThrow(() -> new IllegalArgumentException("Payment does not exist"));
        if (payment.getBooking().getBookingStatus() != BookingStatus.COMPLETED
                || payment.getPaymentStatus() != PaymentStatus.UNPAID) {
            throw new IllegalStateException("A method can only be selected for an unpaid completed booking");
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
        payment.setPaymentStatus(PaymentStatus.PAID);
        payment.setVerifiedAt(verifiedAt.withOffsetSameInstant(BUSINESS_OFFSET).withNano(0));
        return paymentRepository.saveAndFlush(payment);
    }
}
