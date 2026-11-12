package com.housekeeping.management.web;

import com.housekeeping.management.enums.ReturnStatus;
import static com.housekeeping.management.dto.response.PortalViews.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.housekeeping.management.config.SecurityConfig;
import com.housekeeping.management.dto.response.PortalViews;
import com.housekeeping.management.dto.request.PortalForms.HouseForm;
import com.housekeeping.management.entity.House;
import com.housekeeping.management.dto.request.PortalForms.AvailabilityForm;
import com.housekeeping.management.controller.PortalAdminController;
import com.housekeeping.management.controller.AdminServiceController;
import com.housekeeping.management.controller.AdminStaffController;
import com.housekeeping.management.controller.AdminFeedbackController;
import com.housekeeping.management.controller.AdminBookingController;
import com.housekeeping.management.controller.AdminAccountController;
import com.housekeeping.management.controller.PortalController;
import com.housekeeping.management.controller.AuthController;
import com.housekeeping.management.controller.BookingController;
import com.housekeeping.management.controller.PaymentController;
import com.housekeeping.management.controller.HouseController;
import com.housekeeping.management.controller.StaffController;
import com.housekeeping.management.controller.StaffLeaveController;
import com.housekeeping.management.controller.AdminLeaveController;
import com.housekeeping.management.controller.AdminCompanySettingsController;
import com.housekeeping.management.controller.StaffProfileController;
import com.housekeeping.management.service.AdminBookingManagementService;
import com.housekeeping.management.dto.request.PortalForms.CompanySettingsForm;
import com.housekeeping.management.dto.request.PortalForms.CompanyScheduleVersionForm;
import com.housekeeping.management.dto.request.PortalForms.OperatingHoursForm;
import com.housekeeping.management.dto.request.PortalForms.StaffProfileForm;
import com.housekeeping.management.service.PortalQueryService;
import com.housekeeping.management.service.*;
import com.housekeeping.management.dto.response.BookingAvailabilityOptions;
import com.housekeeping.management.dto.response.BookingAvailabilityDates;
import com.housekeeping.management.dto.response.BookingAvailabilityOptions.StaffOption;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.time.LocalDateTime;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.mock.web.MockMultipartFile;

@WebMvcTest({PortalController.class, AuthController.class, BookingController.class, PaymentController.class, HouseController.class, StaffController.class, PortalAdminController.class, AdminServiceController.class, AdminStaffController.class, AdminFeedbackController.class, AdminBookingController.class, AdminAccountController.class, com.housekeeping.management.controller.AdminCustomerController.class, StaffLeaveController.class, AdminLeaveController.class, AdminCompanySettingsController.class, StaffProfileController.class, com.housekeeping.management.controller.FeedbackAttachmentController.class})
@Import(SecurityConfig.class)
class PortalMvcTest {
    @Autowired MockMvc mvc;
    @Autowired PasswordEncoder encoder;
    @MockitoBean PortalQueryService query;
    @MockitoBean RegistrationService registration;
    @MockitoBean BookingCancellationService cancellation;
    @MockitoBean StaffAvailabilityService availability;
    @MockitoBean StaffFollowService follows;
    @MockitoBean ServiceManagementService serviceManagement;
    @MockitoBean StaffManagementService staffManagement;
    @MockitoBean AuthService identity;
    @MockitoBean BookingReservationService reservations;
    @MockitoBean BookingAvailabilityService bookingAvailability;
    @MockitoBean BookingArrivalService arrival;
    @MockitoBean BookingIncidentService incidents;
    @MockitoBean CustomerDelayReportService customerDelayReports;
    @MockitoBean BookingCompletionService completion;
    @MockitoBean PaymentService payments;
    @MockitoBean OnlinePaymentService onlinePayments;
    @MockitoBean FeedbackService feedbackService;
    @MockitoBean CustomerFeedbackSubmissionService feedbackSubmission;
    @MockitoBean FeedbackCaseService feedbackCases;
    @MockitoBean BookingFinancialService financials;
    @MockitoBean CaseAttachmentStorageService caseAttachments;
    @MockitoBean EvidenceStorageService storage;
    @MockitoBean HouseService houses;
    @MockitoBean LeaveManagementService leaveManagement;
    @MockitoBean CompanySettingsService companySettings;
    @MockitoBean BookingDelayCompensationSettingsService delayCompensationSettings;
    @MockitoBean StaffProfileService staffProfiles;
    @MockitoBean StaffIncidentNoticeService incidentNotices;
    @MockitoBean AdminBookingManagementService adminBookingManagement;
    @MockitoBean AdminAccountService adminAccounts;
    @MockitoBean com.housekeeping.management.service.CustomerManagementService customerManagement;
    @MockitoBean AccountAuthorizationService accountAuthorization;

    private final PortalUser customer = account(1L, "CUSTOMER", "Khách hàng kiểm thử");
    private final PortalUser staff = new PortalUser(2L, 22L, "staff@example.test", "unused-hash", "Nhân viên kiểm thử", "STAFF");
    private final PortalUser admin = account(3L, "ADMIN", "Quản trị kiểm thử");
    private final PortalUser manager = account(5L, "MANAGER", "Quản lý kiểm thử");
    private final OffsetDateTime start = OffsetDateTime.now(ZoneOffset.ofHours(7)).plusDays(2).withHour(8).withMinute(0).withSecond(0).withNano(0);

    static PortalUser account(Long id, String role, String name) {
        return new PortalUser(id, role.toLowerCase() + "@example.test", "unused-hash", name, role);
    }

    @BeforeEach
    void data() {
        when(availability.formRules(anyList(), any())).thenReturn(new StaffAvailabilityService.AvailabilityFormRules(
                LocalDateTime.now(ZoneOffset.ofHours(7)).plusHours(2), 60, 720));
        when(accountAuthorization.isActiveAdmin(3L)).thenReturn(true);
        when(accountAuthorization.isActiveAdmin(5L)).thenReturn(true);
        when(adminAccounts.findAll(5L)).thenReturn(List.of());
        when(delayCompensationSettings.get()).thenReturn(List.of(
                new com.housekeeping.management.dto.BookingDelayCompensation.Tier(1, 15, 5),
                new com.housekeeping.management.dto.BookingDelayCompensation.Tier(16, 30, 10),
                new com.housekeeping.management.dto.BookingDelayCompensation.Tier(31, null, 20)));
        when(query.services()).thenReturn(List.of(
            new ServiceCard(10L, "Dọn dẹp nhà theo giờ", "Làm sạch không gian sống, chăm chút từng góc nhỏ.", 120, new BigDecimal("140000"), true),
            new ServiceCard(11L, "Tổng vệ sinh nhà cửa", "Làm sạch kỹ lưỡng để tổ ấm thêm trong lành.", 180, new BigDecimal("350000"), true),
            new ServiceCard(12L, "Vệ sinh sofa", "Chăm sóc khu vực sinh hoạt của gia đình.", 60, new BigDecimal("200000"), true)));
        when(query.staff(any())).thenReturn(List.of(new StaffCard(2L, "Nhân viên kiểm thử", "", true, "PART_TIME", true, null, "Tận tâm"), new StaffCard(4L, "Nhân viên thứ hai", "", false, "FULL_TIME", true, null, null)));
        when(query.staff(isNull())).thenReturn(List.of(new StaffCard(2L, "Nhân viên kiểm thử", "", false, "PART_TIME", true, null, null)));
        when(query.isPartTime(22L)).thenReturn(true);
        when(query.schedules(any())).thenReturn(List.of(new ScheduleCard(20L, 2L, "Nhân viên kiểm thử", start, start.plusHours(8))));
        when(query.busyIntervals()).thenReturn(List.of(new PortalQueryService.BusyInterval(2L, start.plusHours(4), start.plusHours(5))));
        when(query.bookings(any())).thenReturn(List.of(card("CONFIRMED", null, null), card("AWAITING_PAYMENT", "PENDING", "ONLINE")));
        when(query.feedback()).thenReturn(List.of());
        when(leaveManagement.findForStaff(2L)).thenReturn(List.of());
        when(leaveManagement.findAllForAdmin(3L)).thenReturn(List.of());
        var week=java.util.stream.IntStream.rangeClosed(1, 7)
                .mapToObj(day -> new OperatingHoursForm(day, day == 7,
                        day == 7 ? null : java.time.LocalTime.of(8, 0),
                        day == 7 ? null : java.time.LocalTime.of(17, 0))).toList();
        when(companySettings.get()).thenReturn(new CompanySettingsForm("Địa chỉ kiểm thử", BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ONE, java.util.stream.IntStream.rangeClosed(1, 7)
                .mapToObj(day -> new OperatingHoursForm(day, day == 7,
                        day == 7 ? null : java.time.LocalTime.of(8, 0),
                        day == 7 ? null : java.time.LocalTime.of(17, 0))).toList()));
        LocalDate today=LocalDate.now(java.time.ZoneId.of("Asia/Ho_Chi_Minh"));
        when(companySettings.today()).thenReturn(today);
        when(companySettings.firstSelectableWeekStart()).thenReturn(today.with(java.time.temporal.TemporalAdjusters.nextOrSame(java.time.DayOfWeek.MONDAY)));
        when(companySettings.scheduleForEditor(any())).thenReturn(new CompanyScheduleVersionForm(today,week));
        when(companySettings.versionDates()).thenReturn(List.of());
        when(companySettings.scheduleWindows()).thenReturn(java.util.stream.IntStream.rangeClosed(1,7)
                .mapToObj(day -> new CompanySettingsService.ScheduleWindow(LocalDate.of(1900,1,1),day,false,
                        java.time.LocalTime.of(8,0),java.time.LocalTime.of(18,0))).toList());
        when(companySettings.hasExactVersion(any())).thenReturn(false);
        when(staffProfiles.findOwnProfile(22L)).thenReturn(new StaffProfileForm(null, "Giới thiệu nhân viên"));
        when(query.booking(eq(42L), any())).thenReturn(card("CONFIRMED", null, null));
        when(query.bookingImages(eq(42L), any())).thenReturn(List.of());
        when(query.details(eq(42L), any())).thenReturn(List.of(new DetailCard(30L, "Dọn dẹp nhà theo giờ", 1, 120, new BigDecimal("140000"), List.of())));
    }

