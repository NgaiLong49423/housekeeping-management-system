package com.housekeeping.management.demo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.housekeeping.management.service.DuplicateEmailException;
import com.housekeeping.management.service.GlobalEmailUniquenessService;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;

class DemoAccountSeederTest {
    private static final String DEMO_PASSWORD = "test-local-demo-password";
    private static final String ROLE_QUERY = "SELECT role FROM dbo.Users WHERE email = ?";
    private final PasswordEncoder encoder = PasswordEncoderFactories.createDelegatingPasswordEncoder();

    @Test
    void createsSharedUserRowsAndStaffProfileUsingConfiguredPassword() {
        FakeDatabase database = new FakeDatabase();
        GlobalEmailUniquenessService emails = unlockedEmailService();
        new DemoAccountSeeder(database, emails, encoder, DEMO_PASSWORD).run(new DefaultApplicationArguments());

        assertThat(database.roles).containsExactlyInAnyOrderEntriesOf(Map.of(
                "customer@demo.gonnha.local", List.of("CUSTOMER"),
                "staff@demo.gonnha.local", List.of("STAFF"),
                "staff.fulltime@demo.gonnha.local", List.of("STAFF"),
                "admin@demo.gonnha.local", List.of("ADMIN")));
        assertThat(database.staffEmails).containsExactlyInAnyOrder("staff@demo.gonnha.local", "staff.fulltime@demo.gonnha.local");
        assertThat(database.companyCreated).isTrue();
        assertThat(database.operatingDays).hasSize(7);
        assertThat(database.services).containsExactlyInAnyOrder("Dọn dẹp nhà cơ bản", "Tổng vệ sinh", "Vệ sinh nhà bếp");
        assertThat(database.houseCreated).isTrue();
        assertThat(database.availabilityCreated).isTrue();
        database.passwordHashes.values().forEach(hash -> {
            assertThat(hash).startsWith("{bcrypt}");
            assertThat(encoder.matches(DEMO_PASSWORD, hash)).isTrue();
        });
    }

    @Test
    void preservesExistingIdentityAndDoesNotResetItsPassword() {
        FakeDatabase database = new FakeDatabase();
        database.roles.put("customer@demo.gonnha.local", List.of("CUSTOMER"));
        database.passwordHashes.put("customer@demo.gonnha.local", encoder.encode("existing-password"));
        String existingHash = database.passwordHashes.get("customer@demo.gonnha.local");

        new DemoAccountSeeder(database, unlockedEmailService(), encoder, DEMO_PASSWORD)
                .run(new DefaultApplicationArguments());

        assertThat(database.passwordHashes.get("customer@demo.gonnha.local")).isEqualTo(existingHash);
        assertThat(database.roles).hasSize(4);
    }

    @Test
    void doesNotSeedIfNoLocalDemoPasswordWasConfigured() {
        FakeDatabase database = new FakeDatabase();
        new DemoAccountSeeder(database, mock(GlobalEmailUniquenessService.class), encoder, "")
                .run(new DefaultApplicationArguments());
        assertThat(database.roles).isEmpty();
        assertThat(database.companyCreated).isFalse();
    }

