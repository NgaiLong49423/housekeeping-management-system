package com.housekeeping.management.web;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.housekeeping.management.dto.response.PortalViews;
import com.housekeeping.management.entity.Booking;
import com.housekeeping.management.enums.BookingStatus;
import com.housekeeping.management.service.BookingCancellationService;
import com.housekeeping.management.service.PortalQueryService;
import com.housekeeping.management.service.FeedbackService;
import com.housekeeping.management.repository.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

class PortalOwnershipTest {
    @Test
    void roleAndIdMustBothMatchToReadBookingOrEvidence() {
        PortalQueryService query = new com.housekeeping.management.service.impl.PortalQueryServiceImpl(
                mock(BookingPersistenceRepository.class), mock(CancellationLogRepository.class), mock(BookingDetailRepository.class), mock(BookingImageRepository.class),
                mock(ServicesRepository.class), mock(StaffAccountRepository.class), mock(UserRepository.class),
                mock(StaffProfileRepository.class), mock(StaffFollowRepository.class), mock(StaffAvailabilityRepository.class),
                mock(PaymentPersistenceRepository.class), mock(FeedbackRepository.class), mock(FeedbackService.class));
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
        BookingCancellationService cancellation = new com.housekeeping.management.service.impl.BookingCancellationServiceImpl(
                persistence, mock(CancellationLogRepository.class));
        LocalDateTime now = LocalDateTime.now(ZoneOffset.ofHours(7)).withNano(0);
        when(persistence.lockEntity(42L)).thenReturn(Optional.of(entity(4L, BookingStatus.CONFIRMED, now.plusHours(3))))
                .thenReturn(Optional.of(entity(1L, BookingStatus.COMPLETED, now.plusHours(3))))
                .thenReturn(Optional.of(entity(1L, BookingStatus.CONFIRMED, now.plusMinutes(119))));
        assertThatThrownBy(() -> cancellation.cancelByCustomer(42L, 9L)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> cancellation.cancelByCustomer(42L, 1L)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> cancellation.cancelByCustomer(42L, 1L)).isInstanceOf(IllegalStateException.class);
    }

    private static Booking entity(Long customerId, BookingStatus status, LocalDateTime start) {
        Booking booking = new Booking();
        booking.setBookingId(42L); booking.setCustomerId(customerId); booking.setStaffId(2L);
        booking.setStatus(status); booking.setStartTime(start); booking.setTotalAmount(new BigDecimal("150000"));
        booking.setWorkStartedAt(null);
        return booking;
    }

}
