package com.housekeeping.management.web;

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
import com.housekeeping.management.controller.PortalAdminController;
import com.housekeeping.management.controller.PortalController;
import com.housekeeping.management.service.PortalActionsService;
import com.housekeeping.management.service.PortalQueryService;
import com.housekeeping.management.service.*;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
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

@WebMvcTest({PortalController.class, PortalAdminController.class})
@Import(SecurityConfig.class)
class PortalMvcTest {
    @Autowired MockMvc mvc;
    @Autowired PasswordEncoder encoder;
    @MockitoBean PortalQueryService query;
    @MockitoBean PortalActionsService actions;
    @MockitoBean AuthService identity;
    @MockitoBean BookingReservationService reservations;
    @MockitoBean BookingCompletionService completion;
    @MockitoBean PaymentService payments;
    @MockitoBean PayosPaymentService payosPayments;
    @MockitoBean FeedbackService feedbackService;
    @MockitoBean EvidenceStorageService storage;
    @MockitoBean HouseService houses;

    private final PortalUser customer = account(1L, "CUSTOMER", "Khách hàng kiểm thử");
    private final PortalUser staff = account(2L, "STAFF", "Nhân viên kiểm thử");
    private final PortalUser admin = account(3L, "ADMIN", "Quản trị kiểm thử");
    private final OffsetDateTime start = OffsetDateTime.now(ZoneOffset.ofHours(7)).plusDays(2).withHour(8).withMinute(0).withSecond(0).withNano(0);

    static PortalUser account(Long id, String role, String name) {
        return new PortalUser(id, role.toLowerCase() + "@example.test", "unused-hash", name, role);
    }

