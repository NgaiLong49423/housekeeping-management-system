package com.housekeeping.management.demo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.housekeeping.management.web.PortalUser;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.mssqlserver.MSSQLServerContainer;

@Tag("sqlserver")
@ActiveProfiles("demo")
@SpringBootTest
@AutoConfigureMockMvc
class DemoAdditionalAccountsSqlServerTest {
    private static final String TEST_DATABASE = "home_cleaning_demo";
    private static final String DEMO_PASSWORD = "Demo@12345";
    private static final MSSQLServerContainer SQL_SERVER = new MSSQLServerContainer(
            "mcr.microsoft.com/mssql/server:2022-CU20-ubuntu-22.04").acceptLicense();

    private static final Map<String, String> NEW_ACCOUNTS = new LinkedHashMap<>();
    static {
        for (int i = 3; i <= 7; i++) NEW_ACCOUNTS.put("staff" + i + "@demo.gonnha.local", "STAFF");
        for (int i = 2; i <= 6; i++) NEW_ACCOUNTS.put("customer" + i + "@demo.gonnha.local", "CUSTOMER");
    }

    static {
        SQL_SERVER.start();
        try (var connection = DriverManager.getConnection(
                SQL_SERVER.getJdbcUrl(), SQL_SERVER.getUsername(), SQL_SERVER.getPassword());
                Statement statement = connection.createStatement()) {
            statement.execute("CREATE DATABASE " + TEST_DATABASE);
        } catch (Exception failure) {
            throw new ExceptionInInitializerError(failure);
        }
    }

    @DynamicPropertySource
    static void configureDemoDatabase(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> SQL_SERVER.getJdbcUrl() + ";databaseName=" + TEST_DATABASE);
        registry.add("spring.datasource.username", SQL_SERVER::getUsername);
        registry.add("spring.datasource.password", SQL_SERVER::getPassword);
        registry.add("app.evidence.storage-root", () ->
                Path.of(System.getProperty("java.io.tmpdir"), "hsf302-demo-accounts-test-evidence").toString());
    }

    @Autowired private JdbcTemplate jdbc;
    @Autowired private PasswordEncoder passwords;
    @Autowired private MockMvc mvc;

    @Test
    void placeholderDemoNamesAreReplacedWithRealisticNames() {
        Map<String, String> expected = Map.of(
                "customer@demo.gonnha.local", "Trần Thị Thu Hà",
                "staff@demo.gonnha.local", "Lê Thị Hồng Nhung",
                "staff.fulltime@demo.gonnha.local", "Phạm Văn Hùng",
                "admin@demo.gonnha.local", "Võ Thanh Tùng",
                "manager@demo.gonnha.local", "Đặng Quốc Bảo");
        expected.forEach((email, name) -> assertThat(jdbc.queryForObject(
                "SELECT full_name FROM dbo.Users WHERE email=?", String.class, email)).isEqualTo(name));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM dbo.Users WHERE full_name LIKE N'%Demo'", Integer.class))
                .isZero();
        assertThat(jdbc.queryForObject("""
                SELECT h.recipient_name FROM dbo.Houses h JOIN dbo.Users u ON u.user_id=h.customer_id
                WHERE u.email=N'customer@demo.gonnha.local'
                """, String.class)).isEqualTo("Trần Thị Thu Hà");
    }

    @Test
    void additionalStaffHaveStaffRowsAndProfilesWithBothStaffTypes() {
        List<String> types = jdbc.queryForList("""
                SELECT s.staff_type FROM dbo.Staff s
                JOIN dbo.Users u ON u.user_id=s.user_id
                JOIN dbo.Staff_Profiles p ON p.staff_id=s.staff_id
                WHERE u.email LIKE N'staff[3-7]@demo.gonnha.local' AND u.role='STAFF' AND LEN(p.bio) > 0
                """, String.class);
        assertThat(types).hasSize(5).contains("FULL_TIME", "PART_TIME");
    }

    @Test
    void additionalCustomersHaveOneDefaultHomeInsideTheServiceRadius() {
        var homes = jdbc.queryForList("""
                SELECT u.email, h.recipient_name, h.recipient_phone, h.address_detail, h.address_type,
                       h.is_default, h.latitude, h.longitude
                FROM dbo.Houses h JOIN dbo.Users u ON u.user_id=h.customer_id
                WHERE u.email LIKE N'customer[2-6]@demo.gonnha.local' AND h.is_deleted=0
                """);
        assertThat(homes).hasSize(5);
        homes.forEach(home -> {
            assertThat((String) home.get("recipient_name")).isNotBlank();
            assertThat((String) home.get("recipient_phone")).isNotBlank();
            assertThat((String) home.get("address_detail")).isNotBlank();
            assertThat(home.get("address_type")).isEqualTo("HOME");
            assertThat(home.get("is_default")).isEqualTo(true);
            assertThat(home.get("latitude")).isNotNull();
            assertThat(home.get("longitude")).isNotNull();
        });
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM dbo.Houses h
                JOIN dbo.Users u ON u.user_id=h.customer_id
                CROSS JOIN dbo.Company_Settings c
                WHERE u.email LIKE N'customer[2-6]@demo.gonnha.local'
                  AND 6371 * 2 * ASIN(SQRT(
                        POWER(SIN(RADIANS(h.latitude - c.latitude) / 2), 2)
                        + COS(RADIANS(c.latitude)) * COS(RADIANS(h.latitude))
                          * POWER(SIN(RADIANS(h.longitude - c.longitude) / 2), 2))) <= c.service_radius_km
                """, Integer.class)).isEqualTo(5);
    }

    @Test
    void phoneNumbersStayUniqueAndMigrationsCanRunAgainWithoutDuplicates() throws Exception {
        int usersBefore = jdbc.queryForObject("SELECT COUNT(*) FROM dbo.Users", Integer.class);
        int housesBefore = jdbc.queryForObject("SELECT COUNT(*) FROM dbo.Houses", Integer.class);
        jdbc.execute(script("db/shared/V32__rename_demo_accounts_to_realistic_names.sql"));
        jdbc.execute(script("db/shared/V33__seed_additional_demo_staff_and_customers.sql"));

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM dbo.Users", Integer.class)).isEqualTo(usersBefore);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM dbo.Houses", Integer.class)).isEqualTo(housesBefore);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) - COUNT(DISTINCT phone) FROM dbo.Users", Integer.class))
                .isZero();
    }

    @Test
    void everyAdditionalAccountLogsInWithTheDemoPassword() throws Exception {
        for (var account : NEW_ACCOUNTS.entrySet()) {
            String hash = jdbc.queryForObject("SELECT password_hash FROM dbo.Users WHERE email=? AND status='ACTIVE'",
                    String.class, account.getKey());
            assertThat(passwords.matches(DEMO_PASSWORD, hash)).isTrue();

            MvcResult login = mvc.perform(post("/login").with(csrf())
                            .param("email", account.getKey()).param("password", DEMO_PASSWORD))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrl("/portal"))
                    .andReturn();
            MockHttpSession session = (MockHttpSession) login.getRequest().getSession();
            SecurityContext security = (SecurityContext) session.getAttribute("SPRING_SECURITY_CONTEXT");
            assertThat(security).isNotNull();
            assertThat(((PortalUser) security.getAuthentication().getPrincipal()).getRole()).isEqualTo(account.getValue());
            mvc.perform(get("/portal").session(session))
                    .andExpect(redirectedUrl("/" + account.getValue().toLowerCase() + "/bookings"));
        }
    }

    private static String script(String location) throws Exception {
        return new ClassPathResource(location).getContentAsString(StandardCharsets.UTF_8);
    }
}
