package com.housekeeping.management.service;

import com.housekeeping.management.dto.cancellation.CancellationDto.CancelRequest;
import com.housekeeping.management.dto.cancellation.CancellationDto.CancelResponse;
import com.housekeeping.management.dto.cancellation.CancellationDto.PreviewResponse;

public interface BookingCancellationPolicyService {
    PreviewResponse previewCancellation(Long bookingId, Long customerId, String reasonCode);
    default PreviewResponse previewCancellation(Long bookingId, Long customerId) {
        return previewCancellation(bookingId, customerId, "OTHER");
    }
    CancelResponse executeCancellation(Long bookingId, Long customerId, CancelRequest request);
}
