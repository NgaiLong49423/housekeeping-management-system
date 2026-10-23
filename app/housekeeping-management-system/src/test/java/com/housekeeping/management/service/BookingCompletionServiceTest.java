package com.housekeeping.management.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.housekeeping.management.domain.BookingStatus;
import com.housekeeping.management.domain.PaymentStatus;
import com.housekeeping.management.domain.entity.Booking;
import com.housekeeping.management.domain.entity.BookingDetail;
import com.housekeeping.management.domain.entity.Payment;
import com.housekeeping.management.domain.entity.Staff;
import com.housekeeping.management.domain.entity.WorkSchedule;
import com.housekeeping.management.repository.BookingDetailRepository;
import com.housekeeping.management.repository.BookingRepository;
import com.housekeeping.management.repository.EvidencesRepository;
import com.housekeeping.management.repository.PaymentRepository;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class BookingCompletionServiceTest {
    @Test
    void assignedStaffCanCompleteConfirmedBookingBeforeItsScheduledStart() {
        BookingRepository bookings = mock(BookingRepository.class);
        BookingDetailRepository details = mock(BookingDetailRepository.class);
        EvidencesRepository evidences = mock(EvidencesRepository.class);
        PaymentRepository payments = mock(PaymentRepository.class);
        BookingCompletionService service = new BookingCompletionService(bookings, details, evidences, payments);

        Long bookingId = 42L;
        Long staffId = 7L;
        Long detailId = 84L;
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.ofHours(7)).withNano(0);

        Booking booking = new Booking();
        booking.setBookingId(bookingId);
        booking.setBookingStatus(BookingStatus.CONFIRMED);
        booking.setStartAt(now.plusDays(1));

        Staff staff = new Staff();
        staff.setStaffId(staffId);
        WorkSchedule schedule = new WorkSchedule();
        schedule.setStaff(staff);
        BookingDetail detail = new BookingDetail();
        detail.setBookingDetailId(detailId);
        detail.setBooking(booking);
        detail.setWorkSchedule(schedule);
        detail.setUnitPriceSnapshot(new BigDecimal("150000"));

        when(bookings.findByIdForUpdate(bookingId)).thenReturn(Optional.of(booking));
        when(details.findAllByBooking_BookingIdOrderBySequenceNo(bookingId)).thenReturn(List.of(detail));
        when(details.findByIdForUpdate(detailId)).thenReturn(Optional.of(detail));
        when(evidences.countByBookingDetail_BookingDetailId(detailId)).thenReturn(1L);
        when(payments.findByBooking_BookingId(bookingId)).thenReturn(Optional.empty());
        when(bookings.saveAndFlush(booking)).thenReturn(booking);
        when(payments.saveAndFlush(org.mockito.ArgumentMatchers.any(Payment.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Payment payment = service.completeBooking(bookingId, staffId, now);

        assertThat(booking.getBookingStatus()).isEqualTo(BookingStatus.COMPLETED);
        assertThat(payment.getPaymentStatus()).isEqualTo(PaymentStatus.UNPAID);
        assertThat(payment.getAmount()).isEqualByComparingTo("150000");
        assertThat(payment.getCreatedAt()).isEqualTo(now);
    }
}
