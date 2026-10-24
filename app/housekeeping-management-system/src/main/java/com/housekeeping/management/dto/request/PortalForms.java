package com.housekeeping.management.dto.request;

import jakarta.validation.constraints.*;
import jakarta.validation.Valid;
import com.housekeeping.management.domain.StaffType;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;

public final class PortalForms {
    private PortalForms() { }
    public record BookingForm(@NotNull @Positive Long staffId, @NotNull @Positive Long houseId,
            @NotNull @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startAt,
            @NotEmpty List<@NotNull @Positive Long> serviceIds,
            @Size(max = 2000) String areaNote) { }
    public record FeedbackForm(@NotBlank String content) { }
    public record FeedbackReplyForm(@NotBlank String reply) { }
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
    public record AvailabilityForm(@NotNull @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate workDate,
            @NotNull @DateTimeFormat(iso = DateTimeFormat.ISO.TIME) LocalTime startTime,
            @NotNull @DateTimeFormat(iso = DateTimeFormat.ISO.TIME) LocalTime endTime) { }
    public record LeaveRequestForm(@NotNull @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @NotNull @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @NotBlank @Size(max = 500) String reason) { }
    public record HouseForm(@NotBlank @Size(max = 100) String houseName,
            @NotBlank @Size(max = 500) String address,
            @NotNull @DecimalMin("-90.0") @DecimalMax("90.0") @Digits(integer = 3, fraction = 7) BigDecimal latitude,
            @NotNull @DecimalMin("-180.0") @DecimalMax("180.0") @Digits(integer = 3, fraction = 7) BigDecimal longitude,
            @Size(max = 500) String addressNote) { }
    public record CompanySettingsForm(@NotBlank @Size(max = 500) String companyAddress,
            @NotNull @DecimalMin("-90.0") @DecimalMax("90.0") @Digits(integer = 3, fraction = 7) BigDecimal latitude,
            @NotNull @DecimalMin("-180.0") @DecimalMax("180.0") @Digits(integer = 3, fraction = 7) BigDecimal longitude,
            @NotNull @DecimalMin(value = "0.01") @Digits(integer = 4, fraction = 2) BigDecimal serviceRadiusKm,
            @Size(min = 7, max = 7) List<@Valid OperatingHoursForm> operatingHours) { }
    public record OperatingHoursForm(@NotNull @Min(1) @Max(7) Integer dayOfWeek, boolean closed,
            @DateTimeFormat(iso = DateTimeFormat.ISO.TIME) LocalTime openingTime,
            @DateTimeFormat(iso = DateTimeFormat.ISO.TIME) LocalTime closingTime) { }
    public record StaffProfileForm(@Size(max = 500) String avatarUrl, @Size(max = 5000) String bio) { }
    public record AdminBookingCancellationForm(@NotBlank @Size(max = 500) String reason) { }
    public record BookingReassignmentForm(@NotNull @Positive Long staffId) { }
}