    @Test
    void managerInheritsAdminAccessAndAloneCanOpenAdminAccountManagement() throws Exception {
        mvc.perform(get("/admin/bookings").with(user(manager))).andExpect(status().isOk());
        mvc.perform(get("/manager/bookings/42").with(user(manager))).andExpect(status().isOk());
        mvc.perform(get("/admin/accounts").with(user(manager))).andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Tạo tài khoản Admin")));
        mvc.perform(get("/admin/bookings").with(user(staff))).andExpect(status().isForbidden());
    }

    @Test
    void financeScreensKeepOverviewCompactAndSeparateListsFromActions() throws Exception {
        when(financials.all(3L)).thenReturn(List.of());
        when(financials.summary(3L)).thenReturn(new FinancialSummary(BigDecimal.ZERO,BigDecimal.ZERO,
                BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO));
        var adjustment=new FinancialEntryCard(88L,42L,null,"REFUND",new BigDecimal("50000"),"Điều chỉnh giá",
                "PENDING_APPROVAL",null,null,null,false,3L,null,null,null,null,java.time.LocalDateTime.now(),null);
        when(financials.all(3L)).thenReturn(List.of(adjustment));
        when(financials.all(5L)).thenReturn(List.of(adjustment));
        mvc.perform(get("/admin/finance").with(user(admin)))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Tổng hợp dòng tiền thật")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Sổ giao dịch")))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("Điều chỉnh giá"))));
        when(financials.ledger(3L)).thenReturn(List.of(new PortalViews.FinancialLedgerCard(42L,"ADJUSTMENT","Hoàn tiền",
                "PENDING_APPROVAL",new BigDecimal("50000"),false,null,null,"Điều chỉnh giá",
                java.time.LocalDateTime.now(),false,adjustment)));
        mvc.perform(get("/admin/finance/ledger").with(user(admin)))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Chi tiết khoản")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("/admin/finance/entries/88")));
        mvc.perform(get("/admin/finance/entries").with(user(manager)))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Xem chi tiết")))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("Lý do hủy khoản"))));
        when(financials.entry(88L,3L)).thenReturn(java.util.Optional.of(adjustment));
        mvc.perform(get("/admin/finance/entries/88").with(user(admin)))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Chi tiết khoản điều chỉnh")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Lý do hủy khoản")));
        when(financials.entry(88L,5L)).thenReturn(java.util.Optional.of(adjustment));
        mvc.perform(get("/admin/finance/entries/88").with(user(manager)))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Duyệt khoản")))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("Từ chối"))))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("Hủy khoản"))));
        mvc.perform(get("/admin/finance/new").param("bookingId","42").with(user(admin)))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Thông tin khoản thu/chi")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Khách hàng kiểm thử ·")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Booking #GN-42")))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("#42 · Khách hàng kiểm thử"))));
    }

    @Test
    void staffCompletionFailureReturnsToBookingWithFlashError() throws Exception {
        when(query.booking(eq(42L), any())).thenReturn(card("CONFIRMED", null, null));
        doThrow(new IllegalStateException("Cần tải ảnh minh chứng.")).when(completion).completeBooking(42L, 2L);

        mvc.perform(post("/staff/bookings/42/complete").with(user(staff)).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/staff/bookings/42"))
                .andExpect(flash().attribute("error", "Cần tải ảnh minh chứng."));
    }

    @Test
    void adminSessionIsRevokedWhenAccountBecomesInactive() throws Exception {
        when(accountAuthorization.isActiveAdmin(3L)).thenReturn(false);
        mvc.perform(get("/admin/bookings").with(user(admin))).andExpect(status().isForbidden());
    }

    BookingCard card(String status, String payment, String method) {
        return new BookingCard(42L, 1L, 2L, "Khách hàng kiểm thử", "Nhân viên kiểm thử", start, start.plusHours(2), status,
                new BigDecimal("140000"), payment, method, null, null, null);
    }

    @Test
    void rendersAllPublicAndRolePagesWithRealThymeleaf() throws Exception {
        render("/", null, "home"); render("/services", null, "services"); render("/login", null, "login");
        render("/customer/bookings", customer, "customer-bookings");
        render("/customer/bookings/new", customer, "booking-new");
        render("/customer/staff", customer, "customer-staff");
        render("/customer/houses", customer, "customer/houses");
        render("/customer/schedules", customer, "customer-schedules");
        render("/staff/bookings", staff, "staff-bookings"); render("/staff/schedules", staff, "staff-schedules");
        render("/staff/leaves", staff, "staff-leaves");
        render("/staff/profile", staff, "staff-profile");
        render("/admin/bookings", admin, "admin-bookings"); render("/admin/services", admin, "admin-services");
        render("/admin/staff", admin, "admin-staff"); render("/admin/schedules", admin, "admin-schedules");
        render("/admin/feedback", admin, "admin-feedback");
        render("/admin/leaves", admin, "admin-leaves");
        render("/admin/settings", admin, "admin-settings");
        for (PortalUser user : List.of(customer, staff, admin)) {
            render("/" + user.getRole().toLowerCase() + "/bookings/42", user, user.getRole().toLowerCase() + "-detail-confirmed");
        }
    }

    @Test
    void bookingPageStartsWithAvailabilityControlsLockedUntilFilteredOptionsLoad() throws Exception {
        mvc.perform(get("/customer/bookings/new").with(user(customer)))
                .andExpect(status().isOk())
                .andExpect(view().name("booking-new"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("data-booking-submit disabled")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("data-booking-days")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("data-selected-booking-dates")));
    }

    @Test
    void adminLocationAutocompleteIsAdminOnlyAndReturnsVietmapSuggestions() throws Exception {
        var suggestion = new com.housekeeping.management.dto.response.LocationSuggestion("ref-1", "Địa chỉ kiểm thử");
        when(companySettings.locationAutocomplete("Địa chỉ")).thenReturn(List.of(suggestion));

        mvc.perform(get("/admin/settings/location/autocomplete").param("q", "Địa chỉ").with(user(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].refId").value("ref-1"))
                .andExpect(jsonPath("$[0].display").value("Địa chỉ kiểm thử"));
        mvc.perform(get("/admin/settings/location/autocomplete").param("q", "Địa chỉ").with(user(staff)))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminLocationFormRequiresVietmapSelectionAndDoesNotExposeCoordinateInputs() throws Exception {
        String html = mvc.perform(get("/admin/settings").with(user(admin))).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertThat(html).contains("id=\"companyAddressSearch\"", "id=\"companyVietmapRefId\"", "/admin/settings/location/autocomplete",
                        "Không giới hạn địa chỉ Booking")
                .doesNotContain("id=\"latitude\"", "id=\"longitude\"");
    }

    @Test
    void adminScheduleFeedbackAppearsImmediatelyAboveSaveButton() throws Exception {
        String html=mvc.perform(get("/admin/settings").with(user(admin)).flashAttr("scheduleSuccess","Đã lưu lịch tuần."))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertThat(html.indexOf("Giờ hoạt động")).isLessThan(html.indexOf("Đã lưu lịch tuần."));
        assertThat(html.indexOf("Đã lưu lịch tuần.")).isLessThan(html.indexOf("Lưu ngày này"));
        assertThat(html).contains("id=\"schedule-save-feedback\"", "role=\"status\"");
    }

    @Test
    void delayCompensationFeedbackAppearsInsideItsOwnSectionAndPercentInputIsCapped() throws Exception {
        String html=mvc.perform(get("/admin/settings").with(user(admin))
                        .flashAttr("delaySettingsError","Mức giảm tối đa là 30%."))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);

        int section=html.indexOf("Mức giảm khi nhân viên đến trễ");
        int feedback=html.indexOf("Mức giảm tối đa là 30%.");
        assertThat(section).isGreaterThanOrEqualTo(0);
        assertThat(feedback).isGreaterThan(section).isLessThan(html.indexOf("Giảm (%)"));
        assertThat(html).contains("max=\"30\"");
    }

    @Test
    void delayCompensationPostRejectsMoreThanThirtyPercentAtRequestBoundary() throws Exception {
        mvc.perform(post("/admin/settings/delay-compensation").with(user(admin)).with(csrf())
                        .param("tiers[0].minDelayMinutes","1")
                        .param("tiers[0].discountPercent","31"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/settings#delay-compensation"))
                .andExpect(flash().attributeExists("delaySettingsError"));

        verify(delayCompensationSettings, never()).save(anyList());
    }

    @Test
    void adminScheduleSaveRedirectsBackToFeedbackAnchorForSuccessAndFailure() throws Exception {
        LocalDate effective=LocalDate.now(java.time.ZoneId.of("Asia/Ho_Chi_Minh"))
                .with(java.time.temporal.TemporalAdjusters.nextOrSame(java.time.DayOfWeek.MONDAY));
        String redirect="/admin/settings?effectiveFrom="+effective+"#schedule-save-feedback";

        mvc.perform(scheduleSaveRequest(effective))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl(redirect))
                .andExpect(flash().attributeExists("scheduleSuccess"));
        verify(companySettings).saveSchedule(org.mockito.ArgumentMatchers.anyLong(), any(CompanyScheduleVersionForm.class),eq(false));

        doThrow(new IllegalStateException("Lịch ảnh hưởng booking đã xác nhận."))
                .when(companySettings).saveSchedule(org.mockito.ArgumentMatchers.anyLong(), any(CompanyScheduleVersionForm.class),eq(false));
        mvc.perform(scheduleSaveRequest(effective))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl(redirect))
                .andExpect(flash().attribute("scheduleError","Lịch ảnh hưởng booking đã xác nhận."));
    }

    @Test
    void customerAvailabilityLookupReturnsFilteredStaffAndQuarterHourTimes() throws Exception {
        var workDate = LocalDate.now(java.time.ZoneId.of("Asia/Ho_Chi_Minh")).plusDays(2);
        when(bookingAvailability.options(workDate,List.of(10L),2L,java.time.LocalTime.of(10,15),1L,30L))
                .thenReturn(new BookingAvailabilityOptions(List.of(new StaffOption(2L,"Nhân viên kiểm thử","PART_TIME")),List.of("10:15","10:30")));
        mvc.perform(get("/customer/bookings/availability-options").with(user(customer))
                        .param("workDate",workDate.toString()).param("serviceIds","10").param("staffId","2").param("startTime","10:15").param("houseId","30"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.staffOptions[0].id").value(2))
                .andExpect(jsonPath("$.startTimes[0]").value("10:15"));
    }

    @Test
    void customerCalendarReturnsOnlyDatesWithMatchingStaffAvailability() throws Exception {
        LocalDate availableDay=LocalDate.now(java.time.ZoneId.of("Asia/Ho_Chi_Minh")).plusDays(4);
        when(bookingAvailability.availableDates(List.of(10L),2L,customer.getUserId(),30L))
                .thenReturn(new BookingAvailabilityDates(List.of(availableDay),"Chỉ các ngày có lịch mới hiện."));

        mvc.perform(get("/customer/bookings/availability-dates").with(user(customer))
                        .param("serviceIds","10").param("staffId","2").param("houseId","30"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dates[0]").value(availableDay.toString()));
    }

    @Test
    void customerCalendarReturnsSpecificHouseValidationFailure() throws Exception {
        when(bookingAvailability.availableDates(List.of(10L), null, customer.getUserId(), 30L))
                .thenThrow(new IllegalArgumentException("Địa chỉ nhà cần có số nhà và tên đường cụ thể trước khi xem giờ."));

        mvc.perform(get("/customer/bookings/availability-dates").with(user(customer))
                        .param("serviceIds", "10").param("houseId", "30"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Địa chỉ nhà cần có số nhà và tên đường cụ thể trước khi xem giờ."));
    }

    @Test
    void bookingFormRendersRetrySelectionsForFailedDate() throws Exception {
        var retry=java.util.Map.of("houseId",30,"serviceIds",List.of(10),"areaNote","Cổng xanh",
                "days",List.of(java.util.Map.of("date","2026-11-10","staffId",2,"time","09:00")));
        mvc.perform(get("/customer/bookings/new").with(user(customer)).flashAttr("bookingRetryState",retry))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("window.bookingRetryState")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("2026-11-10")));
    }

    @Test
    void staffCanSubmitMultipleAvailabilityIntervalsAndReturnToTheSelectedWeek() throws Exception {
        LocalDate workDate = LocalDate.now(java.time.ZoneId.of("Asia/Ho_Chi_Minh")).plusDays(9);
        LocalDate weekStart = workDate.with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY));
        when(availability.saveForDate(22L, workDate,
                List.of(java.time.LocalTime.of(6, 0), java.time.LocalTime.of(13, 0)),
                List.of(java.time.LocalTime.of(9, 0), java.time.LocalTime.of(17, 0)))).thenReturn(2);

        mvc.perform(post("/staff/schedules/day").with(user(staff)).with(csrf())
                        .param("workDate", workDate.toString()).param("weekStart", weekStart.toString())
                        .param("startTimes", "06:00", "13:00").param("endTimes", "09:00", "17:00"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/staff/schedules?weekStart=" + weekStart));
        verify(availability).saveForDate(22L, workDate,
                List.of(java.time.LocalTime.of(6, 0), java.time.LocalTime.of(13, 0)),
                List.of(java.time.LocalTime.of(9, 0), java.time.LocalTime.of(17, 0)));
    }

    @Test
    void staffGetsBusinessRedirectInsteadOfServerErrorForMalformedAvailabilityTime() throws Exception {
        LocalDate workDate = LocalDate.now(java.time.ZoneId.of("Asia/Ho_Chi_Minh")).plusDays(9);
        LocalDate weekStart = workDate.with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY));

        mvc.perform(post("/staff/schedules/day").with(user(staff)).with(csrf())
                        .param("workDate", workDate.toString()).param("weekStart", weekStart.toString())
                        .param("startTimes", "not-a-time").param("endTimes", "09:00"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/staff/schedules?weekStart=" + weekStart))
                .andExpect(flash().attributeExists("error"));

        verify(availability, never()).saveForDate(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyList(), org.mockito.ArgumentMatchers.anyList());
    }

    @Test
    void staffGetsLoggedFailureMessageInsteadOfSuccessWhenAvailabilityPersistenceFails() throws Exception {
        LocalDate workDate = LocalDate.now(java.time.ZoneId.of("Asia/Ho_Chi_Minh")).plusDays(9);
        LocalDate weekStart = workDate.with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY));
        when(availability.saveForDate(22L, workDate, List.of(java.time.LocalTime.of(8, 0)),
                List.of(java.time.LocalTime.of(12, 0))))
                .thenThrow(new org.springframework.dao.InvalidDataAccessResourceUsageException("database failure"));

        mvc.perform(post("/staff/schedules/day").with(user(staff)).with(csrf())
                        .param("workDate", workDate.toString()).param("weekStart", weekStart.toString())
                        .param("startTimes", "08:00").param("endTimes", "12:00"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/staff/schedules?weekStart=" + weekStart))
                .andExpect(flash().attribute("error", org.hamcrest.Matchers.containsString("lỗi hệ thống")));
    }

    @Test
    void availabilityLookupRejectsStaffAndAdminRoles() throws Exception {
        var workDate = LocalDate.now(java.time.ZoneId.of("Asia/Ho_Chi_Minh")).plusDays(2);
        mvc.perform(get("/customer/bookings/availability-options").with(user(staff))
                        .param("workDate",workDate.toString()).param("serviceIds","10"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/customer/bookings/availability-dates").with(user(staff)).param("serviceIds","10"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/customer/bookings/availability-options").with(user(admin))
                        .param("workDate",workDate.toString()).param("serviceIds","10"))
                .andExpect(status().isForbidden());
    }

    @Test
    void rendersCompletedBookingActionsAndEvidenceForEachRole() throws Exception {
        when(query.booking(eq(42L), any())).thenReturn(card("AWAITING_PAYMENT", "PENDING", "CASH"));
        when(query.details(eq(42L), any())).thenReturn(List.of(new DetailCard(30L, "Dọn dẹp nhà theo giờ", 1, 120,
                new BigDecimal("140000"), List.of(new EvidenceCard(50L, "10:00 · 16/10/2026")))));
        for (PortalUser user : List.of(customer, staff, admin)) {
            render("/" + user.getRole().toLowerCase() + "/bookings/42", user, user.getRole().toLowerCase() + "-detail-completed");
        }
        mvc.perform(get("/customer/bookings/42").with(user(customer))).andExpect(content().string(org.hamcrest.Matchers.containsString("/customer/bookings/42/payment")));
        mvc.perform(get("/staff/bookings/42").with(user(staff))).andExpect(content().string(org.hamcrest.Matchers.containsString("cash-payment")));
    }

    @Test
    void customerCanSubmitTypedAddressWithoutVietmapSuggestion() throws Exception {
        mvc.perform(post("/customer/houses").with(user(customer)).with(csrf())
                        .param("recipientName", "Khách Demo")
                        .param("recipientPhone", "0900000000")
                        .param("streetAddress", "12/3 hẻm 45 Nguyễn Trãi")
                        .param("vietmapRefId", "vietmap-ref-123")
                        .param("addressType", "HOME")
                        .param("defaultAddress", "true")
                        .param("addressNote", "Cổng màu xanh"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/customer/houses?saved"));

        org.mockito.ArgumentCaptor<HouseForm> form = org.mockito.ArgumentCaptor.forClass(HouseForm.class);
        verify(houses).save(eq(customer.getUserId()), isNull(), form.capture());
        assertThat(form.getValue().recipientName()).isEqualTo("Khách Demo");
        assertThat(form.getValue().streetAddress()).isEqualTo("12/3 hẻm 45 Nguyễn Trãi");
        assertThat(form.getValue().vietmapRefId()).isEqualTo("vietmap-ref-123");
    }

    @Test
    void customerHouseFormRequiresStreetAddress() throws Exception {
        mvc.perform(post("/customer/houses").with(user(customer)).with(csrf())
                        .param("recipientName", "Khách Demo")
                        .param("recipientPhone", "0900000000")
                        .param("streetAddress", "")
                        .param("vietmapRefId", "vietmap-ref-123")
                        .param("addressType", "HOME")
                        .param("defaultAddress", "false"))
                .andExpect(status().isOk())
                .andExpect(view().name("customer/houses"))
                .andExpect(model().attributeHasFieldErrors("houseForm", "streetAddress"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Hãy nhập số nhà và tên đường.")));
        verify(houses, never()).save(any(), any(), any());
    }

    @Test
    void adminAndManagerManageCustomersButOtherRolesCannot() throws Exception {
        var active = new com.housekeeping.management.dto.response.PortalViews.CustomerAccountCard(31L, "Đỗ Ngọc Hân",
                "customer2@example.test", "0938502002", true, null, null, 1, 3, 1);
        var locked = new com.housekeeping.management.dto.response.PortalViews.CustomerAccountCard(32L, "Bùi Quang Huy",
                "customer3@example.test", "0938502003", false, "Hủy lịch sát giờ nhiều lần",
                LocalDateTime.of(2026, 11, 12, 9, 30), 1, 0, 0);
        when(customerManagement.findCustomers(3L, null)).thenReturn(List.of(active, locked));
        when(customerManagement.findCustomers(5L, null)).thenReturn(List.of(active));
        render("/admin/customers", admin, "admin-customers");
        mvc.perform(get("/admin/customers").with(user(admin)))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Hủy lịch sát giờ nhiều lần")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("1 Booking chưa hoàn tất")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("/admin/customers/31/lock")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("/admin/customers/32/unlock")));
        mvc.perform(get("/admin/customers").with(user(manager))).andExpect(status().isOk());
        mvc.perform(get("/admin/customers").with(user(customer))).andExpect(status().isForbidden());
        mvc.perform(get("/admin/customers").with(user(staff))).andExpect(status().isForbidden());

        mvc.perform(post("/admin/customers/31/lock").with(user(admin)).with(csrf())
                        .param("reason", "Nhiều lần hủy lịch sát giờ").param("q", "Hân"))
                .andExpect(redirectedUrl("/admin/customers?q=H%C3%A2n"));
        verify(customerManagement).lock(3L, 31L, "Nhiều lần hủy lịch sát giờ");

        org.mockito.Mockito.doThrow(new IllegalArgumentException("Vui lòng nhập lý do khóa tài khoản (từ 5 đến 500 ký tự)."))
                .when(customerManagement).lock(3L, 31L, null);
        mvc.perform(post("/admin/customers/31/lock").with(user(admin)).with(csrf()))
                .andExpect(redirectedUrl("/admin/customers"))
                .andExpect(flash().attribute("error", "Vui lòng nhập lý do khóa tài khoản (từ 5 đến 500 ký tự)."));

        mvc.perform(post("/admin/customers/32/unlock").with(user(admin)).with(csrf()))
                .andExpect(redirectedUrl("/admin/customers"));
        verify(customerManagement).unlock(3L, 32L);
        mvc.perform(post("/admin/customers/31/lock").with(user(customer)).with(csrf()).param("reason", "Tự khóa thử"))
                .andExpect(status().isForbidden());
    }

    @Test
    void staffAndAdminLocksForwardTheRequiredReason() throws Exception {
        mvc.perform(post("/admin/staff/9/delete").with(user(admin)).with(csrf()).param("reason", "Nghỉ việc từ 15/11"))
                .andExpect(redirectedUrl("/admin/staff"));
        verify(staffManagement).deactivate(3L, 9L, "Nghỉ việc từ 15/11");

        mvc.perform(post("/admin/accounts/7/status").with(user(manager)).with(csrf())
                        .param("status", "INACTIVE").param("reason", "Vi phạm quy trình duyệt hoàn tiền"))
                .andExpect(redirectedUrl("/admin/accounts"));
        verify(adminAccounts).updateStatus(5L, 7L, "INACTIVE", "Vi phạm quy trình duyệt hoàn tiền");

        when(adminAccounts.findAll(5L)).thenReturn(List.of(new com.housekeeping.management.dto.response.PortalViews.AdminAccountCard(
                7L, "Võ Thanh Tùng", "admin@example.test", "0900000004", "INACTIVE",
                "Vi phạm quy trình duyệt hoàn tiền", LocalDateTime.of(2026, 11, 12, 10, 0))));
        mvc.perform(get("/admin/accounts").with(user(manager)))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Vi phạm quy trình duyệt hoàn tiền")));
    }

    @Test
    void accountLockedDuringASessionIsSignedOutOnTheNextRequest() throws Exception {
        when(accountAuthorization.lockReasonIfInactive(customer.getUserId())).thenReturn(java.util.Optional.of("Gian lận thanh toán"));

        MvcResult result = mvc.perform(get("/customer/bookings").with(user(customer)))
                .andExpect(redirectedUrl("/login?locked")).andReturn();
        var session = (org.springframework.mock.web.MockHttpSession) result.getRequest().getSession(false);
        assertThat(session.getAttribute("SPRING_SECURITY_CONTEXT")).isNull();
        mvc.perform(get("/login").param("locked", "").session(session))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Gian lận thanh toán")));
        verifyNoInteractions(cancellation);

        when(accountAuthorization.lockReasonIfInactive(staff.getUserId())).thenReturn(java.util.Optional.of(""));
        mvc.perform(get("/staff/bookings").with(user(staff))).andExpect(redirectedUrl("/login?locked"));
    }

    @Test
    void lockedAccountSeesReasonOnlyAfterTheCorrectPassword() throws Exception {
        when(identity.loadUserByUsername(customer.getUsername())).thenReturn(new PortalUser(1L, 1L, customer.getUsername(),
                encoder.encode("correct-password"), "Test", "CUSTOMER", true, "Hủy lịch sát giờ nhiều lần"));

        MvcResult wrong = mvc.perform(post("/login").with(csrf()).param("email", customer.getUsername()).param("password", "wrong"))
                .andExpect(redirectedUrl("/login?error")).andReturn();
        assertThat(wrong.getRequest().getSession().getAttribute("LOGIN_LOCK_REASON")).isNull();

        MvcResult locked = mvc.perform(post("/login").with(csrf()).param("email", customer.getUsername()).param("password", "correct-password"))
                .andExpect(redirectedUrl("/login?locked")).andReturn();
        var session = (org.springframework.mock.web.MockHttpSession) locked.getRequest().getSession(false);
        assertThat(session.getAttribute("SPRING_SECURITY_CONTEXT")).isNull();

        mvc.perform(get("/login").param("locked", "").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Tài khoản của bạn đã bị khóa.")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Hủy lịch sát giờ nhiều lần")));
        assertThat(session.getAttribute("LOGIN_LOCK_REASON")).isNull();
    }

    @Test
    void customerCanSubmitRatedFeedbackAndAdminCanOpenFeedbackInbox() throws Exception {
        mvc.perform(post("/customer/bookings/42/feedback").with(user(customer)).with(csrf())
                        .param("rating", "4").param("topics", "CLEANING_QUALITY")
                        .param("content", "Nhân viên làm việc cẩn thận."))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/customer/bookings/42"));
        verify(feedbackSubmission).submit(eq(42L), eq(1L), any(), eq(false), isNull());

        when(query.booking(eq(42L), any())).thenReturn(card("COMPLETED", "SUCCESS", "CASH"));
        mvc.perform(get("/customer/bookings/42").with(user(customer)))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Rất không hài lòng")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Dịch vụ có xứng với giá tiền?")));

        when(query.feedback()).thenReturn(List.of(new FeedbackCard(90L, 42L, "Khách kiểm thử", 4,
                List.of("Đúng giờ", "Chất lượng dọn dẹp"), "Nhân viên làm việc cẩn thận.", null, "OPEN",
                "COMPLETED", java.time.LocalDateTime.now(), new BigDecimal("140000"), "Nhân viên kiểm thử")));
        mvc.perform(get("/admin/feedback").with(user(admin)))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Phản hồi khách hàng")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("4/5 sao")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Nhân viên kiểm thử")));
        mvc.perform(get("/admin/feedback").param("bookingId", "42").with(user(admin)))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("feedback-booking-42")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Đang xử lý")));
        mvc.perform(get("/admin/feedback/booking/42").with(user(admin)))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/feedback/90"));

        when(query.booking(eq(42L), any())).thenReturn(new BookingCard(42L, 1L, 2L,
                "Khách hàng kiểm thử", "Nhân viên kiểm thử", start, start.plusHours(2), "COMPLETED",
                new BigDecimal("140000"), "SUCCESS", "CASH", null, null, "OPEN"));
        mvc.perform(get("/admin/bookings/42").with(user(admin)))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Mở phản hồi của booking này")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("/admin/feedback/booking/42")));

        mvc.perform(get("/admin/feedback/90").with(user(admin)))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Chi tiết phản hồi")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Nhân viên làm việc cẩn thận.")));
        mvc.perform(post("/admin/feedback/90/reply").with(user(admin)).with(csrf()).param("reply", "Cảm ơn bạn đã góp ý."))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/admin/feedback/90"));
        verify(feedbackService).reply(90L, 3L, "Cảm ơn bạn đã góp ý.");
        mvc.perform(post("/admin/feedback/90/resolve").with(user(admin)).with(csrf()))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/admin/feedback/90"));
        verify(feedbackService).resolve(90L, 3L);
    }

    @Test
    void customerCanSubmitFeedbackAndIncidentEvidenceInOneMultipartRequest() throws Exception {
        var image = new MockMultipartFile("images", "proof.png", "image/png", new byte[] {1, 2, 3});
        mvc.perform(multipart("/customer/bookings/42/feedback").file(image).with(user(customer)).with(csrf())
                        .param("rating", "2").param("topics", "CLEANING_QUALITY")
                        .param("content", "Còn bụi và có đồ hư.")
                        .param("reportIncident", "true").param("incidentTypes", "CLEANLINESS", "PROPERTY_DAMAGE")
                        .param("incidentDescription", "Còn bụi và có đồ hư."))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/customer/bookings/42"));

        var form = org.mockito.ArgumentCaptor.forClass(com.housekeeping.management.dto.request.PortalForms.FeedbackForm.class);
        var images = org.mockito.ArgumentCaptor.forClass(List.class);
        verify(feedbackSubmission).submit(eq(42L), eq(1L), form.capture(), eq(false), images.capture());
        assertThat(form.getValue().reportIncident()).isTrue();
        assertThat(form.getValue().incidentTypes()).containsExactly("CLEANLINESS", "PROPERTY_DAMAGE");
        assertThat(form.getValue().incidentDescription()).isEqualTo("Còn bụi và có đồ hư.");
        assertThat(images.getValue()).containsExactly(image);
    }

    @Test
    void feedbackFormsShowSpecificValidationErrorsInsteadOfGenericServiceErrors() throws Exception {
        mvc.perform(post("/admin/feedback/90/reply").with(user(admin)).with(csrf()).param("reply", ""))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("error", "Vui lòng nhập nội dung phản hồi khách hàng."));

        mvc.perform(post("/admin/feedback/cases/99/review").with(user(admin)).with(csrf())
                        .param("caseStatus", "VERIFIED")
                        .param("internalFinding", "x".repeat(4001)))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("error", "Kết luận nội bộ tối đa 4.000 ký tự."));
        verify(feedbackCases, never()).review(anyLong(), anyLong(), any());
    }

    @Test
    void customerCanOpenMultiIssueCaseAndStaffCannotAccessFinanceOrFeedbackEvidence() throws Exception {
        var itemDetails=new com.housekeeping.management.dto.request.PortalForms.IncidentItemDetails("Bàn gỗ",new BigDecimal("500000"),18);
        when(feedbackCases.open(eq(42L),eq(1L),anyList(),eq("Đồ bị hư và nhà chưa sạch"),eq(itemDetails),isNull(),eq(true))).thenReturn(99L);
        mvc.perform(multipart("/customer/bookings/42/feedback/incident").with(user(customer)).with(csrf())
                        .param("incidentTypes","PROPERTY_DAMAGE","CLEANLINESS")
                        .param("description","Đồ bị hư và nhà chưa sạch")
                        .param("itemDescription","Bàn gỗ").param("estimatedItemValue","500000").param("itemAgeMonths","18")
                        .file(new org.springframework.mock.web.MockMultipartFile("images","proof.png","image/png",new byte[]{1})))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/customer/bookings/42"));
        verify(feedbackCases).open(42L,1L,List.of("PROPERTY_DAMAGE","CLEANLINESS"),"Đồ bị hư và nhà chưa sạch",itemDetails,null,true);

        mvc.perform(get("/admin/finance").with(user(staff))).andExpect(status().isForbidden());
        mvc.perform(get("/feedback-case-evidence/1").with(user(staff))).andExpect(status().isForbidden());
    }

    @Test
    void customerAndAdminCanAppendIncidentConversationMessages() throws Exception {
        when(feedbackCases.addCustomerMessage(99L,42L,1L,"Bổ sung chi tiết",false)).thenReturn(501L);
        mvc.perform(post("/customer/bookings/42/feedback/incident/99/messages").with(user(customer)).with(csrf())
                        .param("message","Bổ sung chi tiết"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/customer/bookings/42"));
        verify(feedbackCases).addCustomerMessage(99L,42L,1L,"Bổ sung chi tiết",false);

        mvc.perform(post("/customer/bookings/42/feedback/incident/99/messages").with(user(staff)).with(csrf())
                        .param("message","Không được phép"))
                .andExpect(status().isForbidden());

        when(feedbackCases.addAdminMessage(99L,3L,"Đã tiếp nhận, chúng tôi sẽ xác minh.",false)).thenReturn(502L);
        mvc.perform(post("/admin/feedback/cases/99/messages").with(user(admin)).with(csrf())
                        .param("message","Đã tiếp nhận, chúng tôi sẽ xác minh."))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/feedback/cases/99#feedback-messages-99"));
        verify(feedbackCases).addAdminMessage(99L,3L,"Đã tiếp nhận, chúng tôi sẽ xác minh.",false);
    }

    @Test
    void managerSeesIncidentConversationAndCanReviewLinkedFinancialEntry() throws Exception {
        when(query.feedback()).thenReturn(List.of(new FeedbackCard(90L,42L,"Khách kiểm thử",1,List.of("Mục khác"),
                "Đồ bị hư",null,"OPEN","COMPLETED",java.time.LocalDateTime.now(),new BigDecimal("140000"),"Nhân viên kiểm thử")));
        when(feedbackCases.adminCase(90L,5L)).thenReturn(new IncidentCard(99L,90L,42L,1L,2L,
                List.of("MISSING_PROPERTY"),"Mất đồ","RECEIVED",null,"OTHER",null,true,null,null,null,null,null,
                java.time.LocalDateTime.now(),null));
        when(feedbackCases.adminCaseById(99L,5L)).thenReturn(new IncidentCard(99L,90L,42L,1L,2L,
                List.of("MISSING_PROPERTY"),"Mất đồ","RECEIVED",null,null,null,false,null,null,null,null,null,
                java.time.LocalDateTime.now(),null));
        when(feedbackCases.adminMessages(99L,5L)).thenReturn(List.of(new FeedbackCaseMessageCard(501L,99L,1L,
                "CUSTOMER","Khách kiểm thử","Mất đồ",java.time.LocalDateTime.now(),false,false,List.of())));
        when(feedbackCases.actions(99L,5L)).thenReturn(List.of());
        when(financials.forBooking(42L,5L)).thenReturn(List.of(new FinancialEntryCard(700L,42L,99L,
                "COMPENSATION",new BigDecimal("250000"),"Bồi thường tài sản", "PENDING_APPROVAL",null,null,null,
                false,3L,null,null,null,null,java.time.LocalDateTime.now(),null)));

        mvc.perform(get("/admin/feedback").with(user(manager)))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Khách kiểm thử · Lịch #42")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Chờ Manager")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Xem chi tiết")));

        mvc.perform(get("/admin/feedback").param("status", "pending").param("bookingId", "42").with(user(manager)))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("feedback-booking-42")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Có hồ sơ sự cố")));
        mvc.perform(get("/admin/feedback").param("status", "resolved").param("bookingId", "42").with(user(manager)))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Không có phản hồi trong trạng thái này")));
        mvc.perform(get("/admin/feedback/cases/99").with(user(manager)))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Chi tiết phản hồi")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Trao đổi với khách")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Chỉ hiển thị nút duyệt sau khi khách đồng ý chính xác số tiền này")))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("Trạng thái xác minh"))))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("Lưu xác minh và gửi phản hồi cho khách"))));
    }

    @Test
    void adminCanProposeCompensationFromIncidentDetailAndReturnToThatCase() throws Exception {
        when(query.feedback()).thenReturn(List.of(new FeedbackCard(90L,42L,"Khách kiểm thử",1,List.of("Mục khác"),
                "Tài sản bị hỏng",null,"OPEN","COMPLETED",java.time.LocalDateTime.now(),new BigDecimal("140000"),"Nhân viên kiểm thử")));
        when(feedbackCases.adminCaseById(99L,3L)).thenReturn(new IncidentCard(99L,90L,42L,1L,2L,
                List.of("PROPERTY_DAMAGE"),"Tài sản bị hỏng","INVESTIGATING",null,null,
                "Đã xác minh lỗi thuộc về công ty.",false,null,null,null,null,null,java.time.LocalDateTime.now(),null));
        when(feedbackCases.adminMessages(99L,3L)).thenReturn(List.of());
        when(feedbackCases.actions(99L,3L)).thenReturn(List.of());
        when(financials.forBooking(42L,3L)).thenReturn(List.of());

        mvc.perform(get("/admin/feedback/cases/99").with(user(admin)))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Ghi chú xác minh nội bộ")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Đề xuất một khoản bồi thường")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Số tiền bồi thường đề xuất (₫)")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Căn cứ xác định số tiền")));

        mvc.perform(post("/admin/feedback/cases/99/compensation").with(user(admin)).with(csrf())
                        .param("amount","250000.00").param("reason","Bồi thường tài sản bị hỏng"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/feedback/cases/99#compensation-entry-0"));
        verify(financials).propose(eq(42L),eq(99L),eq(3L),any());
    }

    @Test
    void staffSeesAndAcknowledgesOnlyTheirApprovedIncidentNotice() throws Exception {
        when(incidentNotices.ownApprovedActions(2L)).thenReturn(List.of(new PortalViews.StaffActionNoticeCard(
                77L,"COACHING","Cần kiểm tra kỹ mặt bàn sau khi dọn.",java.time.LocalDateTime.now(),java.time.LocalDateTime.now(),null)));
        mvc.perform(get("/staff/incident-actions").with(user(staff)))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Cần kiểm tra kỹ mặt bàn")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("không đồng nghĩa với thừa nhận lỗi")));
        mvc.perform(post("/staff/incident-actions/77/acknowledge").with(user(staff)).with(csrf()))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/staff/incident-actions"));
        verify(incidentNotices).acknowledge(77L,2L);
        mvc.perform(get("/staff/incident-actions").with(user(customer))).andExpect(status().isForbidden());
    }

    @Test
    void onlyStaffCanSubmitPrivateIncidentExplanation() throws Exception {
        mvc.perform(post("/staff/incident-actions/77/response").with(user(staff)).with(csrf())
                        .param("response","Tôi xin giải trình sự việc."))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/staff/incident-actions"));
        verify(incidentNotices).respond(77L,2L,"Tôi xin giải trình sự việc.",false);
        mvc.perform(post("/staff/incident-actions/77/response").with(user(customer)).with(csrf())
                        .param("response","Không có quyền"))
                .andExpect(status().isForbidden());
    }

    @Test
    void awaitingPaymentOffersOnlineCheckoutAndAssignedStaffCashConfirmation() throws Exception {
        BookingCard awaitingOnline = new BookingCard(42L, 1L, 2L, "Khách hàng kiểm thử", "Nhân viên kiểm thử", start,
                start.plusHours(2), "AWAITING_PAYMENT", new BigDecimal("140000"), "PENDING", "ONLINE", null, null, null);
        when(query.booking(eq(42L), any())).thenReturn(awaitingOnline);
        when(query.details(eq(42L), any())).thenReturn(List.of());

        mvc.perform(get("/customer/bookings/42").with(user(customer)))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Thanh toán trực tuyến qua VNPay")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Phương thức: VNPay")));
        mvc.perform(get("/staff/bookings/42").with(user(staff)))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("cash-payment")));
    }

    @Test
    void staffCanConfirmCompletionOnlyAfterWorkStartedAndEvidenceIsReady() throws Exception {
        BookingCard started = new BookingCard(42L, 1L, 2L, "Khách hàng kiểm thử", "Nhân viên kiểm thử", start,
                start.plusHours(2), "CONFIRMED", new BigDecimal("140000"), null, null, null, null, null,
                null, null, null, null, start, "0901234567", start, null, null,
                null, null, null, start.plusMinutes(5), 0, BigDecimal.ZERO, BigDecimal.ZERO);
        when(query.booking(eq(42L), any())).thenReturn(started);
        when(query.bookingImages(eq(42L), any())).thenReturn(List.of(new EvidenceCard(50L, "10:00 · 22/10/2026")));
        when(query.details(eq(42L), any())).thenReturn(List.of(new DetailCard(30L, "Dọn dẹp nhà theo giờ", 1,
                120, new BigDecimal("140000"), List.of(new EvidenceCard(50L, "10:00 · 22/10/2026")))));

        String page = mvc.perform(get("/staff/bookings/42").with(user(staff)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(page).contains("Xác nhận hoàn thành");
        assertThat(page).contains("class=\"btn btn-primary w-100\" type=\"submit\">");
    }

    @Test
    void customerCanAcknowledgeStaffClaimAndStaffMustSelfConfirmArrival() throws Exception {
        String customerPage = mvc.perform(get("/customer/bookings/42").with(user(customer)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(customerPage).doesNotContain("Xác nhận giờ Staff khai là đúng");

        String staffPage = mvc.perform(get("/staff/bookings/42").with(user(staff)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(staffPage).contains("type=\"submit\">Tôi đã đến</button>", "bạn có thể bắt đầu ngay hoặc chờ đến giờ hẹn")
                .doesNotContain("Nút xác nhận sẽ mở lúc");

        BookingCard arrivedEarly = new BookingCard(42L, 1L, 2L, "Khách hàng kiểm thử", "Nhân viên kiểm thử",
                start, start.plusHours(2), "CONFIRMED", new BigDecimal("140000"), null, null, null, null, null,
                null, null, null, null, null, "0901234567", OffsetDateTime.now(ZoneOffset.ofHours(7)),
                null, null, null, null, null, null, null, BigDecimal.ZERO, BigDecimal.ZERO, null, List.of(), List.of(), null);
        when(query.booking(eq(42L), any())).thenReturn(arrivedEarly);
        String customerAcknowledgement = mvc.perform(get("/customer/bookings/42").with(user(customer)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(customerAcknowledgement).contains("Xác nhận giờ Staff khai là đúng",
                "Staff vẫn phải tự xác nhận đến trước khi bắt đầu");
        String readyToStart = mvc.perform(get("/staff/bookings/42").with(user(staff)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(readyToStart).contains(">Bắt đầu công việc</button>").doesNotContain("Chỉ bắt đầu từ giờ hẹn");
    }

    @Test
    void staffCanReportIncidentAndConfirmArrivalButCustomerCannotUseStaffRoutes() throws Exception {
        String eta = start.plusMinutes(15).toLocalDateTime().toString();
        mvc.perform(post("/staff/bookings/42/incident").with(user(staff)).with(csrf())
                        .param("note", "Kẹt xe, dự kiến trễ 15 phút").param("expectedArrivalAt", eta))
                        .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/staff/bookings/42"))
                .andExpect(flash().attributeExists("success"));
        verify(incidents).report(42L, 2L, "Kẹt xe, dự kiến trễ 15 phút", java.time.LocalDateTime.parse(eta));

        mvc.perform(post("/staff/bookings/42/arrived").with(user(staff)).with(csrf()))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/staff/bookings/42"));
        verify(arrival).confirmStaffArrival(42L, 2L);

        mvc.perform(post("/staff/bookings/42/incident").with(user(customer)).with(csrf())
                        .param("note", "Kẹt xe").param("expectedArrivalAt", eta))
                .andExpect(status().isForbidden());
        mvc.perform(post("/staff/bookings/42/arrived").with(user(customer)).with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    void customerCanSubmitDelayDecisionAndAssignedStaffCanStartThroughOwnRoute() throws Exception {
        mvc.perform(post("/customer/bookings/42/incident-response").with(user(customer)).with(csrf())
                        .param("response", "ACCEPTED"))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/customer/bookings/42"));
        verify(incidents).respond(42L, 1L, "ACCEPTED");

        mvc.perform(post("/staff/bookings/42/start").with(user(staff)).with(csrf()))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/staff/bookings/42"));
        verify(completion).startBooking(42L, 2L);

        mvc.perform(post("/staff/bookings/42/start").with(user(customer)).with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    void customerAndAdminCanSeeIncidentWhileAssignedStaffGetsCustomerCallLink() throws Exception {
        var reportedAt = OffsetDateTime.now(ZoneOffset.ofHours(7));
        BookingCard incident = new BookingCard(42L, 1L, 2L, "Khách hàng kiểm thử", "Nhân viên kiểm thử",
                start, start.plusHours(2), "CONFIRMED", new BigDecimal("140000"), null, null, null, null, null,
                null, null, null, null, null, "0901234567", null, reportedAt, "Kẹt xe, dự kiến trễ 15 phút");
        when(query.booking(eq(42L), any())).thenReturn(incident);

        String customerPage = mvc.perform(get("/customer/bookings/42").with(user(customer)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(customerPage).contains("Báo cáo sự cố", "Kẹt xe, dự kiến trễ 15 phút")
                .doesNotContain("tel:0901234567");

        String staffPage = mvc.perform(get("/staff/bookings/42").with(user(staff)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(staffPage).contains("tel:0901234567");

        String adminPage = mvc.perform(get("/admin/bookings/42").with(user(admin)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(adminPage).contains("Báo cáo sự cố", "Kẹt xe, dự kiến trễ 15 phút");
    }

    @Test
    void staffCompletionButtonIsDisabledUntilBookingEvidenceExists() throws Exception {
        when(query.booking(eq(42L), any())).thenReturn(card("CONFIRMED", null, null));
        when(query.bookingImages(eq(42L), any())).thenReturn(List.of());
        String page = mvc.perform(get("/staff/bookings/42").with(user(staff)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(page).contains("Bắt đầu công việc", "type=\"submit\" disabled=\"disabled\"")
                .doesNotContain("Xác nhận hoàn thành");
    }

    @Test
    void rendersEmptyStatesWithoutInventedData() throws Exception {
        when(query.services()).thenReturn(List.of()); when(query.staff(any())).thenReturn(List.of());
        when(query.schedules(any())).thenReturn(List.of()); when(query.bookings(any())).thenReturn(List.of());
        render("/", null, "home-empty"); render("/customer/bookings", customer, "bookings-empty");
        render("/customer/bookings/new", customer, "booking-empty"); render("/customer/staff", customer, "staff-empty");
        render("/admin/services", admin, "services-empty"); render("/admin/schedules", admin, "schedules-empty");
    }

    @Test
    void guestAndWrongRoleCannotUseProtectedPages() throws Exception {
        mvc.perform(get("/customer/bookings")).andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/login"));
        mvc.perform(get("/customer/houses").with(user(admin))).andExpect(status().isForbidden());
        mvc.perform(get("/admin/bookings").with(user(customer))).andExpect(status().isForbidden());
        mvc.perform(get("/staff/bookings").with(user(customer))).andExpect(status().isForbidden());
        mvc.perform(post("/admin/services").with(user(customer)).with(csrf())).andExpect(status().isForbidden());
        verifyNoInteractions(registration, cancellation, availability, follows, serviceManagement, staffManagement,
                reservations, payments, storage, houses);
    }

    @Test
    void staffSelfServicePassesUsersIdentityInsteadOfStaffAccountId() throws Exception {
        AvailabilityForm form = new AvailabilityForm(java.time.LocalDate.of(2026, 11, 9),
                java.time.LocalTime.of(9, 0), java.time.LocalTime.of(12, 0));
        mvc.perform(post("/staff/schedules").with(user(staff)).with(csrf())
                        .param("workDate", "2026-11-09").param("startTime", "09:00").param("endTime", "12:00")
                        .param("weekStart", "2026-11-09"))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/staff/schedules?weekStart=2026-11-09"));
        verify(availability).save(22L, null, form);

        mvc.perform(post("/staff/schedules/20/delete").with(user(staff)).with(csrf()).param("weekStart", "2026-11-09"))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/staff/schedules?weekStart=2026-11-09"));
        verify(availability).delete(22L, 20L);
    }

    @Test
    void staffSchedulePageRendersCompanyWindowsForMultiIntervalControls() throws Exception {
        when(query.isPartTime(2L)).thenReturn(true);
        when(companySettings.scheduleWindows()).thenReturn(java.util.stream.IntStream.rangeClosed(1, 7)
                .mapToObj(day -> new CompanySettingsService.ScheduleWindow(LocalDate.of(1900, 1, 1), day,
                        false, java.time.LocalTime.of(8, 0), java.time.LocalTime.of(18, 0)))
                .toList());

        String page = mvc.perform(get("/staff/schedules").with(user(staff)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(page).contains("id=\"company-operating-hours-source\"", "data-company-window",
                "data-start=\"08:00\"", "data-end=\"18:00\"", "data-staff-multi-form",
                "data-add-interval", "name=\"startTimes\"");
    }

    @Test
    void loginCreatesSessionAndPortalRedirectsByDatabaseRole() throws Exception {
        for (PortalUser user : List.of(customer, staff, admin)) {
            PortalUser stored = new PortalUser(user.getAccountId(), user.getUsername(), encoder.encode("test-password"), user.getDisplayName(), user.getRole());
            when(identity.loadUserByUsername(user.getUsername())).thenReturn(stored);
            MvcResult result = mvc.perform(post("/login").with(csrf()).param("email", user.getUsername()).param("password", "test-password"))
                    .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/portal")).andReturn();
            SecurityContext context = (SecurityContext) result.getRequest().getSession().getAttribute("SPRING_SECURITY_CONTEXT");
            assertThat(((PortalUser) context.getAuthentication().getPrincipal()).getRole()).isEqualTo(user.getRole());
            mvc.perform(get("/portal").with(user(user))).andExpect(redirectedUrl("/" + user.getRole().toLowerCase() + "/bookings"));
        }
    }

    @Test
    void loginReturnsCustomerToBookingPageTheyRequestedBeforeAuthenticating() throws Exception {
        PortalUser stored = new PortalUser(customer.getAccountId(), customer.getUsername(), encoder.encode("test-password"), customer.getDisplayName(), "CUSTOMER");
        when(identity.loadUserByUsername(customer.getUsername())).thenReturn(stored);
        MvcResult anonymous = mvc.perform(get("/customer/bookings/new"))
                .andExpect(status().is3xxRedirection()).andReturn();
        var session = (org.springframework.mock.web.MockHttpSession) anonymous.getRequest().getSession(false);

        mvc.perform(post("/login").session(session).with(csrf())
                        .param("email", customer.getUsername()).param("password", "test-password"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", org.hamcrest.Matchers.startsWith("http://localhost/customer/bookings/new")));
    }

    @Test
    void incorrectPasswordDoesNotAuthenticate() throws Exception {
        when(identity.loadUserByUsername(customer.getUsername())).thenReturn(new PortalUser(1L, customer.getUsername(), encoder.encode("correct"), "Test", "CUSTOMER"));
        MvcResult result = mvc.perform(post("/login").with(csrf()).param("email", customer.getUsername()).param("password", "incorrect"))
                .andExpect(redirectedUrl("/login?error")).andReturn();
        assertThat(result.getRequest().getSession().getAttribute("SPRING_SECURITY_CONTEXT")).isNull();
    }

    @Test
    void csrfIsRequiredForMutationsAndLogout() throws Exception {
        mvc.perform(post("/customer/bookings/42/cancel").with(user(customer))).andExpect(status().isForbidden());
        mvc.perform(post("/logout").with(user(customer))).andExpect(status().isForbidden());
        verifyNoInteractions(cancellation);
    }

    @Test
    void bookingUsesSessionCustomerServerServiceSelectionAndVietnamOffset() throws Exception {
        when(reservations.createBooking(anyLong(), anyLong(), anyLong(), any(), anyList(), any())).thenReturn(42L);
        MvcResult created = mvc.perform(post("/customer/bookings").with(user(customer)).with(csrf())
                .param("customerId", "999").param("totalPrice", "1").param("staffId", "2").param("houseId", "30")
                .param("startAt", "2026-11-01T08:00").param("serviceIds", "10", "11").param("areaNote", "Entryway"))
            .andExpect(redirectedUrl("/customer/bookings/42")).andExpect(flash().attributeExists("success")).andReturn();
        assertThat(created.getFlashMap().get("success").toString()).contains("xác nhận trên trang chi tiết");
        verify(reservations).createBooking(eq(1L), eq(2L), eq(30L), eq(java.time.LocalDateTime.parse("2026-11-01T08:00")),
                eq(List.of(10L, 11L)), eq("Entryway"));
    }

    @Test
    void multiDayBookingCreatesIndependentAppointmentsAndReportsUnavailableDates() throws Exception {
        when(reservations.createBooking(eq(1L), eq(2L), eq(30L), any(), eq(List.of(10L)), eq("Entryway")))
                .thenReturn(42L)
                .thenThrow(new IllegalStateException("Staff không còn khả dụng."));

        MvcResult partial = mvc.perform(post("/customer/bookings/multiple").with(user(customer)).with(csrf())
                        .param("houseId", "30").param("serviceIds", "10").param("areaNote", "Entryway")
                        .param("workDates", "2026-11-10", "2026-11-12")
                        .param("staffIds", "2", "2").param("startTimes", "09:00", "10:00"))
                .andExpect(redirectedUrl("/customer/bookings/new?houseId=30"))
                .andExpect(flash().attributeExists("bookingRetryState"))
                .andExpect(flash().attributeExists("warning")).andReturn();
        assertThat(partial.getFlashMap().get("warning").toString()).contains("xác nhận trên từng lịch");

        verify(reservations, org.mockito.Mockito.times(2)).createBooking(eq(1L), eq(2L), eq(30L), any(),
                eq(List.of(10L)), eq("Entryway"));
    }

    @Test
    void multiDayBookingRejectsMismatchedListsBeforeCreatingAnything() throws Exception {
        mvc.perform(post("/customer/bookings/multiple").with(user(customer)).with(csrf())
                        .param("houseId", "30").param("serviceIds", "10")
                        .param("workDates", "2026-11-10", "2026-11-12")
                        .param("staffIds", "2").param("startTimes", "09:00", "10:00"))
                .andExpect(redirectedUrl("/customer/bookings/new"))
                .andExpect(flash().attributeExists("error"));
        verifyNoInteractions(reservations);
    }

    @Test
    void invalidBookingRedisplaysInputsWithoutCallingReservation() throws Exception {
        mvc.perform(post("/customer/bookings").with(user(customer)).with(csrf()).param("staffId", "2").param("houseId", "20")
                .param("startAt", "2026-11-01T08:00")).andExpect(status().isOk()).andExpect(view().name("booking-new"));
        verifyNoInteractions(reservations);
    }

    @Test
    void ownershipFailureStopsPaymentBeforeExistingServiceIsCalled() throws Exception {
        when(query.booking(42L, customer)).thenThrow(new AccessDeniedException("Forbidden"));
        mvc.perform(post("/customer/bookings/42/payment").with(user(customer)).with(csrf()))
                .andExpect(status().isForbidden());
        verifyNoInteractions(onlinePayments);
    }

    @Test
    void customerOnlinePaymentRedirectsToVnpayCheckout() throws Exception {
        String checkoutUrl = "https://sandbox.vnpayment.vn/paymentv2/vpcpay.html?vnp_TxnRef=1001";
        when(query.booking(eq(42L), any())).thenReturn(card("AWAITING_PAYMENT", "PENDING", "ONLINE"));
        when(onlinePayments.checkout(eq(42L), eq(customer.getAccountId()), anyString()))
                .thenReturn(new OnlinePaymentService.Checkout(checkoutUrl, false));

        mvc.perform(post("/customer/bookings/42/payment").with(user(customer)).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl(checkoutUrl));
        verify(onlinePayments).checkout(eq(42L), eq(customer.getAccountId()), anyString());
        verifyNoInteractions(payments);
    }

    @Test
    void onlinePaymentForAttemptVnpayAlreadyChargedReturnsToBookingAsPaid() throws Exception {
        when(query.booking(eq(42L), any())).thenReturn(card("AWAITING_PAYMENT", "PENDING", "ONLINE"));
        when(onlinePayments.checkout(eq(42L), eq(customer.getAccountId()), anyString()))
                .thenReturn(new OnlinePaymentService.Checkout(null, true));

        mvc.perform(post("/customer/bookings/42/payment").with(user(customer)).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/customer/bookings/42"))
                .andExpect(flash().attribute("success", org.hamcrest.Matchers.containsString("Thanh toán thành công")));
    }

    @Test
    void vnpayReturnIsPublicAndShowsPaidResultOnTheBooking() throws Exception {
        when(onlinePayments.handleReturn(anyMap()))
                .thenReturn(new OnlinePaymentService.ReturnOutcome(42L, ReturnStatus.PAID));

        mvc.perform(get("/payments/vnpay/return").param("vnp_TxnRef", "1001").param("vnp_ResponseCode", "00"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/customer/bookings/42"))
                .andExpect(flash().attribute("success", org.hamcrest.Matchers.containsString("Thanh toán thành công")));
        verify(onlinePayments).handleReturn(argThat(params -> "1001".equals(params.get("vnp_TxnRef"))));
    }

    @Test
    void vnpayReturnForFailedPaymentShowsAnError() throws Exception {
        when(onlinePayments.handleReturn(anyMap()))
                .thenReturn(new OnlinePaymentService.ReturnOutcome(42L, ReturnStatus.FAILED));

        mvc.perform(get("/payments/vnpay/return").param("vnp_TxnRef", "1001").param("vnp_ResponseCode", "24"))
                .andExpect(redirectedUrl("/customer/bookings/42"))
                .andExpect(flash().attribute("error", org.hamcrest.Matchers.containsString("chưa thành công")));
    }

    @Test
    void vnpayReturnWithInvalidDataShowsAnErrorInsteadOfAnErrorPage() throws Exception {
        when(onlinePayments.handleReturn(anyMap())).thenThrow(new IllegalArgumentException("invalid signature"));

        mvc.perform(get("/payments/vnpay/return").param("vnp_TxnRef", "1001"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/customer/bookings"))
                .andExpect(flash().attribute("error", org.hamcrest.Matchers.containsString("không hợp lệ")));
    }

    @Test
    void vnpayIpnIsPublicAndAnswersInVnpayJsonFormat() throws Exception {
        when(onlinePayments.handleIpn(anyMap())).thenReturn(new OnlinePaymentService.IpnResponse("00", "Confirm Success"));

        mvc.perform(get("/payments/vnpay/ipn").param("vnp_TxnRef", "1001"))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"RspCode\":\"00\",\"Message\":\"Confirm Success\"}"));
        mvc.perform(post("/customer/bookings/42/cancel").with(user(customer))).andExpect(status().isForbidden());
    }

    @Test
    void assignedStaffConfirmsCashPayment() throws Exception {
        mvc.perform(post("/staff/bookings/42/cash-payment").with(user(staff)).with(csrf()))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/staff/bookings/42"));
        verify(payments).confirmCashReceived(42L, staff.getAccountId());
    }

    @Test
    void staffCanSubmitOwnLeaveAndAdminCanReviewIt() throws Exception {
        mvc.perform(post("/staff/leaves").with(user(staff)).with(csrf())
                        .param("startDate", "2026-11-03").param("endDate", "2026-11-04")
                        .param("reason", "Việc cá nhân"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/staff/leaves"));
        verify(leaveManagement).submitLeaveRequest(eq(staff.getAccountId()), any());

        mvc.perform(post("/admin/leaves/21/approve").with(user(admin)).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/leaves"));
        verify(leaveManagement).reviewLeaveRequest(21L, admin.getUserId(), com.housekeeping.management.enums.LeaveStatus.APPROVED);

        mvc.perform(post("/admin/leaves/21/reject").with(user(staff)).with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    void evidenceUploadChecksOwnershipBeforeWritingAFile() throws Exception {
        when(query.booking(42L, staff)).thenThrow(new AccessDeniedException("Other staff"));
        MockMultipartFile image = new MockMultipartFile("image", "proof.png", "image/png", new byte[]{1, 2, 3});
        mvc.perform(multipart("/staff/bookings/42/evidence").file(image).with(user(staff)).with(csrf()))
                .andExpect(status().isForbidden());
        verifyNoInteractions(storage);
    }

    @Test
    void evidenceDownloadRejectsPathsOutsideStorageRoot() throws Exception {
        when(query.evidencePath(50L, customer)).thenReturn("../outside.png");
        mvc.perform(get("/evidence/50").with(user(customer))).andExpect(status().isNotFound());
    }

    @Test
    void onlyStaffCanReadAndUpdateTheirOwnProfile() throws Exception {
        mvc.perform(get("/staff/profile").with(user(customer))).andExpect(status().isForbidden());
        mvc.perform(get("/staff/profile").with(user(admin))).andExpect(status().isForbidden());
        mvc.perform(post("/staff/profile").with(user(staff)).with(csrf())
                        .param("avatarUrl", "https://example.test/staff.png").param("bio", "Giới thiệu"))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/staff/profile"));
        verify(staffProfiles).updateOwnProfile(22L, new StaffProfileForm("https://example.test/staff.png", "Giới thiệu"));
        verify(staffProfiles, never()).findOwnProfile(1L);
    }

    @Test
    void adminCanRequestCancellationAndReassignmentWithCsrf() throws Exception {
        mvc.perform(post("/admin/bookings/42/cancel").with(user(admin)).with(csrf()).param("reason", "Điều phối nhân sự"))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/admin/bookings/42"));
        verify(adminBookingManagement).cancelConfirmedBooking(42L, 3L, "Điều phối nhân sự");

        mvc.perform(post("/admin/bookings/42/reassign").with(user(admin)).with(csrf()).param("staffId", "2"))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/admin/bookings/42"));
        verify(adminBookingManagement).reassignConfirmedBooking(42L, 3L, 2L);

        mvc.perform(post("/admin/bookings/42/arrival-dispute/verify").with(user(admin)).with(csrf())
                        .param("actualArrivalAt", "2026-11-12T10:15").param("note", "Đã đối chiếu với khách"))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/admin/bookings/42"));
        verify(adminBookingManagement).verifyDisputedArrival(42L, 3L,
                LocalDateTime.parse("2026-11-12T10:15"), "Đã đối chiếu với khách");
    }

    @Test
    void stripsDemoWordFromRenderedFrontendLabelsWithoutAlteringAccounts() throws Exception {
        PortalUser demoCustomer = account(1L, "CUSTOMER", "Khách hàng Demo");
        when(query.bookings(demoCustomer)).thenReturn(List.of(
                new BookingCard(42L, 1L, 2L, "Khách hàng Demo", "Nhân viên bán thời gian Demo",
                        start, start.plusHours(2), "CONFIRMED", new BigDecimal("140000"), null, null, null, null, null)));
        when(query.staff(demoCustomer)).thenReturn(List.of(
                new StaffCard(2L, "Nhân viên bán thời gian Demo", "0900000002", false, "PART_TIME", true, null, null)));

        String bookingsHtml = mvc.perform(get("/customer/bookings").with(user(demoCustomer)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertThat(bookingsHtml).contains("Xin chào Khách hàng,").contains("NV: Nhân viên bán thời gian");
        assertThat(bookingsHtml).doesNotContainIgnoringCase("demo");

        String staffHtml = mvc.perform(get("/customer/staff").with(user(demoCustomer)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertThat(staffHtml).contains("Nhân viên bán thời gian");
        assertThat(staffHtml).doesNotContainIgnoringCase("demo");
    }

    private void render(String url, PortalUser principal, String name) throws Exception {
        var request = get(url);
        if (principal != null) request.with(user(principal));
        String html = mvc.perform(request).andExpect(status().isOk()).andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertThat(html).contains("lang=\"vi\"", "/css/portal.css").doesNotContain("unused-hash", "password_hash");
        if (Boolean.getBoolean("portal.preview")) {
            Path directory = Path.of("target", "ui-preview"); Files.createDirectories(directory);
            Path preview = directory.resolve(name + ".html");
            Files.createDirectories(preview.getParent());
            Files.writeString(preview, html);
        }
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder scheduleSaveRequest(LocalDate effective) {
        var request=post("/admin/settings/schedule").with(user(admin)).with(csrf())
                .param("effectiveFrom",effective.toString()).param("editingExisting","false");
        for(int day=1;day<=7;day++) {
            String prefix="operatingHours["+(day-1)+"].";
            request.param(prefix+"dayOfWeek",Integer.toString(day)).param(prefix+"closed",day==7?"true":"false");
            if(day<7)request.param(prefix+"openingTime","08:00").param(prefix+"closingTime","17:00");
        }
        return request;
    }
}
