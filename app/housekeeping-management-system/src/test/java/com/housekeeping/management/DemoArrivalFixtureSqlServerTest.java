package com.housekeeping.management;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.DriverManager;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.ZoneId;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class DemoArrivalFixtureSqlServerTest extends SqlServerIntegrationTest {
    private static final String DEMO_DATABASE = "home_cleaning_demo";
    private static Flyway demoFlyway;
    private static DriverManagerDataSource demoDataSource;
    private static java.util.List<java.util.Map<String, Object>> bookingCompensationBeforeMigration;
    /** V20 seed thêm hai Booking COMPLETED; Booking đến nơi của V7 là Booking CONFIRMED duy nhất. */
    private static final String COMPENSATION_OF_ALL_BOOKINGS =
            "SELECT booking_id, delay_discount_percent, delay_discount_amount FROM dbo.Bookings ORDER BY booking_id";

    @BeforeAll
    static void migrateDemoDatabase() throws Exception {
        try (var connection = DriverManager.getConnection(SQL_SERVER.getJdbcUrl(), SQL_SERVER.getUsername(), SQL_SERVER.getPassword());
                Statement statement = connection.createStatement()) {
            statement.execute("CREATE DATABASE " + DEMO_DATABASE);
        }

        demoDataSource = new DriverManagerDataSource(
                SQL_SERVER.getJdbcUrl() + ";databaseName=" + DEMO_DATABASE,
                SQL_SERVER.getUsername(), SQL_SERVER.getPassword());
        demoFlyway = Flyway.configure().dataSource(demoDataSource)
                .locations("classpath:db/refactor", "classpath:db/shared")
                .table("flyway_schema_history").target(MigrationVersion.fromVersion("29")).load();
        demoFlyway.migrate();
        var jdbc = new org.springframework.jdbc.core.JdbcTemplate(demoDataSource);
        jdbc.update("UPDATE dbo.Booking_Delay_Compensation_Tiers SET discount_percent=100 WHERE min_delay_minutes=31");
        bookingCompensationBeforeMigration = jdbc.queryForList(COMPENSATION_OF_ALL_BOOKINGS);
        demoFlyway = Flyway.configure().dataSource(demoDataSource)
                .locations("classpath:db/refactor", "classpath:db/shared")
                .table("flyway_schema_history").load();
        demoFlyway.migrate();
    }

    @Test
    void demoMigrationSeedsOneUpcomingBookingAndKeepsFifteenBusinessTables() {
        var jdbc = new org.springframework.jdbc.core.JdbcTemplate(demoDataSource);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM dbo.Bookings WHERE status='CONFIRMED'", Integer.class)).isEqualTo(1);
        Long arrivalBookingId = jdbc.queryForObject("SELECT booking_id FROM dbo.Bookings WHERE status='CONFIRMED'", Long.class);
        LocalDateTime start = jdbc.queryForObject("SELECT start_time FROM dbo.Bookings WHERE booking_id=?",
                LocalDateTime.class, arrivalBookingId);
        var now = LocalDateTime.now(ZoneId.of("Asia/Ho_Chi_Minh"));
        assertThat(start).isBetween(now.plusMinutes(8), now.plusMinutes(12));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM dbo.Booking_Details WHERE booking_id=?",
                Integer.class, arrivalBookingId)).isEqualTo(1);

        // V30 giới hạn mức bồi thường ở 30% nhưng không sửa số liệu của các Booking đã có.
        assertThat(jdbc.queryForObject("SELECT discount_percent FROM dbo.Booking_Delay_Compensation_Tiers WHERE min_delay_minutes=31", Integer.class))
                .isEqualTo(30);
        assertThat(jdbc.queryForList(COMPENSATION_OF_ALL_BOOKINGS)).isEqualTo(bookingCompensationBeforeMigration);
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES
                WHERE TABLE_SCHEMA='dbo' AND TABLE_TYPE='BASE TABLE'
                  AND TABLE_NAME IN (
                    'Users', 'Staff', 'Staff_Profiles', 'Services', 'Company_Settings',
                    'Company_Operating_Hours', 'Houses', 'Staff_Availability', 'Staff_Leaves',
                    'Bookings', 'Booking_Details', 'Booking_Images', 'Payments', 'Feedbacks', 'Staff_Follows'
                  )
                """, Integer.class)).isEqualTo(15);
        assertThat(demoFlyway.info().pending()).isEmpty();
    }
}
