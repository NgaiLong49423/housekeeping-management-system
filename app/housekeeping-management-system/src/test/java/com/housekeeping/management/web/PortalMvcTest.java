package com.housekeeping.management.web;

import static com.housekeeping.management.web.PortalViews.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.housekeeping.management.domain.entity.Booking;
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
@Import(PortalSecurityConfiguration.class)
class PortalMvcTest {
    @Autowired MockMvc mvc;
    @Autowired PasswordEncoder encoder;
    @MockitoBean PortalQueryService query;
    @MockitoBean PortalActionsService actions;
    @MockitoBean PortalIdentityService identity;
    @MockitoBean BookingReservationService reservations;
    @MockitoBean BookingCompletionService completion;
    @MockitoBean PaymentLifecycleService payments;
    @MockitoBean EvidenceStorageService storage;

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
            new ServiceCard(10L, "Dọn dẹp nhà theo giờ", "Làm sạch không gian sống, chăm chút từng góc nhỏ.", 120, new BigDecimal("140000")),
            new ServiceCard(11L, "Tổng vệ sinh nhà cửa", "Làm sạch kỹ lưỡng để tổ ấm thêm trong lành.", 180, new BigDecimal("350000")),
            new ServiceCard(12L, "Vệ sinh sofa", "Chăm sóc khu vực sinh hoạt của gia đình.", 60, new BigDecimal("200000"))));
        when(query.staff(any())).thenReturn(List.of(new StaffCard(2L, "Nhân viên kiểm thử", "", true), new StaffCard(4L, "Nhân viên thứ hai", "", false)));
        when(query.schedules(any())).thenReturn(List.of(new ScheduleCard(20L, 2L, "Nhân viên kiểm thử", start, start.plusHours(8))));
        when(query.busyIntervals()).thenReturn(List.of(new PortalQueryService.BusyInterval(2L, start.plusHours(4), start.plusHours(5))));
        when(query.bookings(any())).thenReturn(List.of(card("CONFIRMED", null, null), card("COMPLETED", "UNPAID", null)));
        when(query.booking(eq(42L), any())).thenReturn(card("CONFIRMED", null, null));
        when(query.details(eq(42L), any())).thenReturn(List.of(new DetailCard(30L, "Dọn dẹp nhà theo giờ", 1, 120, new BigDecimal("140000"), List.of())));
    }

    BookingCard card(String status, String payment, String method) {
        return new BookingCard(42L, 1L, 2L, "Khách hàng kiểm thử", "Nhân viên kiểm thử", start, start.plusHours(2), status,
                new BigDecimal("140000"), payment, method, null);
    }

    @Test
    void rendersAllPublicAndRolePagesWithRealThymeleaf() throws Exception {
        render("/", null, "home"); render("/services", null, "services"); render("/login", null, "login");
        render("/customer/bookings", customer, "customer-bookings");
        render("/customer/bookings/new", customer, "booking-new");
        render("/customer/staff", customer, "customer-staff");
        render("/customer/schedules", customer, "customer-schedules");
        render("/staff/bookings", staff, "staff-bookings"); render("/staff/schedules", staff, "staff-schedules");
        render("/admin/bookings", admin, "admin-bookings"); render("/admin/services", admin, "admin-services");
        render("/admin/staff", admin, "admin-staff"); render("/admin/schedules", admin, "admin-schedules");
        for (PortalUser user : List.of(customer, staff, admin)) {
            render("/" + user.getRole().toLowerCase() + "/bookings/42", user, user.getRole().toLowerCase() + "-detail-confirmed");
        }
    }

    @Test
    void rendersCompletedBookingActionsAndEvidenceForEachRole() throws Exception {
        when(query.booking(eq(42L), any())).thenReturn(card("COMPLETED", "UNPAID", "CASH"));
        when(query.details(eq(42L), any())).thenReturn(List.of(new DetailCard(30L, "Dọn dẹp nhà theo giờ", 1, 120,
                new BigDecimal("140000"), List.of(new EvidenceCard(50L, "10:00 · 16/10/2026")))));
        for (PortalUser user : List.of(customer, staff, admin)) {
            render("/" + user.getRole().toLowerCase() + "/bookings/42", user, user.getRole().toLowerCase() + "-detail-completed");
        }
        mvc.perform(get("/customer/bookings/42").with(user(customer))).andExpect(content().string(org.hamcrest.Matchers.containsString("/customer/bookings/42/payment")));
        mvc.perform(get("/admin/bookings/42").with(user(admin))).andExpect(content().string(org.hamcrest.Matchers.containsString("receiptVerified")));
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
        mvc.perform(get("/admin/bookings").with(user(customer))).andExpect(status().isForbidden());
        mvc.perform(get("/staff/bookings").with(user(customer))).andExpect(status().isForbidden());
        mvc.perform(post("/admin/services").with(user(customer)).with(csrf())).andExpect(status().isForbidden());
        verifyNoInteractions(actions, reservations, payments, storage);
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
        Booking saved = new Booking(); saved.setBookingId(42L);
        when(reservations.createBooking(anyLong(), anyLong(), any(), anyList())).thenReturn(saved);
        mvc.perform(post("/customer/bookings").with(user(customer)).with(csrf())
                .param("customerId", "999").param("totalPrice", "1").param("staffId", "2").param("scheduleId", "20")
                .param("startAt", "2026-11-01T08:00").param("serviceIds", "10", "11"))
            .andExpect(redirectedUrl("/customer/bookings/42"));
        verify(reservations).createBooking(eq(1L), eq(2L), eq(OffsetDateTime.parse("2026-11-01T08:00:00+07:00")), eq(List.of(
                new BookingReservationService.ServiceSelection(20L, 10L, 1), new BookingReservationService.ServiceSelection(20L, 11L, 2))));
    }

    @Test
    void invalidBookingRedisplaysInputsWithoutCallingReservation() throws Exception {
        mvc.perform(post("/customer/bookings").with(user(customer)).with(csrf()).param("staffId", "2").param("scheduleId", "20")
                .param("startAt", "2026-11-01T08:00")).andExpect(status().isOk()).andExpect(view().name("booking-new"));
        verifyNoInteractions(reservations);
    }

    @Test
    void ownershipFailureStopsPaymentBeforeExistingServiceIsCalled() throws Exception {
        when(query.booking(42L, customer)).thenThrow(new AccessDeniedException("Forbidden"));
        mvc.perform(post("/customer/bookings/42/payment").with(user(customer)).with(csrf()).param("method", "CASH"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(payments);
    }

    @Test
    void adminMustSubmitExplicitReceiptVerification() throws Exception {
        doThrow(new IllegalArgumentException("Receipt must be verified before confirming payment"))
                .when(payments).confirmReceivedByAdmin(eq(42L), eq(false), any());
        mvc.perform(post("/admin/bookings/42/paid").with(user(admin)).with(csrf()))
                .andExpect(status().isBadRequest()).andExpect(view().name("error"));
    }

    @Test
    void evidenceUploadChecksOwnershipBeforeWritingAFile() throws Exception {
        when(query.bookingIdForDetail(30L, staff)).thenThrow(new AccessDeniedException("Other staff"));
        MockMultipartFile image = new MockMultipartFile("image", "proof.png", "image/png", new byte[]{1, 2, 3});
        mvc.perform(multipart("/staff/details/30/evidence").file(image).with(user(staff)).with(csrf()))
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
