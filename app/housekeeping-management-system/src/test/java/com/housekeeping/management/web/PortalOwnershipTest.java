package com.housekeeping.management.web;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.housekeeping.management.dto.response.PortalViews;
import com.housekeeping.management.service.BookingCancellationService;
import com.housekeeping.management.service.PortalQueryService;
import com.housekeeping.management.repository.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;

class PortalOwnershipTest {
    @Test
    void roleAndIdMustBothMatchToReadBookingOrEvidence() {
        PortalQueryService query = new PortalQueryService(mock(JdbcTemplate.class), mock(FeedbackRepository.class));
        var now = OffsetDateTime.now();
        var card = new PortalViews.BookingCard(42L, 1L, 2L, "Customer", "Staff", now, now.plusHours(1), "CONFIRMED", BigDecimal.TEN,
                null, null, null, null, null);
        assertThatCode(() -> query.authorize(card, PortalMvcTest.account(1L, "CUSTOMER", "Owner"))).doesNotThrowAnyException();
        assertThatCode(() -> query.authorize(card, PortalMvcTest.account(2L, "STAFF", "Assigned"))).doesNotThrowAnyException();
        assertThatCode(() -> query.authorize(card, PortalMvcTest.account(3L, "ADMIN", "Admin"))).doesNotThrowAnyException();
        assertThatThrownBy(() -> query.authorize(card, PortalMvcTest.account(2L, "CUSTOMER", "Other customer"))).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> query.authorize(card, PortalMvcTest.account(1L, "STAFF", "Other staff"))).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void cancellationCannotChangeAnotherCustomersBookingOrCompletedBooking() {
        BookingPersistenceRepository persistence = mock(BookingPersistenceRepository.class);
        BookingCancellationService cancellation = new BookingCancellationService(persistence);
        var booking = new BookingPersistenceRepository.BookingState(42L, 1L, 2L, 3L,
                LocalDateTime.now().plusHours(3), LocalDateTime.now().plusHours(4), "CONFIRMED");
        when(persistence.lockBooking(42L)).thenReturn(Optional.of(booking));
        assertThatThrownBy(() -> cancellation.cancelByCustomer(42L, 9L)).isInstanceOf(AccessDeniedException.class);
        when(persistence.lockBooking(42L)).thenReturn(Optional.of(new BookingPersistenceRepository.BookingState(42L, 1L, 2L, 3L,
                LocalDateTime.now().plusHours(3), LocalDateTime.now().plusHours(4), "COMPLETED")));
        assertThatThrownBy(() -> cancellation.cancelByCustomer(42L, 1L)).isInstanceOf(IllegalStateException.class);
        when(persistence.lockBooking(42L)).thenReturn(Optional.of(new BookingPersistenceRepository.BookingState(42L, 1L, 2L, 3L,
                LocalDateTime.now().plusMinutes(119), LocalDateTime.now().plusHours(2), "CONFIRMED")));
        assertThatThrownBy(() -> cancellation.cancelByCustomer(42L, 1L)).isInstanceOf(IllegalStateException.class);
        when(persistence.lockBooking(42L)).thenReturn(Optional.of(booking));
        when(persistence.cancel(anyLong(), anyLong(), anyString(), any(LocalDateTime.class))).thenReturn(1);
        cancellation.cancelByCustomer(42L, 1L);
        verify(persistence).cancel(anyLong(), anyLong(), anyString(), any(LocalDateTime.class));
    }
}
