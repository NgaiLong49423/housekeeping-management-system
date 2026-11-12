package com.housekeeping.management.demo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.housekeeping.management.integration.vietmap.VietmapClient;
import com.housekeeping.management.repository.BookingPersistenceRepository;
import com.housekeeping.management.service.BookingReservationService;
import com.housekeeping.management.service.CustomerManagementService;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Customer bấm đặt lịch và Admin bấm khóa tài khoản cùng lúc, chạy trên SQL Server thật.
 *
 * <p>Điều phải luôn đúng: không bao giờ có chuyện tài khoản đã bị khóa mà vẫn có thêm Booking chưa hoàn tất.
 * Hai test đầu ép thứ tự (ai giữ khóa dòng Customer trước) để thấy bên kia phải đứng chờ rồi bị từ chối;
 * test cuối cho hai bên chạy đua thật nhiều vòng.
 *
 * <p>Chạy riêng (cần Docker): {@code ./mvnw test "-Dtest=CustomerLockBookingRaceSqlServerTest"}
 */
@Tag("sqlserver")
@ActiveProfiles("demo")
@SpringBootTest
class CustomerLockBookingRaceSqlServerTest {
    private static final String TEST_DATABASE = "home_cleaning_demo";
    private static final ZoneId VIETNAM = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final long WAIT_SECONDS = 30;
    /** Bên đến sau phải còn đang chờ sau khoảng này thì mới chứng minh được nó bị khóa dòng chặn lại. */
    private static final long BLOCKED_CHECK_MILLIS = 1_500;
    private static final int RACE_ROUNDS = 40;
    private static final org.testcontainers.mssqlserver.MSSQLServerContainer SQL_SERVER =
            new org.testcontainers.mssqlserver.MSSQLServerContainer("mcr.microsoft.com/mssql/server:2022-CU20-ubuntu-22.04")
                    .acceptLicense();

    static {
        SQL_SERVER.start();
        try (var connection = DriverManager.getConnection(SQL_SERVER.getJdbcUrl(), SQL_SERVER.getUsername(), SQL_SERVER.getPassword());
                Statement statement = connection.createStatement()) {
            statement.execute("CREATE DATABASE " + TEST_DATABASE);
        } catch (Exception failure) {
            throw new ExceptionInInitializerError(failure);
        }
    }

