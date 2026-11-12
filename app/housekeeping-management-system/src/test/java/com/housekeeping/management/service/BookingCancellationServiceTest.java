package com.housekeeping.management.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.housekeeping.management.entity.Booking;
import com.housekeeping.management.entity.CancellationLog;
import com.housekeeping.management.enums.BookingStatus;
import com.housekeeping.management.repository.BookingPersistenceRepository;
import com.housekeeping.management.repository.CancellationLogRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import org.mockito.ArgumentCaptor;
import org.junit.jupiter.api.Test;

class BookingCancellationServiceTest {
    @Test
    void ordinaryCancellationIncludesExactTwoHourBoundaryOnly() {
        LocalDateTime start = LocalDateTime.parse("2026-11-01T12:00:00");
        assertThat(BookingCancellationService.canCustomerCancel("CONFIRMED", start, start.minusHours(2), null)).isTrue();
        assertThat(BookingCancellationService.canCustomerCancel("CONFIRMED", start, start.minusMinutes(119), null)).isFalse();
        assertThat(BookingCancellationService.canCustomerCancel("AWAITING_PAYMENT", start, start.minusHours(3), null)).isFalse();
        assertThat(BookingCancellationService.canCustomerCancel("CONFIRMED", start, start.minusHours(3), start.minusHours(4))).isFalse();
    }

    @Test
    void customerDelayReportAloneDoesNotAllowCancellationInsideTwoHours() {
        BookingPersistenceRepository bookings = mock(BookingPersistenceRepository.class);
        CancellationLogRepository logs = mock(CancellationLogRepository.class);
        LocalDateTime now = LocalDateTime.now(ZoneOffset.ofHours(7)).withNano(0);
        Booking booking = booking(3L, now.plusMinutes(30));
        booking.setCustomerDelayReportedAt(now.minusMinutes(1));
        when(bookings.lockEntity(42L)).thenReturn(Optional.of(booking));

        assertThatThrownBy(() -> service(bookings, logs).cancelByCustomer(42L, 3L))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("2 giờ");
        verify(bookings, never()).save(any(Booking.class));
        verify(logs, never()).save(any());
    }

    @Test
    void acceptedLateEtaAllowsFreeCancellationBeforeWorkStarts() {
        BookingPersistenceRepository bookings = mock(BookingPersistenceRepository.class);
        CancellationLogRepository logs = mock(CancellationLogRepository.class);
        LocalDateTime now = LocalDateTime.now(ZoneOffset.ofHours(7)).withNano(0);
        Booking booking = booking(3L, now.plusMinutes(30));
        booking.setIncidentReportedAt(now.minusMinutes(1));
        booking.setIncidentExpectedArrivalAt(booking.getStartTime().plusMinutes(20));
        booking.setIncidentCustomerResponse("ACCEPTED");
        when(bookings.lockEntity(42L)).thenReturn(Optional.of(booking));

        service(bookings, logs).cancelByCustomer(42L, 3L);

        assertCancelledWithoutFinancialEffects(booking, bookings, logs);
    }

    @Test
    void adminVerifiedNoShowAllowsFreeCancellation() {
        BookingPersistenceRepository bookings = mock(BookingPersistenceRepository.class);
        CancellationLogRepository logs = mock(CancellationLogRepository.class);
        LocalDateTime now = LocalDateTime.now(ZoneOffset.ofHours(7)).withNano(0);
        Booking booking = booking(3L, now.plusMinutes(15));
        booking.setCustomerDelayReportedAt(now.minusMinutes(2));
        booking.setAdminNoShowVerifiedAt(now.minusMinutes(1));
        booking.setAdminNoShowVerifiedBy(99L);
        booking.setAdminNoShowNote("Đã gọi xác minh");
        when(bookings.lockEntity(42L)).thenReturn(Optional.of(booking));

        service(bookings, logs).cancelByCustomer(42L, 3L);

        assertCancelledWithoutFinancialEffects(booking, bookings, logs);
    }

