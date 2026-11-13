package com.housekeeping.management;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.Arrays;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

/**
 * Database đã chạy migration theo số cũ (bản .bak, máy đã chạy PR #8, máy đã chạy bản sửa mật khẩu V36 cũ)
 * phải tự đồng bộ lịch sử Flyway nhờ db/shared/beforeValidate.sql rồi chạy tiếp tới phiên bản mới nhất.
 */
class FlywayRenumberedHistorySqlServerTest extends SqlServerIntegrationTest {
    // Tên database duy nhất ngoài Housekeeping_Database mà baseline db/primary chịu chạy.
    private static final String DATABASE = "home_cleaning_acceptance";
    private static final String HISTORY = "flyway_primary_schema_history";

    @Test
    void historyRestoredFromTheBackupIsRenumberedAndMigratesToTheLatestVersion() throws Exception {
        verifyRenumbering(1);
    }

    @Test
    void historyThatAlreadyRanTheOldLockReasonMigrationIsRenumbered() throws Exception {
        verifyRenumbering(2);
    }

    @Test
    void historyThatAlreadyRanTheOldPasswordRepairMigrationIsRenumbered() throws Exception {
        verifyRenumbering(3);
    }

    @Test
    void historyFromTheFirstVersionOfPullRequestEightIsRenumbered() throws Exception {
        // Bản đầu của PR #8 đặt lý do khóa là V34, khi phí hủy (nay là V32) còn chưa có.
        DriverManagerDataSource dataSource = recreateDatabase();
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        flyway(dataSource).target(MigrationVersion.fromVersion("31")).load().migrate();
        Flyway latest = flyway(dataSource).load();
        recordAsApplied(jdbc, latest, "35", "32", "rename demo accounts to realistic names",
                "V32__rename_demo_accounts_to_realistic_names.sql");
        recordAsApplied(jdbc, latest, "33", "33", "seed additional demo staff and customers",
                "V33__seed_additional_demo_staff_and_customers.sql");
        jdbc.execute(script("db/shared/V37__record_account_lock_reasons.sql"));
        recordAsApplied(jdbc, latest, "37", "34", "record account lock reasons", "V34__record_account_lock_reasons.sql");

        latest.migrate();

        assertMigratedToLatest(latest, jdbc);
    }

    /** {@code oldMigrations}: 1 = bản .bak, 2 = thêm V35 lý do khóa cũ, 3 = thêm V36 sửa mật khẩu cũ. */
    private void verifyRenumbering(int oldMigrations) throws Exception {
        DriverManagerDataSource dataSource = recreateDatabase();
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);

        // Giống bản .bak: đã chạy tới V33, với phí hủy mang số V34 và đổi tên demo mang số V32.
        flyway(dataSource).target(MigrationVersion.fromVersion("33")).load().migrate();
        Flyway latest = flyway(dataSource).load();
        renumberAsApplied(jdbc, "32", "V32__add_cancellation_penalty_policy_and_wallets.sql",
                "34", "V34__add_cancellation_penalty_policy_and_wallets.sql");
        recordAsApplied(jdbc, latest, "35", "32", "rename demo accounts to realistic names",
                "V32__rename_demo_accounts_to_realistic_names.sql");
        if (oldMigrations >= 2) {
            jdbc.execute(script("db/shared/V37__record_account_lock_reasons.sql"));
            recordAsApplied(jdbc, latest, "37", "35", "record account lock reasons",
                    "V35__record_account_lock_reasons.sql");
        }
        if (oldMigrations >= 3) {
            jdbc.execute(script("db/shared/V42__repair_truncated_staff_password_hashes.sql"));
            recordAsApplied(jdbc, latest, "42", "36", "repair truncated staff password hashes",
                    "V36__repair_truncated_staff_password_hashes.sql");
        }

        latest.migrate();

        assertMigratedToLatest(latest, jdbc);
    }

    private static void assertMigratedToLatest(Flyway latest, JdbcTemplate jdbc) {
        assertThat(latest.info().pending()).isEmpty();
        assertThat(latest.validateWithResult().validationSuccessful).isTrue();
        assertThat(jdbc.queryForList("SELECT script FROM dbo." + HISTORY + " WHERE version IN ('32','35','37','42')",
                String.class)).containsExactlyInAnyOrder(
                "V32__add_cancellation_penalty_policy_and_wallets.sql",
                "V35__rename_demo_accounts_to_realistic_names.sql",
                "V37__record_account_lock_reasons.sql",
                "V42__repair_truncated_staff_password_hashes.sql");
        // Không migration nào bị chạy lại: mỗi script chỉ có một dòng lịch sử.
        assertThat(jdbc.queryForObject("SELECT COUNT(*) - COUNT(DISTINCT script) FROM dbo." + HISTORY + " WHERE script IS NOT NULL",
                Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COL_LENGTH('dbo.Users', 'status_reason')", Integer.class)).isNotNull();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sys.tables WHERE name='Booking_Delay_Proposals'", Integer.class))
                .isEqualTo(1);
    }

    private static org.flywaydb.core.api.configuration.FluentConfiguration flyway(DriverManagerDataSource dataSource) {
        return Flyway.configure().dataSource(dataSource)
                .locations("classpath:db/primary", "classpath:db/shared")
                .table(HISTORY)
                .outOfOrder(true);
    }

    private static void renumberAsApplied(JdbcTemplate jdbc, String version, String script, String oldVersion,
            String oldScript) {
        assertThat(jdbc.update("UPDATE dbo." + HISTORY + " SET version=?, script=? WHERE version=? AND script=?",
                oldVersion, oldScript, version, script)).isEqualTo(1);
    }

    /** Ghi một dòng lịch sử như thể migration {@code newVersion} đã chạy dưới số và tên cũ. */
    private static void recordAsApplied(JdbcTemplate jdbc, Flyway latest, String newVersion, String oldVersion,
            String description, String oldScript) {
        MigrationInfo resolved = Arrays.stream(latest.info().all())
                .filter(info -> info.getVersion() != null && info.getVersion().getVersion().equals(newVersion))
                .findFirst().orElseThrow();
        jdbc.update("""
                INSERT INTO dbo.%s (installed_rank, version, description, type, script, checksum,
                                    installed_by, execution_time, success)
                SELECT MAX(installed_rank) + 1, ?, ?, 'SQL', ?, ?, 'test', 0, 1 FROM dbo.%s
                """.formatted(HISTORY, HISTORY), oldVersion, description, oldScript, resolved.getChecksum());
    }

    private static DriverManagerDataSource recreateDatabase() throws Exception {
        try (var connection = DriverManager.getConnection(SQL_SERVER.getJdbcUrl(), SQL_SERVER.getUsername(),
                SQL_SERVER.getPassword()); Statement statement = connection.createStatement()) {
            statement.execute("IF DB_ID('" + DATABASE + "') IS NOT NULL BEGIN "
                    + "ALTER DATABASE " + DATABASE + " SET SINGLE_USER WITH ROLLBACK IMMEDIATE; "
                    + "DROP DATABASE " + DATABASE + "; END");
            statement.execute("CREATE DATABASE " + DATABASE);
        }
        return new DriverManagerDataSource(SQL_SERVER.getJdbcUrl() + ";databaseName=" + DATABASE,
                SQL_SERVER.getUsername(), SQL_SERVER.getPassword());
    }

    private static String script(String path) throws Exception {
        try (var input = new ClassPathResource(path).getInputStream()) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
