package com.housekeeping.management;

import static org.assertj.core.api.Assertions.assertThat;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import com.housekeeping.management.domain.BookingStatus;
import com.housekeeping.management.domain.entity.Booking;
import com.housekeeping.management.domain.entity.Customer;
import com.housekeeping.management.repository.BookingRepository;
import com.housekeeping.management.repository.CustomerRepository;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@SpringBootTest
class HousekeepingManagementSystemApplicationTests extends SqlServerIntegrationTest {
    @Autowired
    private Flyway flyway;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private BookingRepository bookingRepository;

    @Test
    void contextLoads() {
        assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("1");
        Integer baselineTables = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM INFORMATION_SCHEMA.TABLES
                WHERE TABLE_TYPE = 'BASE TABLE'
                  AND TABLE_NAME IN (
                    'Customer', 'Staff', 'Admin', 'Services', 'Booking', 'Booking_Detail',
                    'Work_Schedule', 'Payment', 'Feedback', 'Favorite_Staff', 'Evidences'
                  )
                """, Integer.class);
        assertThat(baselineTables).isEqualTo(11);
    }

    @Test
    void offsetDateTimePersistsWithBusinessOffset() {
        Customer customer = new Customer();
        customer.setDisplayName("Offset test");
        customer.setEmail(UUID.randomUUID() + "@example.test");
        customer.setPasswordHash("integration-test-hash");
        customer = customerRepository.saveAndFlush(customer);

        OffsetDateTime start = OffsetDateTime.of(2026, 10, 14, 10, 15, 30, 0, ZoneOffset.ofHours(7));
        Booking booking = new Booking();
        booking.setCustomer(customer);
        booking.setStartAt(start);
        booking.setEndAt(start.plusHours(1));
        booking.setBookingStatus(BookingStatus.CONFIRMED);
        booking.setCreatedAt(start.minusDays(1));
        booking = bookingRepository.saveAndFlush(booking);

        Booking loaded = bookingRepository.findById(booking.getBookingId()).orElseThrow();
        assertThat(loaded.getStartAt()).isEqualTo(start);
        assertThat(loaded.getStartAt().getOffset()).isEqualTo(ZoneOffset.ofHours(7));
    }
}
