package com.housekeeping.management;

import static org.assertj.core.api.Assertions.assertThat;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import jakarta.persistence.EntityManagerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.core.io.ClassPathResource;

@SpringBootTest
class HousekeepingManagementSystemApplicationTests extends SqlServerIntegrationTest {
    @Autowired private Flyway flyway;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private EntityManagerFactory entityManagerFactory;

    @Test
    void freshSqlServerSchemaContainsExactlyTheApprovedBusinessTables() {
        assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("13");
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

        assertThat(jdbc.queryForObject("SELECT COL_LENGTH('dbo.Bookings', 'customer_arrived_at')", Integer.class)).isNotNull();
        assertThat(jdbc.queryForObject("SELECT COL_LENGTH('dbo.Bookings', 'staff_arrived_at')", Integer.class)).isNotNull();
        assertThat(jdbc.queryForObject("SELECT COL_LENGTH('dbo.Bookings', 'incident_reported_at')", Integer.class)).isNotNull();
        assertThat(jdbc.queryForObject("SELECT COL_LENGTH('dbo.Bookings', 'incident_note')", Integer.class)).isNotNull();
        assertThat(jdbc.queryForObject("SELECT COL_LENGTH('dbo.Bookings', 'incident_expected_arrival_at')", Integer.class)).isNotNull();
        assertThat(jdbc.queryForObject("SELECT COL_LENGTH('dbo.Bookings', 'incident_customer_response')", Integer.class)).isNotNull();
        assertThat(jdbc.queryForObject("SELECT COL_LENGTH('dbo.Bookings', 'work_started_at')", Integer.class)).isNotNull();
        assertThat(jdbc.queryForObject("SELECT COL_LENGTH('dbo.Bookings', 'delay_discount_amount')", Integer.class)).isNotNull();
        assertThat(jdbc.queryForObject("SELECT COL_LENGTH('dbo.Company_Operating_Hours', 'period_no')", Integer.class)).isNotNull();
        assertThat(jdbc.queryForObject("SELECT COL_LENGTH('dbo.Company_Operating_Hours', 'effective_from')", Integer.class)).isNotNull();
        assertThat(jdbc.queryForObject("SELECT COL_LENGTH('dbo.Staff_Availability', 'is_active')", Integer.class)).isNotNull();
        assertThat(jdbc.queryForObject("SELECT COL_LENGTH('dbo.Feedbacks', 'rating')", Integer.class)).isNotNull();
        assertThat(jdbc.queryForObject("SELECT COL_LENGTH('dbo.Feedbacks', 'topics')", Integer.class)).isNotNull();
        String workStartConstraint = jdbc.queryForObject("SELECT definition FROM sys.check_constraints WHERE name='CK_Bookings_Work_Start'", String.class);
        assertThat(workStartConstraint).contains("staff_arrived_at IS NOT NULL").doesNotContain("start_time");

        Integer obsoleteTables = jdbc.queryForObject("""
                SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES
                WHERE TABLE_SCHEMA='dbo' AND TABLE_TYPE='BASE TABLE'
                  AND TABLE_NAME IN ('Customer', 'Admin', 'Work_Schedule', 'Payment_Attempt', 'Feedback')
                """, Integer.class);
        assertThat(obsoleteTables).isZero();
    }

    @Test
    void hibernateScansExactlyTheFifteenApprovedBusinessEntities() {
        assertThat(entityManagerFactory.getMetamodel().getEntities())
                .extracting(entityType -> entityType.getJavaType().getSimpleName())
                .containsExactlyInAnyOrder("User", "Staff", "StaffProfile", "Services", "CompanySettings",
                        "CompanyOperatingHour", "House", "StaffAvailability", "StaffLeave", "Booking",
                        "BookingDetail", "BookingImage", "Payment", "Feedback", "StaffFollow");
    }

    @Test
    void demoProfileFlywayLocationsIncludeSharedBookingUpgradeMigration() throws Exception {
        var properties = new ClassPathResource("application-demo.properties");
        String content;
        try (var input = properties.getInputStream()) {
            content = new String(input.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        }
        assertThat(content).contains("spring.flyway.locations=classpath:db/refactor,classpath:db/shared");
        assertThat(new ClassPathResource("db/refactor/V4__extend_booking_schedule_and_service_area.sql").exists()).isTrue();
        assertThat(new ClassPathResource("db/shared/V5__add_effective_dates_to_company_hours.sql").exists()).isTrue();
        assertThat(new ClassPathResource("db/shared/V6__track_booking_arrivals_and_incidents.sql").exists()).isTrue();
        assertThat(new ClassPathResource("db/shared/V8__handle_staff_delay_acknowledgement_and_compensation.sql").exists()).isTrue();
        assertThat(new ClassPathResource("db/shared/V9__add_customer_feedback_ratings_and_topics.sql").exists()).isTrue();
        assertThat(new ClassPathResource("db/shared/V12__allow_staff_early_work_start.sql").exists()).isTrue();
        assertThat(new ClassPathResource("db/shared/V13__add_customer_staff_delay_report.sql").exists()).isTrue();
        assertThat(new ClassPathResource("db/refactor/V7__seed_demo_arrival_booking.sql").exists()).isTrue();
    }
}
