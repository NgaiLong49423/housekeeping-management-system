package com.housekeeping.management.dto.cancellation;

import java.math.BigDecimal;

public class CancellationDto {

    public record PreviewResponse(
        Long bookingId,
        BigDecimal totalAmount,
        BigDecimal cancellationFee,
        BigDecimal refundAmount,
        BigDecimal unpaidDebtAdded,
        long minutesUntilStart,
        String feePolicyNotice,
        boolean canReschedule,
        String rescheduleMessage
    ) {}

    public record CancelRequest(
        String reasonCode,
        String reasonNote
    ) {}

    public record CancelResponse(
        Long bookingId,
        String status,
        BigDecimal cancellationFee,
        BigDecimal refundAmount,
        BigDecimal unpaidDebt,
        boolean codDisabled,
        boolean accountSuspended,
        String message
    ) {}
}
