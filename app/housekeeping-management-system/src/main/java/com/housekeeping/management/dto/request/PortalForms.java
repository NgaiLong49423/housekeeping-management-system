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
            @Size(max = 1000) String incidentDriveLink) {
        public FeedbackForm(Integer rating, List<String> topics, String content) {
            this(rating, topics, content, false, List.of(), null, null, null, null, null);
        }
        public FeedbackForm(Integer rating, List<String> topics, String content, Boolean reportIncident,
                List<String> incidentTypes, String incidentDescription) {
            this(rating, topics, content, reportIncident, incidentTypes, incidentDescription, null, null, null, null);
        }
        public FeedbackForm(Integer rating, List<String> topics, String content, Boolean reportIncident,
                List<String> incidentTypes, String incidentDescription, String incidentItemDescription,
                BigDecimal estimatedItemValue, Integer itemAgeMonths) {
            this(rating,topics,content,reportIncident,incidentTypes,incidentDescription,incidentItemDescription,
                    estimatedItemValue,itemAgeMonths,null);
        }
    }
    public record FeedbackReplyForm(@NotBlank(message = "Vui lòng nhập nội dung phản hồi khách hàng.")
            @jakarta.validation.constraints.Size(max = 4000, message = "Phản hồi tối đa 4.000 ký tự.") String reply) { }
    public record FeedbackIncidentForm(@NotEmpty List<@NotBlank @Size(max = 30) String> incidentTypes,
            @NotBlank @Size(max = 4000) String description,
            @Size(max = 300) String itemDescription,
            @DecimalMin(value = "0.01") @Digits(integer = 10, fraction = 2) BigDecimal estimatedItemValue,
            @Min(0) @Max(1200) Integer itemAgeMonths, @Size(max = 1000) String driveLink) {
        public FeedbackIncidentForm(List<String> incidentTypes, String description) {
            this(incidentTypes, description, null, null, null, null);
        }
    }
    public record IncidentItemDetails(@Size(max = 300) String description,
            @DecimalMin(value = "0.01") @Digits(integer = 10, fraction = 2) BigDecimal estimatedValue,
            @Min(0) @Max(1200) Integer ageMonths) { }
    public record IncidentReviewForm(
            @NotBlank(message = "Hãy chọn trạng thái xác minh.") @Size(max = 30, message = "Trạng thái xác minh không hợp lệ.") String caseStatus,
            @Size(max = 30, message = "Mã biện pháp khắc phục không hợp lệ.") String remedyType,
            @Size(max = 4000, message = "Kết quả/cách xử lý khách sẽ thấy tối đa 4.000 ký tự.") String customerOutcome,
            @Size(max = 4000, message = "Kết luận nội bộ tối đa 4.000 ký tự.") String internalFinding,
            boolean remedyCompleted,
            @Size(max = 30, message = "Mức xử lý Staff không hợp lệ.") String staffAction,
            @Size(max = 1000, message = "Thông báo riêng cho Staff tối đa 1.000 ký tự.") String staffNotice,
            @Size(max = 100, message = "Chỉ chọn tối đa 100 bằng chứng đã xem.") List<Long> reviewedEvidenceIds) {
        public IncidentReviewForm(String caseStatus, String remedyType, String customerOutcome, String internalFinding,
                boolean remedyCompleted, String staffAction, String staffNotice) {
            this(caseStatus, remedyType, customerOutcome, internalFinding, remedyCompleted, staffAction, staffNotice, List.of());
        }
    }
    public record FinancialEntryForm(@NotBlank @Size(max = 30) String entryType,
            @NotNull @DecimalMin(value = "0.01") @Digits(integer = 10, fraction = 2) BigDecimal amount,
            @NotBlank @Size(max = 1000) String reason, @Size(max = 20) String paymentMethod,
            @Size(max = 255) String transactionReference, boolean simulated) { }
    public record CustomerCompensationResponseForm(@NotBlank @Size(max = 30) String decision,
            @Size(max = 20) String payoutMethod, @Size(max = 100) String bankName,
            @Size(max = 150) String accountHolder, @Size(max = 50) String accountNumber) { }
    public record ResolutionActionForm(@NotBlank @Pattern(regexp = "INTERNAL_WORK|CUSTOMER_PAYOUT") String type,
            @NotBlank @Size(max = 1000) String description,
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime dueAt,
            @DecimalMin(value = "0.01") @Digits(integer = 10, fraction = 2) BigDecimal amount,
            @Size(max = 1000) String calculationBasis) { }
    public record ResolutionOptionForm(@NotBlank @Size(max = 150) String title,
            @NotBlank @Size(max = 2000) String description,
            @NotEmpty @Size(max = 8) List<@Valid ResolutionActionForm> actions) { }
    public record ResolutionPlanForm(@Size(max = 4000) String internalFinding,
            @NotEmpty @Size(max = 3) List<@Valid ResolutionOptionForm> options) { }
    public record CustomerResolutionChoiceForm(@NotBlank @Pattern(regexp = "SELECT|DISCUSS") String decision,
            @Positive Long optionId, @Pattern(regexp = "|CASH|BANK_TRANSFER") String payoutMethod,
            @Size(max = 2000) String message) { }
    public record WorkOrderForm(@Positive Long assignedStaffId,
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime scheduledAt,
            @NotBlank @Size(max = 2000) String scope,
            @DecimalMin("0") @Digits(integer = 10, fraction = 2) BigDecimal estimatedCost,
            @Size(max = 1000) String estimatedMaterials) { }
    public record WorkOrderCompletionForm(@DecimalMin("0") @Digits(integer = 10, fraction = 2) BigDecimal actualCost,
            @Size(max = 1000) String actualMaterials, @NotBlank @Size(max = 1000) String evidence) { }
    public record CustomerWorkOrderResponseForm(@NotBlank @Pattern(regexp = "CONFIRMED|DISPUTED") String decision,
            @Size(max = 1000) String message) { }
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
            @NotBlank @Size(max = 500) String vietmapRefId,
            String provinceCity, String wardCommune, String addressDetail,
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
            @Min(1) Integer maxDelayMinutes, @NotNull @Min(0) @Max(100) Integer discountPercent) { }
    public record BookingDelaySettingsForm(@NotEmpty List<@Valid BookingDelayTierForm> tiers) { }
    public record OperatingHoursForm(@NotNull @Min(1) @Max(7) Integer dayOfWeek, boolean closed,
            @DateTimeFormat(iso = DateTimeFormat.ISO.TIME) LocalTime openingTime,
            @DateTimeFormat(iso = DateTimeFormat.ISO.TIME) LocalTime closingTime) { }
    public record StaffProfileForm(@Size(max = 500) String avatarUrl, @Size(max = 5000) String bio) { }
    public record AdminBookingCancellationForm(@NotBlank @Size(max = 500) String reason) { }
    public record BookingReassignmentForm(@NotNull @Positive Long staffId) { }
}
