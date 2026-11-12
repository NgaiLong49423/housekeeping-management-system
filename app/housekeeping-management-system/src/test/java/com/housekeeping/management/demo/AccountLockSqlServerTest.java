package com.housekeeping.management.demo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;

import com.housekeeping.management.service.CustomerManagementService;
import com.housekeeping.management.service.StaffManagementService;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.Map;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/** V35 trên SQL Server thật: khóa tài khoản có lý do, chặn khóa khi còn Booking, và báo lý do lúc đăng nhập. */
@Tag("sqlserver")
@ActiveProfiles("demo")
@SpringBootTest
@AutoConfigureMockMvc
class AccountLockSqlServerTest {
    private static final String TEST_DATABASE = "home_cleaning_demo";
    private static final String DEMO_PASSWORD = "Demo@12345";
    private static final MSSQLServerContainerHolder SQL = new MSSQLServerContainerHolder();

    @DynamicPropertySource
    static void configureDemoDatabase(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> SQL.container.getJdbcUrl() + ";databaseName=" + TEST_DATABASE);
        registry.add("spring.datasource.username", SQL.container::getUsername);
        registry.add("spring.datasource.password", SQL.container::getPassword);
        // Dữ liệu demo (V3, V33…) nằm trong bộ migration db/refactor + db/shared của home_cleaning_demo;
        // chỉ định rõ để test không phụ thuộc profile demo đang trỏ tới database nào.
        registry.add("spring.flyway.locations", () -> "classpath:db/refactor,classpath:db/shared");
        registry.add("spring.flyway.table", () -> "flyway_schema_history");
        registry.add("spring.flyway.baseline-on-migrate", () -> "false");
        registry.add("app.evidence.storage-root", () ->
                Path.of(System.getProperty("java.io.tmpdir"), "hsf302-account-lock-test-evidence").toString());
    }

    @Autowired private JdbcTemplate jdbc;
    @Autowired private MockMvc mvc;
    @Autowired private CustomerManagementService customers;
    @Autowired private StaffManagementService staffManagement;

    @Test
    void lockedCustomerSeesTheReasonAfterTheCorrectPasswordAndCanBeUnlocked() throws Exception {
        long adminId = userId("admin@demo.gonnha.local");
        long customerId = userId("customer3@demo.gonnha.local");

        customers.lock(adminId, customerId, "  Nhiều lần hủy lịch   sát giờ ");

        Map<String, Object> row = jdbc.queryForMap(
                "SELECT status, status_reason, status_changed_by, status_changed_at FROM dbo.Users WHERE user_id=?", customerId);
        assertThat(row.get("status")).isEqualTo("INACTIVE");
        assertThat(row.get("status_reason")).isEqualTo("Nhiều lần hủy lịch sát giờ");
        assertThat(((Number) row.get("status_changed_by")).longValue()).isEqualTo(adminId);
        assertThat(row.get("status_changed_at")).isNotNull();

        mvc.perform(post("/login").with(csrf()).param("email", "customer3@demo.gonnha.local").param("password", "sai-mat-khau"))
                .andExpect(redirectedUrl("/login?error"));
        var locked = mvc.perform(post("/login").with(csrf())
                        .param("email", "customer3@demo.gonnha.local").param("password", DEMO_PASSWORD))
                .andExpect(redirectedUrl("/login?locked")).andReturn();
        MockHttpSession session = (MockHttpSession) locked.getRequest().getSession(false);
        mvc.perform(get("/login").param("locked", "").session(session))
                .andExpect(content().string(Matchers.containsString("Nhiều lần hủy lịch sát giờ")));

        customers.unlock(adminId, customerId);

        // Bị khóa khi đang đăng nhập: request kế tiếp mất phiên và được đưa về trang đăng nhập kèm lý do.
        var signedIn = mvc.perform(post("/login").with(csrf())
                        .param("email", "customer3@demo.gonnha.local").param("password", DEMO_PASSWORD))
                .andExpect(redirectedUrl("/portal")).andReturn();
        MockHttpSession activeSession = (MockHttpSession) signedIn.getRequest().getSession(false);
        customers.lock(adminId, customerId, "Khóa khi đang đăng nhập");
        mvc.perform(get("/customer/houses").session(activeSession)).andExpect(redirectedUrl("/login?locked"));
        assertThat(activeSession.isInvalid()).isTrue();
        customers.unlock(adminId, customerId);

        assertThat(jdbc.queryForObject("SELECT status FROM dbo.Users WHERE user_id=?", String.class, customerId))
                .isEqualTo("ACTIVE");
        assertThat(jdbc.queryForObject("SELECT status_reason FROM dbo.Users WHERE user_id=?", String.class, customerId))
                .isNull();
        mvc.perform(post("/login").with(csrf()).param("email", "customer3@demo.gonnha.local").param("password", DEMO_PASSWORD))
                .andExpect(redirectedUrl("/portal"));
    }

    @Test
    void customerWithAnUnfinishedBookingCannotBeLocked() {
        long adminId = userId("admin@demo.gonnha.local");
        Long customerWithBooking = jdbc.queryForObject("""
                SELECT TOP 1 customer_id FROM dbo.Bookings
                WHERE status IN ('CONFIRMED', 'AWAITING_PAYMENT') ORDER BY booking_id
                """, Long.class);

        assertThatThrownBy(() -> customers.lock(adminId, customerWithBooking, "Thử khóa khi còn lịch"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("chờ thanh toán");
        assertThat(jdbc.queryForObject("SELECT status FROM dbo.Users WHERE user_id=?", String.class, customerWithBooking))
                .isEqualTo("ACTIVE");
    }

    @Test
    void staffLockStoresTheReasonAndDatabaseRejectsAReasonOnAnActiveAccount() {
        long adminId = userId("admin@demo.gonnha.local");
        Long staffId = jdbc.queryForObject("""
                SELECT s.staff_id FROM dbo.Staff s JOIN dbo.Users u ON u.user_id = s.user_id
                WHERE u.email = 'staff6@demo.gonnha.local'
                """, Long.class);

        staffManagement.deactivate(adminId, staffId, "Nghỉ việc từ 15/11/2026");
        assertThat(jdbc.queryForObject("SELECT status_reason FROM dbo.Users WHERE email='staff6@demo.gonnha.local'",
                String.class)).isEqualTo("Nghỉ việc từ 15/11/2026");

        assertThatThrownBy(() -> jdbc.update(
                "UPDATE dbo.Users SET status_reason=N'Không hợp lệ' WHERE email='staff5@demo.gonnha.local'"))
                .isInstanceOf(DataIntegrityViolationException.class);
        staffManagement.activate(adminId, staffId);
    }

    private long userId(String email) {
        return jdbc.queryForObject("SELECT user_id FROM dbo.Users WHERE email=?", Long.class, email);
    }

    /** Khởi động SQL Server và tạo database demo một lần cho cả lớp test. */
    private static final class MSSQLServerContainerHolder {
        private final org.testcontainers.mssqlserver.MSSQLServerContainer container =
                new org.testcontainers.mssqlserver.MSSQLServerContainer("mcr.microsoft.com/mssql/server:2022-CU20-ubuntu-22.04")
                        .acceptLicense();

        private MSSQLServerContainerHolder() {
            container.start();
            try (var connection = DriverManager.getConnection(container.getJdbcUrl(), container.getUsername(), container.getPassword());
                    Statement statement = connection.createStatement()) {
                statement.execute("CREATE DATABASE " + TEST_DATABASE);
            } catch (Exception failure) {
                throw new ExceptionInInitializerError(failure);
            }
        }
    }
}
