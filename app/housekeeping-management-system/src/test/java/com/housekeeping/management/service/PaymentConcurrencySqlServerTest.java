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
class PaymentConcurrencySqlServerTest extends SqlServerIntegrationTest {
    @Autowired private PaymentService payments;
    @Autowired private JdbcTemplate jdbc;

    @Test
    void concurrentCashConfirmationsCreateOneSuccessfulPaymentAndCompleteBookingOnce() throws Exception {
        Fixture fixture = createFixture();
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch startTogether = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> confirmCash(fixture, ready, startTogether));
            var second = executor.submit(() -> confirmCash(fixture, ready, startTogether));
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            startTogether.countDown();
            assertThat((first.get(30, TimeUnit.SECONDS) ? 1 : 0) + (second.get(30, TimeUnit.SECONDS) ? 1 : 0))
                    .isEqualTo(1);
        }

        assertThat(jdbc.queryForObject("SELECT status FROM dbo.Bookings WHERE booking_id=?", String.class,
                fixture.bookingId())).isEqualTo("COMPLETED");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM dbo.Payments WHERE booking_id=? AND status='SUCCESS'",
                Integer.class, fixture.bookingId())).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT payment_method FROM dbo.Payments WHERE booking_id=? AND status='SUCCESS'",
                String.class, fixture.bookingId())).isEqualTo("CASH");
    }

    private boolean confirmCash(Fixture fixture, CountDownLatch ready, CountDownLatch startTogether)
            throws InterruptedException {
        ready.countDown();
        if (!startTogether.await(10, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Concurrent payment test did not start");
        }
        try {
            payments.confirmCashReceived(fixture.bookingId(), fixture.staffId());
            return true;
        } catch (IllegalStateException alreadyPaid) {
            return false;
        }
    }

    private Fixture createFixture() {
        String suffix = UUID.randomUUID().toString().replace("-", "");
        long customerId = createUser("customer-" + suffix + "@example.test", "CUSTOMER");
        long staffUserId = createUser("staff-" + suffix + "@example.test", "STAFF");
        jdbc.update("INSERT INTO dbo.Staff (user_id, staff_type) VALUES (?, 'FULL_TIME')", staffUserId);
        Long staffId = jdbc.queryForObject("SELECT staff_id FROM dbo.Staff WHERE user_id=?", Long.class, staffUserId);
        jdbc.update("""
                INSERT INTO dbo.Houses (customer_id, house_name, address, latitude, longitude)
                VALUES (?, 'Nhà kiểm thử', 'Địa chỉ kiểm thử', 10.0000000, 106.0000000)
                """, customerId);
        Long houseId = jdbc.queryForObject("SELECT house_id FROM dbo.Houses WHERE customer_id=?", Long.class, customerId);
        jdbc.update("""
                INSERT INTO dbo.Bookings (customer_id, staff_id, original_staff_id, house_id,
                    address_snapshot, latitude_snapshot, longitude_snapshot, start_time, end_time,
                    total_duration_minutes, total_amount, status)
                VALUES (?, ?, ?, ?, 'Địa chỉ kiểm thử', 10.0000000, 106.0000000,
                    DATEADD(HOUR, 1, SYSDATETIME()), DATEADD(HOUR, 2, SYSDATETIME()),
                    60, 125000, 'AWAITING_PAYMENT')
                """, customerId, staffId, staffId, houseId);
        Long bookingId = jdbc.queryForObject("SELECT booking_id FROM dbo.Bookings WHERE customer_id=?", Long.class, customerId);
        return new Fixture(bookingId, staffId);
    }

    private long createUser(String email, String role) {
        jdbc.update("""
                INSERT INTO dbo.Users (full_name, email, password_hash, phone, role, status)
                VALUES (?, ?, 'integration-test-hash', '0900000000', ?, 'ACTIVE')
                """, role + " test", email, role);
        return jdbc.queryForObject("SELECT user_id FROM dbo.Users WHERE email=?", Long.class, email);
    }

    private record Fixture(Long bookingId, Long staffId) { }
}