    @Test
    void adminVerifiedLateArrivalAllowsFreeCancellationButWorkStartDoesNot() {
        BookingPersistenceRepository bookings = mock(BookingPersistenceRepository.class);
        CancellationLogRepository logs = mock(CancellationLogRepository.class);
        LocalDateTime now = LocalDateTime.now(ZoneOffset.ofHours(7)).withNano(0);
        Booking booking = booking(3L, now.plusMinutes(15));
        booking.setCustomerDelayReportedAt(now.minusMinutes(2));
        booking.setArrivalVerifiedAt(booking.getStartTime().plusMinutes(5));
        booking.setArrivalVerificationSource("ADMIN");
        when(bookings.lockEntity(42L)).thenReturn(Optional.of(booking));

        service(bookings, logs).cancelByCustomer(42L, 3L);
        assertCancelledWithoutFinancialEffects(booking, bookings, logs);

        Booking started = booking(3L, now.plusMinutes(15));
        started.setWorkStartedAt(now.minusMinutes(1));
        when(bookings.lockEntity(43L)).thenReturn(Optional.of(started));
        assertThatThrownBy(() -> service(bookings, logs).cancelByCustomer(43L, 3L))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("đã bắt đầu");
    }

    @Test
    void rejectsNonOwnerAndCancellationInsideOrdinaryWindow() {
        BookingPersistenceRepository bookings = mock(BookingPersistenceRepository.class);
        CancellationLogRepository logs = mock(CancellationLogRepository.class);
        LocalDateTime now = LocalDateTime.now(ZoneOffset.ofHours(7)).withNano(0);
        when(bookings.lockEntity(42L)).thenReturn(Optional.of(booking(3L, now.plusHours(3))));
        assertThatThrownBy(() -> service(bookings, logs).cancelByCustomer(42L, 4L))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);

        when(bookings.lockEntity(43L)).thenReturn(Optional.of(booking(3L, now.plusMinutes(119))));
        assertThatThrownBy(() -> service(bookings, logs).cancelByCustomer(43L, 3L))
                .isInstanceOf(IllegalStateException.class);
    }

    private static com.housekeeping.management.service.impl.BookingCancellationServiceImpl service(
            BookingPersistenceRepository bookings, CancellationLogRepository logs) {
        return new com.housekeeping.management.service.impl.BookingCancellationServiceImpl(bookings, logs);
    }

    private static Booking booking(Long customerId, LocalDateTime start) {
        Booking booking = new Booking();
        booking.setBookingId(42L);
        booking.setCustomerId(customerId);
        booking.setStaffId(7L);
        booking.setStatus(BookingStatus.CONFIRMED);
        booking.setStartTime(start);
        booking.setTotalAmount(new BigDecimal("150000"));
        booking.setCancellationFee(new BigDecimal("50000"));
        booking.setRefundAmount(new BigDecimal("100000"));
        return booking;
    }

    private static void assertCancelledWithoutFinancialEffects(Booking booking,
            BookingPersistenceRepository bookings, CancellationLogRepository logs) {
        assertThat(booking.getStatus()).isEqualTo(BookingStatus.CANCELLED);
        assertThat(booking.getCancellationFee()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(booking.getRefundAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(booking.getDelayMinutes()).isZero();
        assertThat(booking.getDelayDiscountPercent()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(booking.getDelayDiscountAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        verify(bookings).save(booking);
        ArgumentCaptor<CancellationLog> cancellationLog = ArgumentCaptor.forClass(CancellationLog.class);
        verify(logs).save(cancellationLog.capture());
        assertThat(cancellationLog.getValue().getBookingTotalAmount()).isEqualByComparingTo("150000");
        assertThat(cancellationLog.getValue().getCancellationFee()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(cancellationLog.getValue().getWorkerCompensation()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(cancellationLog.getValue().getPlatformFee()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(cancellationLog.getValue().getRefundAmount()).isEqualByComparingTo(BigDecimal.ZERO);
    }
}