    @DynamicPropertySource
    static void configureDemoDatabase(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> SQL_SERVER.getJdbcUrl() + ";databaseName=" + TEST_DATABASE);
        registry.add("spring.datasource.username", SQL_SERVER::getUsername);
        registry.add("spring.datasource.password", SQL_SERVER::getPassword);
        // Dữ liệu demo (V3, V33…) nằm trong bộ migration db/refactor + db/shared của home_cleaning_demo;
        // chỉ định rõ để test không phụ thuộc profile demo đang trỏ tới database nào.
        registry.add("spring.flyway.locations", () -> "classpath:db/refactor,classpath:db/shared");
        registry.add("spring.flyway.table", () -> "flyway_schema_history");
        registry.add("spring.flyway.baseline-on-migrate", () -> "false");
        registry.add("app.evidence.storage-root", () ->
                Path.of(System.getProperty("java.io.tmpdir"), "hsf302-lock-race-test-evidence").toString());
    }

    // VietMap là dịch vụ ngoài; thời gian di chuyển giả lập 1 phút để chỉ còn lại phần cần kiểm tra.
    @MockitoBean private VietmapClient vietmap;
    @Autowired private BookingReservationService reservations;
    @Autowired private CustomerManagementService customers;
    @Autowired private BookingPersistenceRepository bookingPersistence;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private JdbcTemplate jdbc;

    private ExecutorService pool;
    private long adminId;
    private long customerId;
    private long houseId;
    private long staffId;
    private long serviceId;
    private int slot;

    @BeforeEach
    void prepare() {
        when(vietmap.motorcycleMinutesBeforeArrival(any(), any(), any(), any(), any())).thenReturn(1);
        when(vietmap.motorcycleMinutes(any(), any(), any(), any(), any())).thenReturn(1);
        when(vietmap.motorcycleRoute(any(), any(), any(), any(), any()))
                .thenReturn(new VietmapClient.MotorcycleRoute(new BigDecimal("1000"), 1));
        pool = Executors.newFixedThreadPool(2);
        adminId = userId("admin@demo.gonnha.local");
        customerId = userId("customer2@demo.gonnha.local");
        houseId = jdbc.queryForObject("SELECT TOP 1 house_id FROM dbo.Houses WHERE customer_id=? AND is_deleted=0",
                Long.class, customerId);
        staffId = jdbc.queryForObject("""
                SELECT s.staff_id FROM dbo.Staff s JOIN dbo.Users u ON u.user_id = s.user_id
                WHERE u.email = 'staff3@demo.gonnha.local'
                """, Long.class);
        serviceId = jdbc.queryForObject("SELECT service_id FROM dbo.Services WHERE service_name=N'Vệ sinh nhà bếp'", Long.class);
    }

    @AfterEach
    void cleanUp() {
        pool.shutdownNow();
        resetCustomer();
    }

    @Test
    void whenAdminLocksFirstTheBookingWaitsAndIsThenRejected() throws Exception {
        CountDownLatch lockHeld = new CountDownLatch(1);
        CountDownLatch releaseLock = new CountDownLatch(1);
        // Admin khóa trong một transaction được giữ mở, như thể đang lưu dở.
        Future<?> admin = pool.submit(() -> inTransaction(() -> {
            customers.lock(adminId, customerId, "Khóa trong lúc Customer đang đặt lịch");
            lockHeld.countDown();
            awaitQuietly(releaseLock);
            return null;
        }));
        awaitOrFail(lockHeld, admin);

        Future<Long> booking = pool.submit(() -> reservations.createBooking(customerId, staffId, houseId, nextStart(),
                List.of(serviceId), null));
        Thread.sleep(BLOCKED_CHECK_MILLIS);
        assertThat(booking.isDone()).as("Đặt lịch phải đứng chờ Admin lưu xong").isFalse();

        releaseLock.countDown();
        admin.get(WAIT_SECONDS, TimeUnit.SECONDS);
        assertThatThrownBy(() -> booking.get(WAIT_SECONDS, TimeUnit.SECONDS))
                .isInstanceOf(ExecutionException.class).hasCauseInstanceOf(AccessDeniedException.class);

        assertThat(status()).isEqualTo("INACTIVE");
        assertThat(unfinishedBookings()).isZero();
    }

    @Test
    void whenCustomerBooksFirstTheLockWaitsAndIsThenRefused() throws Exception {
        CountDownLatch bookingHeld = new CountDownLatch(1);
        CountDownLatch releaseBooking = new CountDownLatch(1);
        // Customer đặt lịch trong một transaction được giữ mở, như thể Booking đang được lưu dở.
        Future<Long> booking = pool.submit(() -> inTransaction(() -> {
            Long bookingId = reservations.createBooking(customerId, staffId, houseId, nextStart(), List.of(serviceId), null);
            bookingHeld.countDown();
            awaitQuietly(releaseBooking);
            return bookingId;
        }));
        awaitOrFail(bookingHeld, booking);

        Future<?> lock = pool.submit(() -> {
            customers.lock(adminId, customerId, "Khóa trong lúc Customer đang đặt lịch");
            return null;
        });
        Thread.sleep(BLOCKED_CHECK_MILLIS);
        assertThat(lock.isDone()).as("Khóa tài khoản phải đứng chờ Booking lưu xong").isFalse();

        releaseBooking.countDown();
        assertThat(booking.get(WAIT_SECONDS, TimeUnit.SECONDS)).isNotNull();
        assertThatThrownBy(() -> lock.get(WAIT_SECONDS, TimeUnit.SECONDS))
                .isInstanceOf(ExecutionException.class).hasCauseInstanceOf(IllegalStateException.class)
                .hasRootCauseMessage("Không thể khóa khi Customer còn Booking đã xác nhận hoặc đang chờ thanh toán. "
                        + "Hãy hoàn tất hoặc hủy các Booking đó trước.");

        assertThat(status()).isEqualTo("ACTIVE");
        assertThat(unfinishedBookings()).isEqualTo(1);
    }

    @Test
    void realRaceNeverLeavesALockedCustomerWithANewBooking() throws Exception {
        int bookingWon = 0;
        int lockWon = 0;
        for (int round = 1; round <= RACE_ROUNDS; round++) {
            CyclicBarrier startTogether = new CyclicBarrier(2);
            LocalDateTime start = nextStart();
            // Cho một bên xuất phát trễ ngẫu nhiên vài mili giây để cả hai thứ tự (Booking trước / Khóa trước) đều xảy ra.
            boolean delayBooking = round % 2 == 0;
            long jitterMillis = java.util.concurrent.ThreadLocalRandom.current().nextLong(0, 40);
            Future<Long> booking = pool.submit(() -> {
                startTogether.await(WAIT_SECONDS, TimeUnit.SECONDS);
                if (delayBooking) Thread.sleep(jitterMillis);
                return reservations.createBooking(customerId, staffId, houseId, start, List.of(serviceId), null);
            });
            Future<?> lock = pool.submit(() -> {
                startTogether.await(WAIT_SECONDS, TimeUnit.SECONDS);
                if (!delayBooking) Thread.sleep(jitterMillis);
                customers.lock(adminId, customerId, "Vòng chạy đua " + start);
                return null;
            });

            Throwable bookingFailure = failureOf(booking);
            Throwable lockFailure = failureOf(lock);
            String status = status();
            int unfinished = unfinishedBookings();

            // Đúng một bên thắng, bên kia bị từ chối với đúng lý do, và dữ liệu khớp với bên thắng.
            if (bookingFailure == null) {
                assertThat(lockFailure).as("vòng %d: Booking đã lưu thì không được khóa", round)
                        .isInstanceOf(IllegalStateException.class);
                assertThat(status).isEqualTo("ACTIVE");
                assertThat(unfinished).isEqualTo(1);
                bookingWon++;
            } else {
                assertThat(bookingFailure).as("vòng %d: khóa trước thì Booking phải bị từ chối vì tài khoản đã khóa", round)
                        .isInstanceOf(AccessDeniedException.class);
                assertThat(lockFailure).as("vòng %d: khóa phải thành công", round).isNull();
                assertThat(status).isEqualTo("INACTIVE");
                assertThat(unfinished).isZero();
                lockWon++;
            }
            resetCustomer();
        }
        System.out.printf("[Chạy đua đặt lịch/khóa] %d vòng: Booking thắng %d lần, Khóa thắng %d lần, "
                + "không vòng nào vừa khóa vừa có Booking mới.%n", RACE_ROUNDS, bookingWon, lockWon);
        assertThat(bookingWon + lockWon).isEqualTo(RACE_ROUNDS);
    }

    /** Mỗi lần đặt dùng một khung giờ riêng (08:00–15:30, cách nhau 2,5 giờ) để không trùng lịch Staff. */
    private LocalDateTime nextStart() {
        int index = slot++;
        LocalDate day = LocalDate.now(VIETNAM).plusDays(2 + index / 4);
        return day.atTime(8, 0).plusMinutes(150L * (index % 4));
    }

    /** Đưa Customer về trạng thái ban đầu: mở khóa và hủy các Booking chưa hoàn tất do test tạo. */
    private void resetCustomer() {
        if ("INACTIVE".equals(status())) customers.unlock(adminId, customerId);
        for (Long bookingId : jdbc.queryForList("""
                SELECT booking_id FROM dbo.Bookings
                WHERE customer_id=? AND status IN ('CONFIRMED', 'AWAITING_PAYMENT')
                """, Long.class, customerId)) {
            inTransaction(() -> bookingPersistence.cancel(bookingId, adminId, "Dọn dữ liệu test", LocalDateTime.now(VIETNAM).withNano(0)));
        }
    }

    private <T> T inTransaction(Callable<T> work) {
        return new TransactionTemplate(transactionManager).execute(status -> {
            try {
                return work.call();
            } catch (RuntimeException failure) {
                throw failure;
            } catch (Exception failure) {
                throw new IllegalStateException(failure);
            }
        });
    }

    private static void awaitQuietly(CountDownLatch latch) {
        try {
            latch.await(WAIT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    /** Chờ bên đi trước giữ được khóa; nếu nó lỗi trước khi kịp báo thì ném luôn lỗi đó để dễ đọc. */
    private static void awaitOrFail(CountDownLatch latch, Future<?> worker) throws Exception {
        if (latch.await(WAIT_SECONDS, TimeUnit.SECONDS)) return;
        if (worker.isDone()) worker.get();
        throw new AssertionError("Bên đi trước không giữ được khóa trong " + WAIT_SECONDS + " giây");
    }

    private static Throwable failureOf(Future<?> future) throws Exception {
        try {
            future.get(WAIT_SECONDS, TimeUnit.SECONDS);
            return null;
        } catch (ExecutionException failure) {
            return failure.getCause();
        }
    }

    private String status() {
        return jdbc.queryForObject("SELECT status FROM dbo.Users WHERE user_id=?", String.class, customerId);
    }

    private int unfinishedBookings() {
        return jdbc.queryForObject("""
                SELECT COUNT(*) FROM dbo.Bookings WHERE customer_id=? AND status IN ('CONFIRMED', 'AWAITING_PAYMENT')
                """, Integer.class, customerId);
    }

    private long userId(String email) {
        return jdbc.queryForObject("SELECT user_id FROM dbo.Users WHERE email=?", Long.class, email);
    }
}
