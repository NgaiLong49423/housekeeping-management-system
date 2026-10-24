package com.housekeeping.management.demo;

import com.housekeeping.management.service.DuplicateEmailException;
import com.housekeeping.management.service.GlobalEmailUniquenessService;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/** Creates optional demo identities in the shared Users table without changing existing accounts. */
@Component
@Profile("demo")
public class DemoAccountSeeder implements ApplicationRunner {
    private static final Logger LOG = LoggerFactory.getLogger(DemoAccountSeeder.class);
    private static final String FIND_ROLE = "SELECT role FROM dbo.Users WHERE email = ?";
    private static final List<DemoAccount> ACCOUNTS = List.of(
            new DemoAccount("CUSTOMER", "customer@demo.gonnha.local", "Khách hàng Demo"),
            new DemoAccount("STAFF", "staff@demo.gonnha.local", "Nhân viên Demo"),
            new DemoAccount("ADMIN", "admin@demo.gonnha.local", "Quản trị viên Demo"));

    private final JdbcTemplate jdbc;
    private final GlobalEmailUniquenessService emails;
    private final PasswordEncoder encoder;
    private final String demoPassword;

    public DemoAccountSeeder(JdbcTemplate jdbc, GlobalEmailUniquenessService emails, PasswordEncoder encoder,
            @Value("${app.demo.account-password:}") String demoPassword) {
        this.jdbc = jdbc;
        this.emails = emails;
        this.encoder = encoder;
        this.demoPassword = demoPassword;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (demoPassword == null || demoPassword.isBlank()) {
            LOG.warn("Demo accounts were not seeded because app.demo.account-password is not configured.");
            return;
        }
        LOG.warn("Demo profile is enabled. Use this profile only with a disposable local database.");
        for (DemoAccount account : ACCOUNTS) {
            List<String> existing = roles(account.email());
            if (!existing.isEmpty()) {
                requireExpectedRole(account, existing);
                LOG.info("Keeping existing demo account {} and its password", account.email());
                continue;
            }
            try {
                emails.executeWithUniqueEmail(account.email(), canonical -> {
                    Long userId = jdbc.queryForObject("""
                            INSERT INTO dbo.Users (full_name, email, password_hash, phone, role, status)
                            OUTPUT INSERTED.user_id
                            VALUES (?, ?, ?, ?, ?, 'ACTIVE')
                            """, Long.class, account.fullName(), canonical, encoder.encode(demoPassword),
                            "0000000000", account.role());
                    if ("STAFF".equals(account.role())) {
                        jdbc.update("INSERT INTO dbo.Staff (user_id, staff_type) VALUES (?, 'PART_TIME')", userId);
                    }
                    return null;
                });
                LOG.info("Created demo account {} with role {}", account.email(), account.role());
            } catch (DuplicateEmailException concurrentInsert) {
                requireExpectedRole(account, roles(account.email()));
            }
        }
    }

    private List<String> roles(String email) {
        List<String> roles = jdbc.queryForList(FIND_ROLE, String.class, email);
        if (roles.size() == 1 && "STAFF".equals(roles.getFirst())) {
            Integer staffRows = jdbc.queryForObject("SELECT COUNT(*) FROM dbo.Staff s JOIN dbo.Users u ON u.user_id=s.user_id WHERE u.email=?", Integer.class, email);
            if (staffRows == null || staffRows != 1) {
                throw new IllegalStateException("Demo staff identity is missing its Staff record. No account was changed.");
            }
        }
        return roles;
    }

    private void requireExpectedRole(DemoAccount account, List<String> roles) {
        if (roles.size() != 1 || !roles.getFirst().equals(account.role())) {
            throw new IllegalStateException("Demo email has an unexpected or ambiguous role: " + account.email()
                    + ". No existing account was changed.");
        }
    }

    private record DemoAccount(String role, String email, String fullName) { }
}
