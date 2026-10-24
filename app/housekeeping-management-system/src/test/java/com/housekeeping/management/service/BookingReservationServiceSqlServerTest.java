package com.housekeeping.management.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.housekeeping.management.SqlServerIntegrationTest;
import com.housekeeping.management.domain.StaffType;
import com.housekeeping.management.domain.UserRole;
import com.housekeeping.management.domain.UserStatus;
import com.housekeeping.management.entity.Services;
import com.housekeeping.management.entity.House;
import com.housekeeping.management.entity.Staff;
import com.housekeeping.management.entity.User;
import com.housekeeping.management.repository.HouseRepository;
import com.housekeeping.management.repository.ServicesRepository;
import com.housekeeping.management.repository.StaffAccountRepository;
import com.housekeeping.management.repository.UserRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
class BookingReservationServiceSqlServerTest extends SqlServerIntegrationTest {
    @Autowired private BookingReservationService reservationService;
    @Autowired private UserRepository users;
    @Autowired private StaffAccountRepository staffAccounts;
    @Autowired private HouseRepository houses;
    @Autowired private ServicesRepository services;
    @Autowired private JdbcTemplate jdbc;

    @BeforeEach
    void seedCompanyHoursWhenEmpty() {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM dbo.Company_Settings", Integer.class);
        if (count == null || count == 0) {
            jdbc.update("""
                    INSERT INTO dbo.Company_Settings (company_address, latitude, longitude, service_radius_km)
                    VALUES ('Test office', 10.0000000, 106.0000000, 50.00)
                    """);
            Long companyId = jdbc.queryForObject("SELECT TOP (1) company_id FROM dbo.Company_Settings", Long.class);
            jdbc.batchUpdate("""
                    INSERT INTO dbo.Company_Operating_Hours
                        (company_id, day_of_week, opening_time, closing_time, is_closed)
                    VALUES (?, ?, '08:00', '18:00', 0)
                    """, java.util.stream.IntStream.rangeClosed(1, 7).boxed().toList(), 7,
                    (statement, day) -> {
                        statement.setLong(1, companyId);
                        statement.setInt(2, day);
                    });
        }
    }

    @Test
    void concurrentRequestsForSameStaffAndIntervalAllowOnlyOneBooking() throws Exception {
        String suffix = UUID.randomUUID().toString();
        User firstCustomer = users.saveAndFlush(user("first-" + suffix, UserRole.CUSTOMER));
        User secondCustomer = users.saveAndFlush(user("second-" + suffix, UserRole.CUSTOMER));
        User staffUser = users.saveAndFlush(user("staff-" + suffix, UserRole.STAFF));
        Staff staff = new Staff();
        staff.setUser(staffUser);
        staff.setStaffType(StaffType.FULL_TIME);
        Staff savedStaff = staffAccounts.saveAndFlush(staff);
        Long staffId = savedStaff.getStaffId();
        House firstHouse = houses.saveAndFlush(house(firstCustomer.getUserId(), "First"));
        House secondHouse = houses.saveAndFlush(house(secondCustomer.getUserId(), "Second"));
        Services service = services.saveAndFlush(service());

        LocalDateTime start = LocalDateTime.now(ZoneOffset.ofHours(7)).plusDays(4)
                .withHour(10).withMinute(0).withSecond(0).withNano(0);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch startTogether = new CountDownLatch(1);

        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> reserve(firstCustomer.getUserId(), staffId,
                    firstHouse.getHouseId(), start, service.getServiceId(), ready, startTogether));
            var second = executor.submit(() -> reserve(secondCustomer.getUserId(), staffId,
                    secondHouse.getHouseId(), start, service.getServiceId(), ready, startTogether));
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            startTogether.countDown();

            boolean firstCreated = first.get(30, TimeUnit.SECONDS);
            boolean secondCreated = second.get(30, TimeUnit.SECONDS);
            assertThat((firstCreated ? 1 : 0) + (secondCreated ? 1 : 0)).isEqualTo(1);
        }
    }

    private boolean reserve(Long customerId, Long staffId, Long houseId, LocalDateTime start,
            Long serviceId, CountDownLatch ready, CountDownLatch startTogether) throws InterruptedException {
        ready.countDown();
        if (!startTogether.await(10, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Concurrent booking test did not start");
        }
        try {
            reservationService.createBooking(customerId, staffId, houseId, start, List.of(serviceId), null);
            return true;
        } catch (IllegalArgumentException expectedConflict) {
            return false;
        }
    }

    private User user(String suffix, UserRole role) {
        User user = new User();
        user.setFullName(suffix);
        user.setEmail(suffix + "@example.test");
        user.setPasswordHash("test-hash");
        user.setPhone("0900000000");
        user.setRole(role);
        user.setStatus(UserStatus.ACTIVE);
        return user;
    }

    private House house(Long customerId, String name) {
        House house = new House();
        house.setCustomerId(customerId);
        house.setHouseName(name);
        house.setAddress(name + " address");
        house.setLatitude(new BigDecimal("10.0000000"));
        house.setLongitude(new BigDecimal("106.0000000"));
        return house;
    }

    private Services service() {
        Services service = new Services();
        service.setServiceName("Concurrent booking test service");
        service.setDurationMinutes(60);
        service.setPrice(new BigDecimal("100000.00"));
        service.setStatus(Services.Status.ACTIVE);
        return service;
    }
}
