package com.housekeeping.management.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.housekeeping.management.SqlServerIntegrationTest;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
class GlobalEmailUniquenessServiceSqlServerTest extends SqlServerIntegrationTest {
    @Autowired
    private GlobalEmailUniquenessService emailUniquenessService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void canonicalizesEmailBeforeCheckingAndPersisting() {
        assertThat(GlobalEmailUniquenessService.canonicalize("  Mixed.Case@Example.COM  "))
                .isEqualTo("mixed.case@example.com");
    }

    @Test
    void concurrentRoleRegistrationAllowsOnlyOneSharedUserEmail() throws Exception {
        String canonicalEmail = "race-" + UUID.randomUUID() + "@example.com";
        CountDownLatch ready = new CountDownLatch(3);
        CountDownLatch start = new CountDownLatch(1);

        try (var executor = Executors.newFixedThreadPool(3)) {
            var customerAttempt = executor.submit(() -> tryInsert(
                    "  " + canonicalEmail.toUpperCase() + " ",
                    "CUSTOMER",
                    ready,
                    start));
            var staffAttempt = executor.submit(() -> tryInsert(
                    canonicalEmail,
                    "STAFF",
                    ready,
                    start));
            var adminAttempt = executor.submit(() -> tryInsert(
                    " " + canonicalEmail.toUpperCase() + " ",
                    "ADMIN",
                    ready,
                    start));

            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            boolean customerCreated = customerAttempt.get(30, TimeUnit.SECONDS);
            boolean staffCreated = staffAttempt.get(30, TimeUnit.SECONDS);
            boolean adminCreated = adminAttempt.get(30, TimeUnit.SECONDS);

            assertThat((customerCreated ? 1 : 0) + (staffCreated ? 1 : 0) + (adminCreated ? 1 : 0))
                    .isEqualTo(1);
            Integer total = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM dbo.Users WHERE email = ?", Integer.class, canonicalEmail);
            assertThat(total).isEqualTo(1);
        }
    }

    private boolean tryInsert(
            String submittedEmail,
            String targetTable,
            CountDownLatch ready,
            CountDownLatch start) throws InterruptedException {
        ready.countDown();
        if (!start.await(10, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Concurrent email test did not start");
        }
        try {
            emailUniquenessService.executeWithUniqueEmail(submittedEmail, canonicalEmail -> {
                Long userId = jdbcTemplate.queryForObject("""
                        INSERT INTO dbo.Users (full_name, email, password_hash, phone, role)
                        OUTPUT INSERTED.user_id VALUES (?, ?, ?, ?, ?)
                        """, Long.class, "Integration " + targetTable.toLowerCase(), canonicalEmail,
                        "test-hash", "0000000000", targetTable);
                if ("STAFF".equals(targetTable)) {
                    jdbcTemplate.update("INSERT INTO dbo.Staff (user_id, staff_type) VALUES (?, 'PART_TIME')", userId);
                }
                return true;
            });
            return true;
        } catch (DuplicateEmailException expected) {
            return false;
        }
    }
}
