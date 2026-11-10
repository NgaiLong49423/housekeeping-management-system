package com.housekeeping.management.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.housekeeping.management.repository.BookingPersistenceRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import static org.assertj.core.api.Assertions.assertThat;

class BookingIncidentServiceTest {
    @Test
    void assignedStaffCanReportAnExpectedArrivalAndNoteIsTrimmed() {
        BookingPersistenceRepository bookings = mock(BookingPersistenceRepository.class);
        BookingIncidentService service = new com.housekeeping.management.service.impl.BookingIncidentServiceImpl(bookings);
        var booking = booking(7L, "CONFIRMED", LocalDateTime.now(ZoneOffset.ofHours(7)).plusDays(1), null, null, null);
        when(bookings.lockBookingWorkflow(42L)).thenReturn(Optional.of(booking));
        when(bookings.reportStaffIncident(eq(42L), eq(7L), eq("kẹt xe"), any(), any())).thenReturn(1);

        LocalDateTime eta = booking.startTime().plusMinutes(15);
        service.report(42L, 7L, "  kẹt xe  ", eta);

        verify(bookings).reportStaffIncident(eq(42L), eq(7L), eq("kẹt xe"), eq(eta), any());
    }

    @Test
    void canCompleteLegacyIncidentWithoutEtaByAddingEtaBeforeWorkStarts() {
        BookingPersistenceRepository bookings = mock(BookingPersistenceRepository.class);
        BookingIncidentService service = new com.housekeeping.management.service.impl.BookingIncidentServiceImpl(bookings);
        LocalDateTime start = LocalDateTime.now(ZoneOffset.ofHours(7)).plusMinutes(10).withNano(0);
        when(bookings.lockBookingWorkflow(42L)).thenReturn(Optional.of(booking(7L, "CONFIRMED", start,
                start.minusMinutes(1), null, null)));
        when(bookings.reportStaffIncident(eq(42L), eq(7L), eq("kẹt xe"), any(), any())).thenReturn(1);

        service.report(42L, 7L, "kẹt xe", start.plusMinutes(5));

        verify(bookings).reportStaffIncident(eq(42L), eq(7L), eq("kẹt xe"), eq(start.plusMinutes(5)), any());
    }

    @Test
    void rejectsUnassignedStaffAndInvalidEtaOrNote() {
        BookingPersistenceRepository bookings = mock(BookingPersistenceRepository.class);
        BookingIncidentService service = new com.housekeeping.management.service.impl.BookingIncidentServiceImpl(bookings);
        LocalDateTime start = LocalDateTime.now(ZoneOffset.ofHours(7)).plusDays(1);
        when(bookings.lockBookingWorkflow(42L)).thenReturn(Optional.of(booking(7L, "CONFIRMED", start, null, null, null)));

        assertThatThrownBy(() -> service.report(42L, 8L, "kẹt xe", start.plusMinutes(15)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.report(42L, 7L, "  ", start.plusMinutes(15)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.report(42L, 7L, "x".repeat(501), start.plusMinutes(15)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.report(42L, 7L, "kẹt xe", start.minusMinutes(1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsReportAfterWorkStartsAndDuplicateReport() {
        BookingPersistenceRepository bookings = mock(BookingPersistenceRepository.class);
        BookingIncidentService service = new com.housekeeping.management.service.impl.BookingIncidentServiceImpl(bookings);
        LocalDateTime start = LocalDateTime.now(ZoneOffset.ofHours(7)).plusDays(1);
        when(bookings.lockBookingWorkflow(42L))
                .thenReturn(Optional.of(booking(7L, "CONFIRMED", start, null, start, null)))
                .thenReturn(Optional.of(booking(7L, "CONFIRMED", start, null, null, start.plusMinutes(15))));

        assertThatThrownBy(() -> service.report(42L, 7L, "kẹt xe", start.plusMinutes(15)))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> service.report(42L, 7L, "kẹt xe", start.plusMinutes(15)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void customerAcceptanceStoresResponseAndCalculatedDiscount() {
        BookingPersistenceRepository bookings = mock(BookingPersistenceRepository.class);
        BookingIncidentService service = new com.housekeeping.management.service.impl.BookingIncidentServiceImpl(bookings);
        LocalDateTime start = LocalDateTime.now(ZoneOffset.ofHours(7)).minusMinutes(20).withNano(0);
        when(bookings.lockBookingWorkflow(42L)).thenReturn(Optional.of(booking(7L, "CONFIRMED", start,
                start.plusMinutes(20), null, start.plusMinutes(25))));
        when(bookings.respondToIncident(eq(42L), eq(3L), eq("ACCEPTED"), any(), any(), any())).thenReturn(1);

        service.respond(42L, 3L, "ACCEPTED");

        ArgumentCaptor<BigDecimal> percent = ArgumentCaptor.forClass(BigDecimal.class);
        ArgumentCaptor<BigDecimal> amount = ArgumentCaptor.forClass(BigDecimal.class);
        verify(bookings).respondToIncident(eq(42L), eq(3L), eq("ACCEPTED"), any(), percent.capture(), amount.capture());
        assertThat(percent.getValue()).isEqualByComparingTo("10");
        assertThat(amount.getValue()).isEqualByComparingTo("15000.00");
    }

    @Test
    void declineCancelsWithoutAnyDiscountAndNonOwnerCannotRespond() {
        BookingPersistenceRepository bookings = mock(BookingPersistenceRepository.class);
        BookingIncidentService service = new com.housekeeping.management.service.impl.BookingIncidentServiceImpl(bookings);
        LocalDateTime start = LocalDateTime.now(ZoneOffset.ofHours(7)).plusMinutes(10).withNano(0);
        when(bookings.lockBookingWorkflow(42L)).thenReturn(Optional.of(booking(7L, "CONFIRMED", start, null, null, start.plusMinutes(15))))
                .thenReturn(Optional.of(new BookingPersistenceRepository.BookingWorkflowState(42L, 8L, 7L,
                        start, start.plusHours(1), "CONFIRMED", new BigDecimal("150000.00"), null, null,
                        start.minusMinutes(2), "kẹt xe", start.plusMinutes(15), null, null, null,
                        BigDecimal.ZERO, BigDecimal.ZERO)));
        when(bookings.respondToIncident(eq(42L), eq(3L), eq("DECLINED"), any(), any(), any())).thenReturn(1);

        service.respond(42L, 3L, "DECLINED");
        verify(bookings).respondToIncident(eq(42L), eq(3L), eq("DECLINED"), any(), eq(BigDecimal.ZERO), eq(new BigDecimal("0.00")));
        assertThatThrownBy(() -> service.respond(42L, 3L, "ACCEPTED"))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
    }

    private BookingPersistenceRepository.BookingWorkflowState booking(Long staffId, String status, LocalDateTime start,
            LocalDateTime staffArrivedAt, LocalDateTime workStartedAt, LocalDateTime expectedArrivalAt) {
        boolean reported = expectedArrivalAt != null || staffArrivedAt != null || workStartedAt != null;
        return new BookingPersistenceRepository.BookingWorkflowState(42L, 3L, staffId, start, start.plusHours(1), status,
                new BigDecimal("150000.00"), staffArrivedAt, workStartedAt,
                reported ? start.minusMinutes(2) : null, reported ? "kẹt xe" : null, expectedArrivalAt,
                null, null, null, BigDecimal.ZERO, BigDecimal.ZERO);
    }
}
