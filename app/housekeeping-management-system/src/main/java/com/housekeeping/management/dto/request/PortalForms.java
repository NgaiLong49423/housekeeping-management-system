package com.housekeeping.management.dto.request;

import jakarta.validation.constraints.*;
import com.housekeeping.management.domain.StaffType;
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
    public record CustomerRegistrationForm(@NotBlank @Size(max = 150) String name,
            @NotBlank @Email @Size(max = 255) String email, @NotBlank @Size(max = 20) String phone,
            @NotBlank @Size(min = 8, max = 72) String password, @NotBlank @Size(max = 72) String confirmPassword) { }
    public record ServiceForm(@NotBlank @Size(max = 150) String name, @Size(max = 1000) String description,
            @Min(1) int duration, @NotNull @DecimalMin("0") @Digits(integer = 10, fraction = 2) BigDecimal price) { }
    public record StaffCreateForm(@NotBlank @Size(max = 150) String name, @NotBlank @Email @Size(max = 255) String email,
            @NotBlank @Size(min = 8, max = 72) String password, @NotBlank @Size(max = 20) String phone,
            @NotNull StaffType staffType) { }
    public record StaffUpdateForm(@NotBlank @Size(max = 150) String name, @Size(max = 20) String phone) { }
    public record ScheduleForm(@NotNull @Positive Long staffId,
            @NotNull @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startAt,
            @NotNull @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endAt) { }
    public record HouseForm(@NotBlank @Size(max = 100) String houseName,
            @NotBlank @Size(max = 500) String address,
            @NotNull @DecimalMin("-90.0") @DecimalMax("90.0") @Digits(integer = 3, fraction = 7) BigDecimal latitude,
            @NotNull @DecimalMin("-180.0") @DecimalMax("180.0") @Digits(integer = 3, fraction = 7) BigDecimal longitude,
            @Size(max = 500) String addressNote) { }
}
