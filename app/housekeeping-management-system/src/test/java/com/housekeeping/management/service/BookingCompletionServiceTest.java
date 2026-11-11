package com.housekeeping.management.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.housekeeping.management.repository.BookingImageRepository;
import com.housekeeping.management.repository.BookingPersistenceRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class BookingCompletionServiceTest {
    private final LocalDateTime start = LocalDateTime.parse("2026-11-02T10:00:00");

    @Test
    void assignedStaffCanStartOnTimeAfterArrival() {
        BookingPersistenceRepository bookings = mock(BookingPersistenceRepository.class);
        var service = new com.housekeeping.management.service.impl.BookingCompletionServiceImpl(bookings, mock(BookingImageRepository.class));
        when(bookings.lockBookingWorkflow(42L)).thenReturn(Optional.of(workflow(7L, "CONFIRMED", start,
                start, null, null, null)));
        when(bookings.startWork(eq(42L), eq(7L), any())).thenReturn(1);

        service.startBooking(42L, 7L);

        verify(bookings).startWork(eq(42L), eq(7L), any());
    }

    @Test
    void lateArrivalCanStartWithoutOptionalIncidentReport() {
        BookingPersistenceRepository bookings = mock(BookingPersistenceRepository.class);
        var service = new com.housekeeping.management.service.impl.BookingCompletionServiceImpl(bookings, mock(BookingImageRepository.class));
        when(bookings.lockBookingWorkflow(42L)).thenReturn(Optional.of(workflow(7L, "CONFIRMED", start,
                start.plusMinutes(1), null, null, null)));
        when(bookings.startWork(eq(42L), eq(7L), any())).thenReturn(1);

        service.startBooking(42L, 7L);

        verify(bookings).startWork(eq(42L), eq(7L), any());
    }

    @Test
    void assignedStaffMayStartEarlyAfterConfirmingArrival() {
        BookingPersistenceRepository bookings = mock(BookingPersistenceRepository.class);
        var service = new com.housekeeping.management.service.impl.BookingCompletionServiceImpl(bookings, mock(BookingImageRepository.class));
        LocalDateTime futureStart = LocalDateTime.now(java.time.ZoneOffset.ofHours(7)).plusHours(2).withNano(0);
        LocalDateTime arrivedAt = LocalDateTime.now(java.time.ZoneOffset.ofHours(7)).withNano(0);
        when(bookings.lockBookingWorkflow(42L)).thenReturn(Optional.of(workflow(7L, "CONFIRMED", futureStart,
                arrivedAt, null, null, null)));
        when(bookings.startWork(eq(42L), eq(7L), any())).thenReturn(1);

        service.startBooking(42L, 7L);

        verify(bookings).startWork(eq(42L), eq(7L), any());
    }

    @Test
    void reportedDelayMustBeAcceptedBeforeWorkStarts() {
        BookingPersistenceRepository bookings = mock(BookingPersistenceRepository.class);
        var service = new com.housekeeping.management.service.impl.BookingCompletionServiceImpl(bookings, mock(BookingImageRepository.class));
        when(bookings.lockBookingWorkflow(42L)).thenReturn(Optional.of(workflow(7L, "CONFIRMED", start,
                start, null, start.minusMinutes(5), null)));

        assertThatThrownBy(() -> service.startBooking(42L, 7L))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("Customer");
        verify(bookings, never()).startWork(any(), any(), any());
    }

    @Test
    void wrongStaffCannotStartAndStartBeforeArrivalIsRejected() {
        BookingPersistenceRepository bookings = mock(BookingPersistenceRepository.class);
        var service = new com.housekeeping.management.service.impl.BookingCompletionServiceImpl(bookings, mock(BookingImageRepository.class));
        when(bookings.lockBookingWorkflow(42L)).thenReturn(Optional.of(workflow(7L, "CONFIRMED", start,
                start, null, null, null))).thenReturn(Optional.of(workflow(7L, "CONFIRMED", start,
                null, null, null, null)));

        assertThatThrownBy(() -> service.startBooking(42L, 8L)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.startBooking(42L, 7L)).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("xác nhận đã đến");
        verify(bookings, never()).startWork(any(), any(), any());
    }

    @Test
    void startedWorkNeedsEvidenceBeforeMovingToAwaitingPayment() {
        BookingPersistenceRepository bookings = mock(BookingPersistenceRepository.class);
        BookingImageRepository images = mock(BookingImageRepository.class);
        var service = new com.housekeeping.management.service.impl.BookingCompletionServiceImpl(bookings, images);
        when(bookings.lockBookingWorkflow(42L)).thenReturn(Optional.of(workflow(7L, "CONFIRMED", start,
                start, start.plusMinutes(1), null, null)));
        when(images.existsByBookingId(42L)).thenReturn(true);
        when(bookings.changeStatus(42L, "CONFIRMED", "AWAITING_PAYMENT")).thenReturn(1);

        service.completeBooking(42L, 7L);

        verify(bookings).changeStatus(42L, "CONFIRMED", "AWAITING_PAYMENT");
    }

    @Test
    void cannotCompleteBeforeStartOrWithoutEvidence() {
        BookingPersistenceRepository bookings = mock(BookingPersistenceRepository.class);
        BookingImageRepository images = mock(BookingImageRepository.class);
        var service = new com.housekeeping.management.service.impl.BookingCompletionServiceImpl(bookings, images);
        when(bookings.lockBookingWorkflow(42L)).thenReturn(Optional.of(workflow(7L, "CONFIRMED", start,
                start, null, null, null))).thenReturn(Optional.of(workflow(7L, "CONFIRMED", start,
                start, start.plusMinutes(1), null, null)));

        assertThatThrownBy(() -> service.completeBooking(42L, 7L))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("bắt đầu công việc");
        when(images.existsByBookingId(42L)).thenReturn(false);
        assertThatThrownBy(() -> service.completeBooking(42L, 7L))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("ảnh minh chứng");
        verify(bookings, never()).changeStatus(any(), any(), any());
    }

    private BookingPersistenceRepository.BookingWorkflowState workflow(Long staffId, String status, LocalDateTime start,
            LocalDateTime arrivedAt, LocalDateTime workStartedAt, LocalDateTime incidentReportedAt, String response) {
        return new BookingPersistenceRepository.BookingWorkflowState(42L, 3L, staffId, start, start.plusHours(1),
                status, new BigDecimal("150000.00"), arrivedAt, workStartedAt, incidentReportedAt,
                incidentReportedAt == null ? null : "kẹt xe",
                incidentReportedAt == null ? null : incidentReportedAt.plusMinutes(30), response,
                response == null ? null : incidentReportedAt.plusMinutes(1), null, BigDecimal.ZERO, BigDecimal.ZERO);
    }
}
