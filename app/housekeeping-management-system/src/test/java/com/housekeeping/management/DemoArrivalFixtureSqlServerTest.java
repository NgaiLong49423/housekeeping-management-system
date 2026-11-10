package com.housekeeping.management;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.DriverManager;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.ZoneId;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class DemoArrivalFixtureSqlServerTest extends SqlServerIntegrationTest {
    private static final String DEMO_DATABASE = "home_cleaning_demo";
    private static Flyway demoFlyway;
    private static DriverManagerDataSource demoDataSource;

    @BeforeAll
    static void migrateDemoDatabase() throws Exception {
        try (var connection = DriverManager.getConnection(SQL_SERVER.getJdbcUrl(), SQL_SERVER.getUsername(), SQL_SERVER.getPassword());
                Statement statement = connection.createStatement()) {
            statement.execute("CREATE DATABASE " + DEMO_DATABASE);
        }

        demoDataSource = new DriverManagerDataSource(
                SQL_SERVER.getJdbcUrl() + ";databaseName=" + DEMO_DATABASE,
                SQL_SERVER.getUsername(), SQL_SERVER.getPassword());
        demoFlyway = Flyway.configure()
                .dataSource(demoDataSource)
                .locations("classpath:db/refactor", "classpath:db/shared")
                .table("flyway_schema_history")
                .load();
        demoFlyway.migrate();
    }

    @Test
    void demoMigrationSeedsOneUpcomingBookingAndKeepsFifteenBusinessTables() {
        var jdbc = new org.springframework.jdbc.core.JdbcTemplate(demoDataSource);
        LocalDateTime start = jdbc.queryForObject("SELECT start_time FROM dbo.Bookings", LocalDateTime.class);
        var now = LocalDateTime.now(ZoneId.of("Asia/Ho_Chi_Minh"));
        assertThat(start).isBetween(now.plusMinutes(8), now.plusMinutes(12));

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM dbo.Bookings", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM dbo.Booking_Details", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES
                WHERE TABLE_SCHEMA='dbo' AND TABLE_TYPE='BASE TABLE'
                  AND TABLE_NAME IN (
                    'Users', 'Staff', 'Staff_Profiles', 'Services', 'Company_Settings',
                    'Company_Operating_Hours', 'Houses', 'Staff_Availability', 'Staff_Leaves',
                    'Bookings', 'Booking_Details', 'Booking_Images', 'Payments', 'Feedbacks', 'Staff_Follows'
                  )
                """, Integer.class)).isEqualTo(15);
        assertThat(demoFlyway.info().current().getVersion().getVersion()).isEqualTo("13");
    }
}
