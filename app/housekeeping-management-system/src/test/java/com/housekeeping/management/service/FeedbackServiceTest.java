package com.housekeeping.management.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import com.housekeeping.management.repository.FeedbackRepository;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

class FeedbackServiceTest {
    private BookingReservationService bookings;
    private FeedbackRepository feedback;
    private AccountAuthorizationService authorization;
    private FeedbackService service;

    @BeforeEach
    void setUp() {
        bookings = mock(BookingReservationService.class);
        feedback = mock(FeedbackRepository.class);
        authorization = mock(AccountAuthorizationService.class);
        service = new com.housekeeping.management.service.impl.FeedbackServiceImpl(bookings, feedback, authorization);
    }

    @Test
    void customerCanSubmitRatedFeedbackOnceAfterSuccessfulPayment() {
        when(bookings.lockBookingForFeedback(30L)).thenReturn(Optional.of(booking(4L, "COMPLETED")));
        when(feedback.existsForBooking(30L)).thenReturn(false);

        service.submit(30L, 4L, 5, List.of("CLEANING_QUALITY"), "  Nhân viên làm việc cẩn thận.  ");

        verify(feedback).create(30L, 4L, 5, List.of("CLEANING_QUALITY"), "Nhân viên làm việc cẩn thận.");
    }

    @Test
    void feedbackRequiresBookingOwnershipCompletedStatusAndNoExistingFeedback() {
        when(bookings.lockBookingForFeedback(30L)).thenReturn(Optional.of(booking(4L, "COMPLETED")));
        assertThatThrownBy(() -> service.submit(30L, 5L, 2, List.of(), "Góp ý"))
                .isInstanceOf(AccessDeniedException.class);

        when(bookings.lockBookingForFeedback(31L)).thenReturn(Optional.of(booking(4L, "AWAITING_PAYMENT")));
        assertThatThrownBy(() -> service.submit(31L, 4L, 2, List.of(), "Góp ý"))
                .isInstanceOf(IllegalStateException.class);

        when(feedback.existsForBooking(30L)).thenReturn(true);
        assertThatThrownBy(() -> service.submit(30L, 4L, 2, List.of(), "Góp ý"))
                .isInstanceOf(IllegalStateException.class);
        verify(feedback, never()).create(anyLong(), anyLong(), anyInt(), anyList(), any());
    }

    @Test
    void validatesRatingTopicsAndConditionalMessageAndAllowsEditingBeforeReply() {
        when(bookings.lockBookingForFeedback(30L)).thenReturn(Optional.of(booking(4L, "COMPLETED")));
        when(feedback.update(30L, 4L, 5, List.of("TIMELINESS"), null)).thenReturn(1);

        assertThatThrownBy(() -> service.submit(30L, 4L, 0, List.of(), "Bad rating"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.submit(30L, 4L, 2, List.of(), " "))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.submit(30L, 4L, 4, List.of("UNKNOWN"), null))
                .isInstanceOf(IllegalArgumentException.class);

        service.update(30L, 4L, 5, List.of("TIMELINESS"), " ");
        verify(feedback).update(30L, 4L, 5, List.of("TIMELINESS"), null);
    }

    @Test
    void customerCannotEditAfterAdminReply() {
        when(bookings.lockBookingForFeedback(30L)).thenReturn(Optional.of(booking(4L, "COMPLETED")));
        when(feedback.existsForBooking(30L)).thenReturn(true);
        when(feedback.update(30L, 4L, 5, List.of(), null)).thenReturn(0);
        assertThatThrownBy(() -> service.update(30L, 4L, 5, List.of(), null))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void feedbackReplyAndResolutionRequireAnActiveAdmin() {
        when(feedback.reply(90L, "Đã tiếp nhận.")).thenReturn(1);
        when(feedback.resolve(90L)).thenReturn(1);

        service.reply(90L, 9L, " Đã tiếp nhận. ");
        service.resolve(90L, 9L);

        verify(feedback).reply(90L, "Đã tiếp nhận.");
        verify(feedback).resolve(90L);
        verify(authorization, times(2)).requireActiveAdmin(9L);
    }

    @Test
    void feedbackReplyAndResolutionRejectNonAdminInactiveOrUnknownActors() {
        doThrow(new AccessDeniedException("forbidden")).when(authorization).requireActiveAdmin(4L);
        doThrow(new AccessDeniedException("forbidden")).when(authorization).requireActiveAdmin(5L);
        doThrow(new AccessDeniedException("forbidden")).when(authorization).requireActiveAdmin(null);

        assertThatThrownBy(() -> service.reply(90L, 4L, "Trả lời"))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.resolve(90L, 5L))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.reply(90L, null, "Trả lời"))
                .isInstanceOf(AccessDeniedException.class);

        verify(feedback, never()).reply(anyLong(), anyString());
        verify(feedback, never()).resolve(anyLong());
    }

    private BookingReservationService.BookingFeedbackState booking(Long customerId, String status) {
        return new BookingReservationService.BookingFeedbackState(customerId, status);
    }
}
