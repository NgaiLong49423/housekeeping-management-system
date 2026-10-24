package com.housekeeping.management;

import static org.assertj.core.api.Assertions.assertThat;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
class HousekeepingManagementSystemApplicationTests extends SqlServerIntegrationTest {
    @Autowired private Flyway flyway;
    @Autowired private JdbcTemplate jdbc;

    @Test
    void freshSqlServerSchemaContainsExactlyTheApprovedBusinessTables() {
        assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("1");
        Integer tableCount = jdbc.queryForObject("""
                SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES
                WHERE TABLE_SCHEMA='dbo' AND TABLE_TYPE='BASE TABLE'
                  AND TABLE_NAME IN (
                    'Users', 'Staff', 'Staff_Profiles', 'Services', 'Company_Settings',
                    'Company_Operating_Hours', 'Houses', 'Staff_Availability', 'Staff_Leaves',
                    'Bookings', 'Booking_Details', 'Booking_Images', 'Payments', 'Feedbacks', 'Staff_Follows'
                  )
                """, Integer.class);
        assertThat(tableCount).isEqualTo(15);

        Integer obsoleteTables = jdbc.queryForObject("""
                SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES
                WHERE TABLE_SCHEMA='dbo' AND TABLE_TYPE='BASE TABLE'
                  AND TABLE_NAME IN ('Customer', 'Admin', 'Work_Schedule', 'Payment_Attempt', 'Feedback')
                """, Integer.class);
        assertThat(obsoleteTables).isZero();
    }
}
