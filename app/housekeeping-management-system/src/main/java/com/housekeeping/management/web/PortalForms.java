package com.housekeeping.management.web;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;

public final class PortalForms {
    private PortalForms() { }
    public record BookingForm(@NotNull @Positive Long staffId, @NotNull @Positive Long scheduleId,
            @NotNull @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startAt,
            @NotEmpty @Size(max = 20) List<@NotNull @Positive Long> serviceIds) { }
    public record FeedbackForm(@Min(1) @Max(5) int rating, @NotBlank @Size(max = 2000) String comment) { }
    public record ServiceForm(@NotBlank @Size(max = 150) String name, @Size(max = 1000) String description,
            @Min(1) int duration, @NotNull @DecimalMin("0") @Digits(integer = 18, fraction = 0) BigDecimal price) { }
    public record StaffCreateForm(@NotBlank @Size(max = 150) String name, @NotBlank @Email @Size(max = 254) String email,
            @NotBlank @Size(min = 8, max = 72) String password, @Size(max = 20) String phone) { }
    public record StaffUpdateForm(@NotBlank @Size(max = 150) String name, @Size(max = 20) String phone) { }
    public record ScheduleForm(@NotNull @Positive Long staffId,
            @NotNull @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startAt,
            @NotNull @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endAt) { }
}
