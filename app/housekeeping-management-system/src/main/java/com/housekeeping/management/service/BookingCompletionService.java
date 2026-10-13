package com.housekeeping.management.service;

import com.housekeeping.management.domain.BookingStatus;
import com.housekeeping.management.domain.PaymentStatus;
import com.housekeeping.management.domain.entity.Booking;
import com.housekeeping.management.domain.entity.BookingDetail;
import com.housekeeping.management.domain.entity.Payment;
import com.housekeeping.management.domain.entity.WorkSchedule;
import com.housekeeping.management.repository.BookingDetailRepository;
import com.housekeeping.management.repository.BookingRepository;
import com.housekeeping.management.repository.EvidencesRepository;
import com.housekeeping.management.repository.PaymentRepository;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BookingCompletionService {
    private static final int MIN_EVIDENCES_PER_DETAIL = 1;
    private static final int MAX_EVIDENCES_PER_DETAIL = 5;
    private static final ZoneOffset BUSINESS_OFFSET = ZoneOffset.ofHours(7);

    private final BookingRepository bookingRepository;
    private final BookingDetailRepository bookingDetailRepository;
    private final EvidencesRepository evidencesRepository;
    private final PaymentRepository paymentRepository;

    public BookingCompletionService(
            BookingRepository bookingRepository,
            BookingDetailRepository bookingDetailRepository,
            EvidencesRepository evidencesRepository,
            PaymentRepository paymentRepository) {
        this.bookingRepository = bookingRepository;
        this.bookingDetailRepository = bookingDetailRepository;
        this.evidencesRepository = evidencesRepository;
        this.paymentRepository = paymentRepository;
    }

    @Transactional
    public Payment completeBooking(Long bookingId, Long staffId, OffsetDateTime completedAt) {
        Booking booking = bookingRepository.findByIdForUpdate(bookingId)
                .orElseThrow(() -> new IllegalArgumentException("Booking does not exist"));
        if (booking.getBookingStatus() != BookingStatus.CONFIRMED) {
            throw new IllegalStateException("Only a confirmed booking can be completed");
        }

        OffsetDateTime verifiedNow = completedAt.withOffsetSameInstant(BUSINESS_OFFSET).withNano(0);
        if (verifiedNow.isBefore(booking.getStartAt())) {
            throw new IllegalStateException("A booking cannot be completed before its start time");
        }

        List<BookingDetail> details = bookingDetailRepository
                .findAllByBooking_BookingIdOrderBySequenceNo(bookingId);
        if (details.isEmpty()) {
            throw new IllegalStateException("A booking without details cannot be completed");
        }

        BigDecimal amount = BigDecimal.ZERO;
        for (BookingDetail detail : details) {
            BookingDetail lockedDetail = bookingDetailRepository.findByIdForUpdate(detail.getBookingDetailId())
                    .orElseThrow(() -> new IllegalStateException("Booking detail disappeared"));
            WorkSchedule schedule = lockedDetail.getWorkSchedule();
            if (!schedule.getStaff().getStaffId().equals(staffId)) {
                throw new IllegalArgumentException("Only the selected Staff can complete this booking");
            }
            long evidenceCount = evidencesRepository.countByBookingDetail_BookingDetailId(
                    lockedDetail.getBookingDetailId());
            if (evidenceCount < MIN_EVIDENCES_PER_DETAIL || evidenceCount > MAX_EVIDENCES_PER_DETAIL) {
                throw new IllegalStateException("Every detail must have between one and five valid evidences");
            }
            amount = amount.add(lockedDetail.getUnitPriceSnapshot());
        }

        if (paymentRepository.findByBooking_BookingId(bookingId).isPresent()) {
            throw new IllegalStateException("Payment already exists for this booking");
        }

        booking.setBookingStatus(BookingStatus.COMPLETED);
        bookingRepository.saveAndFlush(booking);

        Payment payment = new Payment();
        payment.setBooking(booking);
        payment.setPaymentMethod(null);
        payment.setPaymentStatus(PaymentStatus.UNPAID);
        payment.setAmount(amount);
        payment.setCreatedAt(verifiedNow);
        payment.setVerifiedAt(null);
        return paymentRepository.saveAndFlush(payment);
    }
}
