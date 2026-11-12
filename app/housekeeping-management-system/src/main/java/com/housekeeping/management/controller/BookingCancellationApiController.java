package com.housekeeping.management.controller;

import com.housekeeping.management.dto.cancellation.CancellationDto.CancelRequest;
import com.housekeeping.management.dto.cancellation.CancellationDto.CancelResponse;
import com.housekeeping.management.dto.cancellation.CancellationDto.PreviewResponse;
import com.housekeeping.management.service.BookingCancellationPolicyService;
import com.housekeeping.management.web.PortalUser;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/bookings")
@RequiredArgsConstructor
public class BookingCancellationApiController {

    private final BookingCancellationPolicyService cancellationService;

    @GetMapping("/{id}/cancellation-preview")
    public ResponseEntity<PreviewResponse> previewCancellation(
            @PathVariable("id") Long bookingId,
            @AuthenticationPrincipal PortalUser user) {
        if (user == null) {
            return ResponseEntity.status(401).build();
        }
        PreviewResponse response = cancellationService.previewCancellation(bookingId, user.getAccountId());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<CancelResponse> executeCancellation(
            @PathVariable("id") Long bookingId,
            @AuthenticationPrincipal PortalUser user,
            @RequestBody(required = false) CancelRequest request) {
        if (user == null) {
            return ResponseEntity.status(401).build();
        }
        CancelResponse response = cancellationService.executeCancellation(bookingId, user.getAccountId(), request);
        return ResponseEntity.ok(response);
    }
}
