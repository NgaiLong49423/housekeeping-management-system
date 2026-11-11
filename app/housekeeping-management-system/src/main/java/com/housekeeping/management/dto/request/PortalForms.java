package com.housekeeping.management.dto.request;

import jakarta.validation.constraints.*;
import jakarta.validation.Valid;
import com.housekeeping.management.domain.StaffType;
import com.housekeeping.management.domain.HouseAddressType;
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
    public record FeedbackForm(@NotNull @Min(1) @Max(5) Integer rating,
            @Size(max = 6) List<@NotBlank @Size(max = 30) String> topics,
            @Size(max = 4000) String content, Boolean reportIncident,
            @Size(max = 4) List<@NotBlank @Size(max = 30) String> incidentTypes,
            @Size(max = 4000) String incidentDescription,
            @Size(max = 300) String incidentItemDescription,
            @DecimalMin(value = "0.01") @Digits(integer = 10, fraction = 2) BigDecimal estimatedItemValue,
            @Min(0) @Max(1200) Integer itemAgeMonths,
            @Size(max = 1000) String incidentDriveLink,
            @Size(max = 1000) String customerRequestedResolution) {
        public FeedbackForm(Integer rating, List<String> topics, String content) {
            this(rating, topics, content, false, List.of(), null, null, null, null, null, null);
        }
        public FeedbackForm(Integer rating, List<String> topics, String content, Boolean reportIncident,
                List<String> incidentTypes, String incidentDescription) {
            this(rating, topics, content, reportIncident, incidentTypes, incidentDescription, null, null, null, null, null);
        }
        public FeedbackForm(Integer rating, List<String> topics, String content, Boolean reportIncident,
                List<String> incidentTypes, String incidentDescription, String incidentItemDescription,
                BigDecimal estimatedItemValue, Integer itemAgeMonths) {
            this(rating,topics,content,reportIncident,incidentTypes,incidentDescription,incidentItemDescription,
                    estimatedItemValue,itemAgeMonths,null,null);
        }
        public FeedbackForm(Integer rating, List<String> topics, String content, Boolean reportIncident,
                List<String> incidentTypes, String incidentDescription, String incidentItemDescription,
                BigDecimal estimatedItemValue, Integer itemAgeMonths, String incidentDriveLink) {
            this(rating,topics,content,reportIncident,incidentTypes,incidentDescription,incidentItemDescription,
                    estimatedItemValue,itemAgeMonths,incidentDriveLink,null);
        }
    }
    public record FeedbackReplyForm(@NotBlank(message = "Vui lòng nhập nội dung phản hồi khách hàng.")
            @jakarta.validation.constraints.Size(max = 4000, message = "Phản hồi tối đa 4.000 ký tự.") String reply) { }
    public record FeedbackIncidentForm(@NotEmpty List<@NotBlank @Size(max = 30) String> incidentTypes,
            @NotBlank @Size(max = 4000) String description,
            @Size(max = 300) String itemDescription,
            @DecimalMin(value = "0.01") @Digits(integer = 10, fraction = 2) BigDecimal estimatedItemValue,
            @Min(0) @Max(1200) Integer itemAgeMonths, @Size(max = 1000) String driveLink,
            @Size(max = 1000) String customerRequestedResolution) {
        public FeedbackIncidentForm(List<String> incidentTypes, String description) {
            this(incidentTypes, description, null, null, null, null, null);
        }
        public FeedbackIncidentForm(List<String> incidentTypes, String description, String itemDescription,
                BigDecimal estimatedItemValue, Integer itemAgeMonths, String driveLink) {
            this(incidentTypes,description,itemDescription,estimatedItemValue,itemAgeMonths,driveLink,null);
        }
    }
    public record IncidentItemDetails(@Size(max = 300) String description,
            @DecimalMin(value = "0.01") @Digits(integer = 10, fraction = 2) BigDecimal estimatedValue,
            @Min(0) @Max(1200) Integer ageMonths) { }
    public record IncidentReviewForm(
            @NotBlank(message = "Hãy chọn trạng thái xác minh.") @Size(max = 30, message = "Trạng thái xác minh không hợp lệ.") String caseStatus,
            @Size(max = 4000, message = "Kết luận nội bộ tối đa 4.000 ký tự.") String internalFinding,
            @Size(max = 100, message = "Chỉ chọn tối đa 100 bằng chứng đã xem.") List<Long> reviewedEvidenceIds) {
        public IncidentReviewForm(String caseStatus, String internalFinding) {
            this(caseStatus, internalFinding, List.of());
        }
    }
    public record StaffIncidentActionForm(@NotBlank @Size(max = 30) String staffAction,
            @Size(max = 1000) String staffNotice) { }
    public record StaffIncidentResponseForm(@NotBlank @Size(max = 4000) String response) { }
    public record StaffIncidentDecisionForm(@NotBlank @Size(max = 30) String decision,
            @DecimalMin(value = "0.00") @Digits(integer = 10, fraction = 2) BigDecimal liabilityAmount,
            @NotBlank @Size(max = 1000) String reason) { }
    public record StaffRecoveryInstallmentForm(@NotNull @DecimalMin(value = "0.01") @Digits(integer = 10, fraction = 2) BigDecimal amount,
            @NotBlank @Size(max = 20) String paymentMethod, @NotBlank @Size(max = 255) String transactionReference) { }
    public record FinancialEntryForm(@NotBlank @Size(max = 30) String entryType,
            @NotNull @DecimalMin(value = "0.01") @Digits(integer = 10, fraction = 2) BigDecimal amount,
            @NotBlank @Size(max = 1000) String reason, @Size(max = 20) String paymentMethod,
            @Size(max = 255) String transactionReference, boolean simulated) { }
    public record CustomerCompensationResponseForm(@NotBlank @Size(max = 30) String decision,
            @Size(max = 20) String payoutMethod, @Size(max = 100) String bankName,
            @Size(max = 150) String accountHolder, @Size(max = 50) String accountNumber,
            @Size(max = 4000) String discussionMessage) {
        public CustomerCompensationResponseForm(String decision, String payoutMethod, String bankName,
                String accountHolder, String accountNumber) {
            this(decision,payoutMethod,bankName,accountHolder,accountNumber,null);
        }
    }
    public record CustomerRegistrationForm(@NotBlank @Size(max = 150) String name,
            @NotBlank @Email @Size(max = 255) String email, @NotBlank @Size(max = 20) String phone,
            @NotBlank @Size(min = 8, max = 72) String password, @NotBlank @Size(max = 72) String confirmPassword) { }
    public record AdminAccountForm(@NotBlank @Size(max = 150) String name,
            @NotBlank @Email @Size(max = 255) String email, @NotBlank @Size(max = 20) String phone,
            @NotBlank @Size(min = 8, max = 72) String password) { }
    public record ServiceForm(@NotBlank @Size(max = 150) String name, @Size(max = 1000) String description,
            @Min(1) int duration, @NotNull @DecimalMin("0") @Digits(integer = 10, fraction = 2) BigDecimal price,
            @Size(max = 500) @Pattern(regexp = "^$|https://\\S+$", message = "URL ảnh phải bắt đầu bằng https://.") String imageUrl) {
        public ServiceForm(String name, String description, int duration, BigDecimal price) {
            this(name, description, duration, price, null);
        }
    }
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
    public record AvailabilityBatchForm(@NotNull @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @NotNull @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            List<Integer> daysOfWeek,
            @NotNull @DateTimeFormat(iso = DateTimeFormat.ISO.TIME) LocalTime startTime,
            @NotNull @DateTimeFormat(iso = DateTimeFormat.ISO.TIME) LocalTime endTime) { }
    public record LeaveRequestForm(@NotNull @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @NotNull @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @NotBlank @Size(max = 500) String reason) { }
    public record HouseForm(@NotBlank @Size(max = 150) String recipientName,
            @NotBlank @Size(min = 7, max = 20) @Pattern(regexp = "[+0-9() .-]+", message = "Số điện thoại không hợp lệ.") String recipientPhone,
            @Size(max = 500) String vietmapRefId,
            @NotNull HouseAddressType addressType, boolean defaultAddress,
            @Size(max = 500) String addressNote) { }
    public record CompanySettingsForm(@NotBlank @Size(max = 500) String companyAddress,
            @NotNull @DecimalMin("-90.0") @DecimalMax("90.0") @Digits(integer = 3, fraction = 7) BigDecimal latitude,
            @NotNull @DecimalMin("-180.0") @DecimalMax("180.0") @Digits(integer = 3, fraction = 7) BigDecimal longitude,
            @NotNull @DecimalMin(value = "0.01") @Digits(integer = 4, fraction = 2) BigDecimal serviceRadiusKm,
            @Size(min = 7, max = 56) List<@Valid OperatingHoursForm> operatingHours) { }
    public record CompanyScheduleVersionForm(@NotNull @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate effectiveFrom,
            @Size(min = 7, max = 56) List<@Valid OperatingHoursForm> operatingHours) { }
    public record CompanyLocationForm(@NotBlank @Size(max = 500) String vietmapRefId,
            @NotNull @DecimalMin("0.01") @Digits(integer = 4, fraction = 2) BigDecimal serviceRadiusKm) { }
    public record BookingDelayTierForm(@NotNull @Min(1) Integer minDelayMinutes,
            @Min(1) Integer maxDelayMinutes, @NotNull @Min(0) @Max(30) Integer discountPercent) { }
    public record BookingDelaySettingsForm(@NotEmpty List<@Valid BookingDelayTierForm> tiers) { }
    public record OperatingHoursForm(@NotNull @Min(1) @Max(7) Integer dayOfWeek, boolean closed,
            @DateTimeFormat(iso = DateTimeFormat.ISO.TIME) LocalTime openingTime,
            @DateTimeFormat(iso = DateTimeFormat.ISO.TIME) LocalTime closingTime) { }
    public record StaffProfileForm(@Size(max = 500) String avatarUrl, @Size(max = 5000) String bio) { }
    public record AdminBookingCancellationForm(@NotBlank @Size(max = 500) String reason) { }
    public record BookingReassignmentForm(@NotNull @Positive Long staffId) { }
    public record ArrivalVerificationForm(@NotNull @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime actualArrivalAt,
            @NotBlank @Size(max = 500) String note) { }
}
