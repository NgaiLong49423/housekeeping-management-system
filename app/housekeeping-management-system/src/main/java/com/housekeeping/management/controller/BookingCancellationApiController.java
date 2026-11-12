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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.security.access.AccessDeniedException;
import java.util.Map;

@RestController
@RequestMapping("/api/bookings")
@RequiredArgsConstructor
public class BookingCancellationApiController {

    private final BookingCancellationPolicyService cancellationService;

    @GetMapping("/{id}/cancellation-preview")
    public ResponseEntity<PreviewResponse> previewCancellation(
            @PathVariable("id") Long bookingId,
            @RequestParam(defaultValue = "OTHER") String reasonCode,
            @AuthenticationPrincipal PortalUser user) {
        if (user == null) {
            return ResponseEntity.status(401).build();
        }
        PreviewResponse response = cancellationService.previewCancellation(bookingId, user.getAccountId(), reasonCode);
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

    @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
    public ResponseEntity<Map<String, String>> cancellationError(RuntimeException failure) {
        return ResponseEntity.badRequest().body(Map.of("message", failure.getMessage() == null
                ? "Không thể hủy lịch hẹn." : failure.getMessage()));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, String>> accessDenied(AccessDeniedException failure) {
        return ResponseEntity.status(403).body(Map.of("message", "Bạn không có quyền thao tác lịch hẹn này."));
    }
}
