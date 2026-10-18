package com.housekeeping.management.demo;

import com.housekeeping.management.service.DuplicateEmailException;
import com.housekeeping.management.service.GlobalEmailUniquenessService;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/** Public example identities for an explicitly selected demo environment. */
@Component
@Profile("demo")
public class DemoAccountSeeder implements ApplicationRunner {
    static final String DEMO_PASSWORD = "Demo@123456";
    private static final Logger LOG = LoggerFactory.getLogger(DemoAccountSeeder.class);
    private static final String FIND_ROLES = """
            SELECT 'CUSTOMER' FROM Customer WHERE email = ?
            UNION ALL SELECT 'STAFF' FROM Staff WHERE email = ?
            UNION ALL SELECT 'ADMIN' FROM Admin WHERE email = ?
            """;
    private static final List<DemoAccount> ACCOUNTS = List.of(
            new DemoAccount("Customer", "CUSTOMER", "customer@demo.gonnha.local", "Khách hàng Demo"),
            new DemoAccount("Staff", "STAFF", "staff@demo.gonnha.local", "Nhân viên Demo"),
            new DemoAccount("Admin", "ADMIN", "admin@demo.gonnha.local", "Quản trị viên Demo"));

    private final JdbcTemplate jdbc;
    private final GlobalEmailUniquenessService emails;
    private final PasswordEncoder encoder;

    public DemoAccountSeeder(JdbcTemplate jdbc, GlobalEmailUniquenessService emails, PasswordEncoder encoder) {
        this.jdbc = jdbc;
        this.emails = emails;
        this.encoder = encoder;
    }

    @Override
    public void run(ApplicationArguments args) {
        LOG.warn("Demo profile is enabled; public example accounts are available. Use this profile only for demos.");
        for (DemoAccount account : ACCOUNTS) {
            List<String> existing = roles(account.email());
            if (!existing.isEmpty()) {
                requireExpectedRole(account, existing);
                LOG.info("Keeping existing demo account {} and its password", account.email());
                continue;
            }
            try {
                // Reuse the same SQL Server locks and canonical email rule as account administration.
                emails.executeWithUniqueEmail(account.email(), canonical -> {
                    jdbc.update("INSERT INTO " + account.table()
                                    + " (display_name, email, password_hash) VALUES (?, ?, ?)",
                            account.displayName(), canonical, encoder.encode(DEMO_PASSWORD));
                    return null;
                });
                LOG.info("Created demo account {} with role {}", account.email(), account.role());
            } catch (DuplicateEmailException concurrentInsert) {
                // A second application may have seeded the same identity while this one waited for the lock.
                requireExpectedRole(account, roles(account.email()));
            }
        }
    }

    private List<String> roles(String email) {
        return jdbc.queryForList(FIND_ROLES, String.class, email, email, email);
    }

    private void requireExpectedRole(DemoAccount account, List<String> roles) {
        if (roles.size() != 1 || !roles.getFirst().equals(account.role())) {
            throw new IllegalStateException("Demo email has an unexpected or ambiguous role: " + account.email()
                    + ". No existing account was changed.");
        }
    }

    private record DemoAccount(String table, String role, String email, String displayName) { }
}