    @Test
    void rejectsAnExistingEmailWithAnUnexpectedRole() {
        FakeDatabase database = new FakeDatabase();
        database.roles.put("customer@demo.gonnha.local", List.of("ADMIN"));
        assertThatThrownBy(() -> new DemoAccountSeeder(database, unlockedEmailService(), encoder, DEMO_PASSWORD)
                .run(new DefaultApplicationArguments()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("unexpected or ambiguous role");
    }

    @Test
    void acceptsAnotherStartupWinningTheUniqueEmailRace() {
        FakeDatabase database = new FakeDatabase();
        GlobalEmailUniquenessService emails = mock(GlobalEmailUniquenessService.class);
        when(emails.executeWithUniqueEmail(anyString(), any())).thenAnswer(call -> {
            @SuppressWarnings("unchecked")
            Function<String, Object> insert = call.getArgument(1);
            String email = GlobalEmailUniquenessService.canonicalize(call.getArgument(0));
            if (!database.roles.containsKey(email)) {
                boolean staffAccount = email.startsWith("staff@") || email.startsWith("staff.fulltime@");
                database.roles.put(email, List.of(staffAccount ? "STAFF" : email.startsWith("admin@") ? "ADMIN" : "CUSTOMER"));
                if (staffAccount) database.staffEmails.add(email);
            }
            throw new DuplicateEmailException(email);
        });

        new DemoAccountSeeder(database, emails, encoder, DEMO_PASSWORD).run(new DefaultApplicationArguments());
        assertThat(database.roles).containsEntry("customer@demo.gonnha.local", List.of("CUSTOMER"));
        assertThat(database.roles).containsEntry("staff@demo.gonnha.local", List.of("STAFF"));
        assertThat(database.roles).containsEntry("staff.fulltime@demo.gonnha.local", List.of("STAFF"));
        assertThat(database.roles).containsEntry("admin@demo.gonnha.local", List.of("ADMIN"));
    }

    @SuppressWarnings("unchecked")
    private GlobalEmailUniquenessService unlockedEmailService() {
        GlobalEmailUniquenessService emails = mock(GlobalEmailUniquenessService.class);
        try {
            when(emails.executeWithUniqueEmail(anyString(), any())).thenAnswer(call -> {
                Function<String, Object> insert = call.getArgument(1);
                return insert.apply(GlobalEmailUniquenessService.canonicalize(call.getArgument(0)));
            });
        } catch (Throwable failure) {
            throw failure;
        }
        return emails;
    }

    private static final class FakeDatabase extends JdbcTemplate {
        private final Map<String, List<String>> roles = new HashMap<>();
        private final Map<String, String> passwordHashes = new HashMap<>();
        private final Map<String, Long> userIds = new HashMap<>();
        private final List<String> staffEmails = new java.util.ArrayList<>();
        private final Set<Integer> operatingDays = new java.util.HashSet<>();
        private final Set<String> services = new java.util.HashSet<>();
        private long nextId = 1;
        private String pendingEmail;
        private boolean companyCreated;
        private boolean houseCreated;
        private boolean availabilityCreated;

        @Override
        public <T> List<T> queryForList(String sql, Class<T> elementType, Object... args) {
            String email = String.valueOf(args[0]);
            if (sql.equals(ROLE_QUERY)) return (List<T>) roles.getOrDefault(email, List.of());
            if (sql.contains("COUNT(*)") && sql.contains("dbo.Staff")) {
                return (List<T>) List.of(staffEmails.contains(email) ? 1 : 0);
            }
            throw new UnsupportedOperationException(sql);
        }

        @Override
        public <T> T queryForObject(String sql, Class<T> requiredType, Object... args) {
            if (sql.contains("COUNT(*)") && sql.contains("dbo.Company_Settings")) return requiredType.cast(companyCreated ? 1 : 0);
            if (sql.startsWith("INSERT INTO dbo.Company_Settings")) { companyCreated = true; return requiredType.cast(1L); }
            if (sql.startsWith("SELECT TOP (1) company_id")) return requiredType.cast(1L);
            if (sql.contains("COUNT(*)") && sql.contains("dbo.Company_Operating_Hours")) return requiredType.cast(operatingDays.contains((Integer) args[1]) ? 1 : 0);
            if (sql.contains("COUNT(*)") && sql.contains("dbo.Services")) return requiredType.cast(services.contains(String.valueOf(args[0])) ? 1 : 0);
            if (sql.contains("COUNT(*)") && sql.contains("dbo.Houses")) return requiredType.cast(houseCreated ? 1 : 0);
            if (sql.startsWith("SELECT latitude FROM dbo.Company_Settings")) return requiredType.cast(new java.math.BigDecimal("10.7769000"));
            if (sql.startsWith("SELECT longitude FROM dbo.Company_Settings")) return requiredType.cast(new java.math.BigDecimal("106.7009000"));
            if (sql.contains("COUNT(*)") && sql.contains("dbo.Staff_Availability")) return requiredType.cast(availabilityCreated ? 1 : 0);
            if (sql.startsWith("SELECT user_id FROM dbo.Users")) return requiredType.cast(userIds.get(String.valueOf(args[0])));
            if (sql.startsWith("SELECT s.staff_id FROM dbo.Staff")) return requiredType.cast(10L);
            if (sql.contains("COUNT(*)") && sql.contains("dbo.Staff")) {
                String email = String.valueOf(args[0]);
                return requiredType.cast(staffEmails.contains(email) ? 1 : 0);
            }
            if (sql.startsWith("INSERT INTO dbo.Users")) {
                pendingEmail = String.valueOf(args[1]);
                String role = String.valueOf(args[4]);
                roles.put(pendingEmail, List.of(role));
                userIds.put(pendingEmail, nextId);
                passwordHashes.put(pendingEmail, String.valueOf(args[2]));
                return requiredType.cast(nextId++);
            }
            throw new UnsupportedOperationException(sql);
        }

        @Override
        public <T> T queryForObject(String sql, Class<T> requiredType) {
            if (sql.contains("COUNT(*)") && sql.contains("dbo.Company_Settings")) return requiredType.cast(companyCreated ? 1 : 0);
            throw new UnsupportedOperationException(sql);
        }

        @Override
        public int update(String sql, Object... args) {
            if (sql.startsWith("INSERT INTO dbo.Staff (")) {
                staffEmails.add(pendingEmail);
                return 1;
            }
            if (sql.contains("INSERT INTO dbo.Company_Operating_Hours")) { operatingDays.add((Integer) args[1]); return 1; }
            if (sql.contains("INSERT INTO dbo.Services")) { services.add(String.valueOf(args[0])); return 1; }
            if (sql.contains("INSERT INTO dbo.Houses")) { houseCreated = true; return 1; }
            if (sql.contains("INSERT INTO dbo.Staff_Availability")) { availabilityCreated = true; return 1; }
            throw new UnsupportedOperationException(sql);
        }
    }
}
