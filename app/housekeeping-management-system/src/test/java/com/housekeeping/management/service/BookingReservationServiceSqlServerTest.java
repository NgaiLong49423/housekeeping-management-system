package com.housekeeping.management.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.housekeeping.management.SqlServerIntegrationTest;
import com.housekeeping.management.domain.entity.Admin;
import com.housekeeping.management.domain.entity.Customer;
import com.housekeeping.management.domain.entity.Services;
import com.housekeeping.management.domain.entity.Staff;
import com.housekeeping.management.domain.entity.WorkSchedule;
import com.housekeeping.management.repository.AdminRepository;
import com.housekeeping.management.repository.BookingRepository;
import com.housekeeping.management.repository.CustomerRepository;
import com.housekeeping.management.repository.ServicesRepository;
import com.housekeeping.management.repository.StaffRepository;
import com.housekeeping.management.repository.WorkScheduleRepository;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class BookingReservationServiceSqlServerTest extends SqlServerIntegrationTest {
    @Autowired
    private BookingReservationService reservationService;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private StaffRepository staffRepository;

    @Autowired
    private AdminRepository adminRepository;

    @Autowired
    private ServicesRepository servicesRepository;

    @Autowired
    private WorkScheduleRepository workScheduleRepository;

    @Autowired
    private BookingRepository bookingRepository;

    @Test
    void concurrentRequestsForSameStaffAndIntervalAllowOnlyOneBooking() throws Exception {
        String suffix = UUID.randomUUID().toString();
        Customer firstCustomer = customerRepository.saveAndFlush(customer("first-" + suffix));
        Customer secondCustomer = customerRepository.saveAndFlush(customer("second-" + suffix));
        Staff staff = staffRepository.saveAndFlush(staff("staff-" + suffix));
        Admin admin = adminRepository.saveAndFlush(admin("admin-" + suffix));
        Services service = servicesRepository.saveAndFlush(service());

        OffsetDateTime start = OffsetDateTime.now(ZoneOffset.ofHours(7))
                .plusDays(1)
                .withHour(10).withMinute(0).withSecond(0).withNano(0);
        WorkSchedule schedule = new WorkSchedule();
        schedule.setStaff(staff);
        schedule.setManagedByAdmin(admin);
        schedule.setAvailableStart(start.minusHours(1));
        schedule.setAvailableEnd(start.plusHours(2));
        schedule = workScheduleRepository.saveAndFlush(schedule);

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch startTogether = new CountDownLatch(1);
        Long scheduleId = schedule.getWorkScheduleId();
        Long serviceId = service.getServiceId();

        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> reserve(firstCustomer.getCustomerId(), staff.getStaffId(),
                    start, scheduleId, serviceId, ready, startTogether));
            var second = executor.submit(() -> reserve(secondCustomer.getCustomerId(), staff.getStaffId(),
                    start, scheduleId, serviceId, ready, startTogether));
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            startTogether.countDown();

            boolean firstCreated = first.get(30, TimeUnit.SECONDS);
            boolean secondCreated = second.get(30, TimeUnit.SECONDS);
            assertThat((firstCreated ? 1 : 0) + (secondCreated ? 1 : 0)).isEqualTo(1);
        }
    }

    private boolean reserve(
            Long customerId,
            Long staffId,
            OffsetDateTime start,
            Long scheduleId,
            Long serviceId,
            CountDownLatch ready,
            CountDownLatch startTogether) throws InterruptedException {
        ready.countDown();
        if (!startTogether.await(10, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Concurrent booking test did not start");
        }
        try {
            reservationService.createBooking(
                    customerId,
                    staffId,
                    start,
                    List.of(new BookingReservationService.ServiceSelection(scheduleId, serviceId, 1)));
            return true;
        } catch (IllegalArgumentException expectedConflict) {
            return false;
        }
    }

    private Customer customer(String name) {
        Customer customer = new Customer();
        customer.setDisplayName(name);
        customer.setEmail(name + "@example.test");
        customer.setPasswordHash("test-hash");
        return customer;
    }

    private Staff staff(String name) {
        Staff staff = new Staff();
        staff.setDisplayName(name);
        staff.setEmail(name + "@example.test");
        staff.setPasswordHash("test-hash");
        return staff;
    }

    private Admin admin(String name) {
        Admin admin = new Admin();
        admin.setDisplayName(name);
        admin.setEmail(name + "@example.test");
        admin.setPasswordHash("test-hash");
        return admin;
    }

    private Services service() {
        Services service = new Services();
        service.setServiceName("Concurrent booking test service");
        service.setDurationMinutes(60);
        service.setBasePrice(BigDecimal.valueOf(100_000));
        return service;
    }
}
