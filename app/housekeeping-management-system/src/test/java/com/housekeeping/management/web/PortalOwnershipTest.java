package com.housekeeping.management.web;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.housekeeping.management.domain.BookingStatus;
import com.housekeeping.management.domain.entity.*;
import com.housekeeping.management.repository.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;

class PortalOwnershipTest {
    @Test
    void roleAndIdMustBothMatchToReadBookingOrEvidence() {
        PortalQueryService query = new PortalQueryService(mock(JdbcTemplate.class));
        var now = OffsetDateTime.now();
        var card = new PortalViews.BookingCard(42L, 1L, 2L, "Customer", "Staff", now, now.plusHours(1), "CONFIRMED", BigDecimal.TEN, null, null, null);
        assertThatCode(() -> query.authorize(card, PortalMvcTest.account(1L, "CUSTOMER", "Owner"))).doesNotThrowAnyException();
        assertThatCode(() -> query.authorize(card, PortalMvcTest.account(2L, "STAFF", "Assigned"))).doesNotThrowAnyException();
        assertThatCode(() -> query.authorize(card, PortalMvcTest.account(3L, "ADMIN", "Admin"))).doesNotThrowAnyException();
        assertThatThrownBy(() -> query.authorize(card, PortalMvcTest.account(2L, "CUSTOMER", "Other customer"))).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> query.authorize(card, PortalMvcTest.account(1L, "STAFF", "Other staff"))).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void cancellationCannotChangeAnotherCustomersBookingOrCompletedBooking() {
        BookingRepository repository = mock(BookingRepository.class);
        PortalActionsService actions = new PortalActionsService(null, repository, null, null, null, null, null, null);
        Customer owner = new Customer(); owner.setCustomerId(1L);
        Booking booking = new Booking(); booking.setCustomer(owner); booking.setBookingStatus(BookingStatus.CONFIRMED);
        booking.setStartAt(PortalActionsService.now().plusHours(3));
        when(repository.findByIdForUpdate(42L)).thenReturn(Optional.of(booking));
        assertThatThrownBy(() -> actions.cancel(42L, 9L)).isInstanceOf(AccessDeniedException.class);
        assertThat(booking.getBookingStatus()).isEqualTo(BookingStatus.CONFIRMED);
        booking.setBookingStatus(BookingStatus.COMPLETED);
        assertThatThrownBy(() -> actions.cancel(42L, 1L)).isInstanceOf(IllegalStateException.class);
        assertThat(booking.getBookingStatus()).isEqualTo(BookingStatus.COMPLETED);
        booking.setBookingStatus(BookingStatus.CONFIRMED); booking.setStartAt(PortalActionsService.now().plusMinutes(119));
        assertThatThrownBy(() -> actions.cancel(42L, 1L)).isInstanceOf(IllegalStateException.class);
        booking.setStartAt(PortalActionsService.now().plusHours(3)); actions.cancel(42L, 1L);
        assertThat(booking.getBookingStatus()).isEqualTo(BookingStatus.CANCELLED);
    }
}