    @BeforeEach
    void data() {
        when(query.services()).thenReturn(List.of(
            new ServiceCard(10L, "Dọn dẹp nhà theo giờ", "Làm sạch không gian sống, chăm chút từng góc nhỏ.", 120, new BigDecimal("140000"), true),
            new ServiceCard(11L, "Tổng vệ sinh nhà cửa", "Làm sạch kỹ lưỡng để tổ ấm thêm trong lành.", 180, new BigDecimal("350000"), true),
            new ServiceCard(12L, "Vệ sinh sofa", "Chăm sóc khu vực sinh hoạt của gia đình.", 60, new BigDecimal("200000"), true)));
        when(query.staff(any())).thenReturn(List.of(new StaffCard(2L, "Nhân viên kiểm thử", "", true, "PART_TIME", true), new StaffCard(4L, "Nhân viên thứ hai", "", false, "FULL_TIME", true)));
        when(query.schedules(any())).thenReturn(List.of(new ScheduleCard(20L, 2L, "Nhân viên kiểm thử", start, start.plusHours(8))));
        when(query.busyIntervals()).thenReturn(List.of(new PortalQueryService.BusyInterval(2L, start.plusHours(4), start.plusHours(5))));
        when(query.bookings(any())).thenReturn(List.of(card("CONFIRMED", null, null), card("AWAITING_PAYMENT", "PENDING", "ONLINE")));
        when(query.feedback()).thenReturn(List.of());
        when(query.booking(eq(42L), any())).thenReturn(card("CONFIRMED", null, null));
        when(query.details(eq(42L), any())).thenReturn(List.of(new DetailCard(30L, "Dọn dẹp nhà theo giờ", 1, 120, new BigDecimal("140000"), List.of())));
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
        render("/admin/bookings", admin, "admin-bookings"); render("/admin/services", admin, "admin-services");
        render("/admin/staff", admin, "admin-staff"); render("/admin/schedules", admin, "admin-schedules");
        render("/admin/feedback", admin, "admin-feedback");
        for (PortalUser user : List.of(customer, staff, admin)) {
            render("/" + user.getRole().toLowerCase() + "/bookings/42", user, user.getRole().toLowerCase() + "-detail-confirmed");
        }
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
    void customerHouseFormUsesAuthenticatedUserAndValidCoordinates() throws Exception {
        mvc.perform(post("/customer/houses").with(user(customer)).with(csrf())
                        .param("houseName", "Nhà chính")
                        .param("address", "Quận 1, TP. Hồ Chí Minh")
                        .param("latitude", "10.1234567")
                        .param("longitude", "106.1234567")
                        .param("addressNote", "Cổng màu xanh"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/customer/houses?saved"));

        org.mockito.ArgumentCaptor<HouseForm> form = org.mockito.ArgumentCaptor.forClass(HouseForm.class);
        verify(houses).save(eq(customer.getUserId()), isNull(), form.capture());
        assertThat(form.getValue().houseName()).isEqualTo("Nhà chính");
        assertThat(form.getValue().latitude()).isEqualByComparingTo("10.1234567");
    }

    @Test
    void customerFeedbackAcceptsTextWithoutRatingAndAdminCanOpenFeedbackInbox() throws Exception {
        mvc.perform(post("/customer/bookings/42/feedback").with(user(customer)).with(csrf())
                        .param("content", "Nhân viên làm việc cẩn thận."))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/customer/bookings/42"));
        verify(feedbackService).submit(42L, 1L, "Nhân viên làm việc cẩn thận.");

        mvc.perform(get("/admin/feedback").with(user(admin)))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Phản hồi khách hàng")));

        mvc.perform(post("/admin/feedback/90/reply").with(user(admin)).with(csrf()).param("reply", "Cảm ơn bạn đã góp ý."))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/admin/feedback"));
        verify(feedbackService).reply(90L, "Cảm ơn bạn đã góp ý.");
        mvc.perform(post("/admin/feedback/90/resolve").with(user(admin)).with(csrf()))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/admin/feedback"));
        verify(feedbackService).resolve(90L);
    }

    @Test
    void awaitingPaymentOffersOnlineCheckoutAndAssignedStaffCashConfirmation() throws Exception {
        BookingCard payos = new BookingCard(42L, 1L, 2L, "Khách hàng kiểm thử", "Nhân viên kiểm thử", start,
                start.plusHours(2), "AWAITING_PAYMENT", new BigDecimal("140000"), "PENDING", "ONLINE", null, null, null);
        when(query.booking(eq(42L), any())).thenReturn(payos);
        when(query.details(eq(42L), any())).thenReturn(List.of());

        mvc.perform(get("/customer/bookings/42").with(user(customer)))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Thanh toán trực tuyến qua PayOS")));
        mvc.perform(get("/staff/bookings/42").with(user(staff)))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("cash-payment")));
    }

    @Test
    void staffCanConfirmCompletionBeforeScheduledStartWhenEvidenceIsReady() throws Exception {
        when(query.details(eq(42L), any())).thenReturn(List.of(new DetailCard(30L, "Dọn dẹp nhà theo giờ", 1,
                120, new BigDecimal("140000"), List.of(new EvidenceCard(50L, "10:00 · 22/10/2026")))));

        String page = mvc.perform(get("/staff/bookings/42").with(user(staff)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(page).contains("Xác nhận hoàn thành");
        assertThat(page).doesNotContain("Chưa đến giờ bắt đầu công việc.");
        assertThat(page).doesNotContain("type=\"submit\" disabled=\"disabled\">");
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
        verifyNoInteractions(actions, reservations, payments, storage, houses);
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
        verifyNoInteractions(actions);
    }

    @Test
    void bookingUsesSessionCustomerServerServiceSelectionAndVietnamOffset() throws Exception {
        when(reservations.createBooking(anyLong(), anyLong(), anyLong(), any(), anyList(), any())).thenReturn(42L);
        mvc.perform(post("/customer/bookings").with(user(customer)).with(csrf())
                .param("customerId", "999").param("totalPrice", "1").param("staffId", "2").param("houseId", "30")
                .param("startAt", "2026-11-01T08:00").param("serviceIds", "10", "11").param("areaNote", "Entryway"))
            .andExpect(redirectedUrl("/customer/bookings/42"));
        verify(reservations).createBooking(eq(1L), eq(2L), eq(30L), eq(java.time.LocalDateTime.parse("2026-11-01T08:00")),
                eq(List.of(10L, 11L)), eq("Entryway"));
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
        verifyNoInteractions(payosPayments);
    }

    @Test
    void customerPayosChoiceRedirectsToProviderCheckout() throws Exception {
        when(query.booking(eq(42L), any())).thenReturn(card("AWAITING_PAYMENT", "PENDING", "ONLINE"));
        when(payosPayments.checkout(42L, customer.getAccountId())).thenReturn("https://pay.payos.vn/web/payment-link");

        mvc.perform(post("/customer/bookings/42/payment").with(user(customer)).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("https://pay.payos.vn/web/payment-link"));
        verify(payosPayments).checkout(42L, customer.getAccountId());
        verifyNoInteractions(payments);
    }

    @Test
    void payosWebhookIsPublicAndExemptFromCsrfOnlyForProviderCallback() throws Exception {
        mvc.perform(post("/payments/payos/webhook").contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"00\",\"desc\":\"success\",\"success\":true,\"data\":{\"orderCode\":123,\"amount\":140000,\"currency\":\"VND\",\"code\":\"00\",\"paymentLinkId\":\"link-123\"},\"signature\":\"test\"}"))
                .andExpect(status().isNoContent());
        verify(payosPayments).receiveWebhook(any(PayosWebhookRequest.class));
        mvc.perform(post("/customer/bookings/42/cancel").with(user(customer))).andExpect(status().isForbidden());
    }

    @Test
    void assignedStaffConfirmsCashPayment() throws Exception {
        mvc.perform(post("/staff/bookings/42/cash-payment").with(user(staff)).with(csrf()))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/staff/bookings/42"));
        verify(payments).confirmCashReceived(42L, staff.getAccountId());
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

    private void render(String url, PortalUser principal, String name) throws Exception {
        var request = get(url);
        if (principal != null) request.with(user(principal));
        String html = mvc.perform(request).andExpect(status().isOk()).andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertThat(html).contains("lang=\"vi\"", "/css/portal.css").doesNotContain("unused-hash", "password_hash");
        if (Boolean.getBoolean("portal.preview")) {
            Path directory = Path.of("target", "ui-preview"); Files.createDirectories(directory);
            Files.writeString(directory.resolve(name + ".html"), html);
        }
    }
}
