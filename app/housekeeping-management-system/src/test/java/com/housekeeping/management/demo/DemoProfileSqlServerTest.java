package com.housekeeping.management.demo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.housekeeping.management.web.PortalUser;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
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
class DemoProfileSqlServerTest {
    private static final String TEST_DATABASE = "home_cleaning_demo";
    private static final String DEMO_PASSWORD = "Demo@12345";
    private static final MSSQLServerContainer SQL_SERVER = new MSSQLServerContainer(
            "mcr.microsoft.com/mssql/server:2022-CU20-ubuntu-22.04").acceptLicense();

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
                Path.of(System.getProperty("java.io.tmpdir"), "hsf302-demo-profile-test-evidence").toString());
    }

    @Autowired private JdbcTemplate jdbc;
    @Autowired private PasswordEncoder passwords;
    @Autowired private MockMvc mvc;

    @Test
    void demoProfileMigratesSeedsAndAuthenticatesAllFourRolesOnSqlServer() throws Exception {
        // Cùng số bảng nghiệp vụ như database chính.
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA='dbo' AND TABLE_TYPE='BASE TABLE' AND TABLE_NAME<>'flyway_schema_history'",
                Integer.class)).isEqualTo(29);
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
                WHERE TABLE_SCHEMA='dbo' AND TABLE_NAME='Users' AND COLUMN_NAME='must_change_password'
                """, Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM dbo.Users WHERE must_change_password = 1", Integer.class))
                .isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM dbo.Company_Operating_Hours", Integer.class)).isEqualTo(7);
        // Bốn tài khoản gốc của V3; các migration demo sau (V11, V33) thêm tài khoản khác với cùng mật khẩu demo.
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM dbo.Users WHERE email IN (N'customer@demo.gonnha.local', N'staff@demo.gonnha.local',
                    N'staff.fulltime@demo.gonnha.local', N'admin@demo.gonnha.local')
                """, Integer.class)).isEqualTo(4);

        var demoUsers = jdbc.query("SELECT email, role, password_hash FROM dbo.Users ORDER BY email",
                (rs, row) -> Map.entry(rs.getString("email"), Map.entry(rs.getString("role"), rs.getString("password_hash"))))
                .stream().collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        // Mọi tài khoản demo đều dùng chung mật khẩu demo đã mã hóa bcrypt.
        assertThat(demoUsers).isNotEmpty();
        demoUsers.forEach((email, roleAndHash) -> {
            assertThat(roleAndHash.getValue()).startsWith("{bcrypt}");
            assertThat(passwords.matches(DEMO_PASSWORD, roleAndHash.getValue())).isTrue();
        });

        Map<String, String> roles = Map.of(
                "customer@demo.gonnha.local", "CUSTOMER",
                "staff@demo.gonnha.local", "STAFF",
                "staff.fulltime@demo.gonnha.local", "STAFF",
                "admin@demo.gonnha.local", "ADMIN");
        for (var account : roles.entrySet()) {
            MvcResult login = mvc.perform(post("/login").with(csrf())
                            .param("email", account.getKey()).param("password", DEMO_PASSWORD))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrl("/portal"))
                    .andReturn();
            SecurityContext security = (SecurityContext) login.getRequest().getSession()
                    .getAttribute("SPRING_SECURITY_CONTEXT");
            assertThat(security).isNotNull();
            assertThat(((PortalUser) security.getAuthentication().getPrincipal()).getRole()).isEqualTo(account.getValue());
            mvc.perform(get("/portal").session((org.springframework.mock.web.MockHttpSession) login.getRequest().getSession()))
                    .andExpect(redirectedUrl("/" + account.getValue().toLowerCase() + "/bookings"));
        }
    }
}
