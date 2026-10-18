package com.housekeeping.management.demo;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.housekeeping.management.service.DuplicateEmailException;
import com.housekeeping.management.service.GlobalEmailUniquenessService;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;

class DemoAccountSeederTest {
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final GlobalEmailUniquenessService emails = mock(GlobalEmailUniquenessService.class);
    private final PasswordEncoder encoder = PasswordEncoderFactories.createDelegatingPasswordEncoder();
    private final Map<String, List<String>> identities = new HashMap<>();
    private final Map<String, String> hashes = new HashMap<>();
    private DemoAccountSeeder seeder;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        seeder = new DemoAccountSeeder(jdbc, emails, encoder);
        when(jdbc.queryForList(anyString(), eq(String.class), any(), any(), any()))
                .thenAnswer(call -> identities.getOrDefault(call.getArgument(2), List.of()));
        when(emails.executeWithUniqueEmail(anyString(), any()))
                .thenAnswer(call -> ((Function<String, Object>) call.getArgument(1)).apply(call.getArgument(0)));
        when(jdbc.update(anyString(), any(), any(), any())).thenAnswer(call -> {
            String sql = call.getArgument(0);
            String role = sql.contains("Customer") ? "CUSTOMER" : sql.contains("Staff") ? "STAFF" : "ADMIN";
            String email = call.getArgument(2);
            identities.put(email, List.of(role));
            hashes.put(email, call.getArgument(3));
            return 1;
        });
    }

    @Test
    void createsThreeUsableRolesWithBcryptAndDoesNotDuplicateOrResetOnRestart() {
        seeder.run(new DefaultApplicationArguments());
        assertThat(identities).containsExactlyInAnyOrderEntriesOf(Map.of(
                "customer@demo.gonnha.local", List.of("CUSTOMER"),
                "staff@demo.gonnha.local", List.of("STAFF"),
                "admin@demo.gonnha.local", List.of("ADMIN")));
        hashes.values().forEach(hash -> {
            assertThat(hash).startsWith("{bcrypt}");
            assertThat(encoder.matches(DemoAccountSeeder.DEMO_PASSWORD, hash)).isTrue();
        });
        Map<String, String> originalHashes = Map.copyOf(hashes);
        seeder.run(new DefaultApplicationArguments());
        assertThat(hashes).isEqualTo(originalHashes);
        verify(jdbc, times(3)).update(anyString(), any(), any(), any());
    }

    @Test
    void existingAccountKeepsItsOwnPassword() {
        identities.put("customer@demo.gonnha.local", List.of("CUSTOMER"));
        String originalHash = encoder.encode("existing-owner-password");
        hashes.put("customer@demo.gonnha.local", originalHash);
        seeder.run(new DefaultApplicationArguments());
        assertThat(hashes.get("customer@demo.gonnha.local")).isEqualTo(originalHash);
        verify(emails, never()).executeWithUniqueEmail(eq("customer@demo.gonnha.local"), any());
    }

    @Test
    void refusesWrongRoleOrAmbiguousEmailInsteadOfGrantingPrivileges() {
        for (List<String> conflictingRoles : List.of(List.of("ADMIN"), List.of("CUSTOMER", "ADMIN"))) {
            identities.put("customer@demo.gonnha.local", conflictingRoles);
            assertThatThrownBy(() -> seeder.run(new DefaultApplicationArguments()))
                    .isInstanceOf(IllegalStateException.class).hasMessageContaining("unexpected or ambiguous role");
        }
        verifyNoInteractions(emails);
        assertThat(hashes).isEmpty();
    }

    @Test
    void acceptsAnotherStartupCreatingTheSameRoleWhileWaitingForEmailLock() {
        when(emails.executeWithUniqueEmail(eq("customer@demo.gonnha.local"), any())).thenAnswer(call -> {
            identities.put("customer@demo.gonnha.local", List.of("CUSTOMER"));
            throw new DuplicateEmailException("customer@demo.gonnha.local");
        });
        assertThatCode(() -> seeder.run(new DefaultApplicationArguments())).doesNotThrowAnyException();
        assertThat(identities).hasSize(3);
        assertThat(hashes).hasSize(2);
    }

    @Test
    void seederIsAbsentWithoutDemoProfileAndPresentOnlyWhenExplicitlyEnabled() {
        ApplicationContextRunner context = new ApplicationContextRunner()
                .withUserConfiguration(DemoAccountSeeder.class)
                .withBean(JdbcTemplate.class, () -> jdbc)
                .withBean(GlobalEmailUniquenessService.class, () -> emails)
                .withBean(PasswordEncoder.class, () -> encoder);
        context.run(app -> assertThat(app).doesNotHaveBean(DemoAccountSeeder.class));
        context.withPropertyValues("spring.profiles.active=demo")
                .run(app -> assertThat(app).hasSingleBean(DemoAccountSeeder.class));
    }
}
