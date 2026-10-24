package com.housekeeping.management.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import com.housekeeping.management.repository.BookingPersistenceRepository;
import com.housekeeping.management.repository.FeedbackRepository;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

class FeedbackServiceTest {
    private BookingPersistenceRepository bookings;
    private FeedbackRepository feedback;
    private FeedbackService service;

    @BeforeEach
    void setUp() {
        bookings = mock(BookingPersistenceRepository.class);
        feedback = mock(FeedbackRepository.class);
        service = new FeedbackService(bookings, feedback);
    }

    @Test
    void customerCanSubmitTextFeedbackOnceAfterSuccessfulPayment() {
        when(bookings.lockBooking(30L)).thenReturn(Optional.of(booking(4L, "COMPLETED")));
        when(feedback.existsForBooking(30L)).thenReturn(false);

        service.submit(30L, 4L, "  Nhân viên làm việc cẩn thận.  ");

        verify(feedback).create(30L, 4L, "Nhân viên làm việc cẩn thận.");
    }

    @Test
    void feedbackRequiresBookingOwnershipCompletedStatusAndNoExistingFeedback() {
        when(bookings.lockBooking(30L)).thenReturn(Optional.of(booking(4L, "COMPLETED")));
        assertThatThrownBy(() -> service.submit(30L, 5L, "Góp ý"))
                .isInstanceOf(AccessDeniedException.class);

        when(bookings.lockBooking(31L)).thenReturn(Optional.of(booking(4L, "AWAITING_PAYMENT")));
        assertThatThrownBy(() -> service.submit(31L, 4L, "Góp ý"))
                .isInstanceOf(IllegalStateException.class);

        when(feedback.existsForBooking(30L)).thenReturn(true);
        assertThatThrownBy(() -> service.submit(30L, 4L, "Góp ý"))
                .isInstanceOf(IllegalStateException.class);
        verify(feedback, never()).create(anyLong(), anyLong(), anyString());
    }

    private BookingPersistenceRepository.BookingState booking(Long customerId, String status) {
        return new BookingPersistenceRepository.BookingState(30L, customerId, 8L, 9L,
                LocalDateTime.now(), LocalDateTime.now().plusHours(1), status);
    }
}
