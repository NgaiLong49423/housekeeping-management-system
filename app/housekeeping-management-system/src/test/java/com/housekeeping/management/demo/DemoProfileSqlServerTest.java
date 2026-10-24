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
@SpringBootTest(properties = "app.demo.account-password=Demo-Only-Integration-Password-42!")
@AutoConfigureMockMvc
class DemoProfileSqlServerTest {
    private static final String TEST_DATABASE = "home_cleaning_demo_acceptance";
    private static final String DEMO_PASSWORD = "Demo-Only-Integration-Password-42!";
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
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA='dbo' AND TABLE_TYPE='BASE TABLE' AND TABLE_NAME<>'flyway_schema_history'",
                Integer.class)).isEqualTo(15);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM dbo.Users", Integer.class)).isEqualTo(4);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM dbo.Staff", Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM dbo.Company_Operating_Hours", Integer.class)).isEqualTo(7);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM dbo.Services", Integer.class)).isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM dbo.Houses", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM dbo.Staff_Availability", Integer.class)).isEqualTo(1);

        var demoUsers = jdbc.query("SELECT email, role, password_hash FROM dbo.Users ORDER BY email",
                (rs, row) -> Map.entry(rs.getString("email"), Map.entry(rs.getString("role"), rs.getString("password_hash"))))
                .stream().collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        assertThat(demoUsers).hasSize(4);
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
