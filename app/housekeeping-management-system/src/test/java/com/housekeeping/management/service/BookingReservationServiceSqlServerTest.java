package com.housekeeping.management.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.housekeeping.management.SqlServerIntegrationTest;
import com.housekeeping.management.domain.StaffType;
import com.housekeeping.management.domain.UserRole;
import com.housekeeping.management.domain.UserStatus;
import com.housekeeping.management.domain.LeaveStatus;
import com.housekeeping.management.dto.request.PortalForms.LeaveRequestForm;
import com.housekeeping.management.entity.Services;
import com.housekeeping.management.entity.House;
import com.housekeeping.management.entity.Staff;
import com.housekeeping.management.entity.User;
import com.housekeeping.management.repository.HouseRepository;
import com.housekeeping.management.repository.ServicesRepository;
import com.housekeeping.management.repository.StaffAccountRepository;
import com.housekeeping.management.repository.StaffLeaveRepository;
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
    @Autowired private StaffLeaveRepository leaves;
    @Autowired private LeaveManagementService leaveManagement;
    @Autowired private AdminBookingManagementService adminBookingManagement;
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

    @Test
    void pendingLeaveDoesNotBlockBookingButApprovalBlocksLaterStartsOnItsDates() {
        String suffix = UUID.randomUUID().toString();
        User customer = users.saveAndFlush(user("leave-customer-" + suffix, UserRole.CUSTOMER));
        User staffUser = users.saveAndFlush(user("leave-staff-" + suffix, UserRole.STAFF));
        User admin = users.saveAndFlush(user("leave-admin-" + suffix, UserRole.ADMIN));
        Staff staff = new Staff();
        staff.setUser(staffUser);
        staff.setStaffType(StaffType.FULL_TIME);
        Staff savedStaff = staffAccounts.saveAndFlush(staff);
        House house = houses.saveAndFlush(house(customer.getUserId(), "Leave integration"));
        Services service = services.saveAndFlush(service());
        LocalDateTime bookingDate = LocalDateTime.now(ZoneOffset.ofHours(7)).plusDays(4)
                .withHour(10).withMinute(0).withSecond(0).withNano(0);

        leaveManagement.submitLeaveRequest(savedStaff.getStaffId(), new LeaveRequestForm(
                bookingDate.toLocalDate(), bookingDate.toLocalDate(), "Integration test"));
        assertThat(leaves.findAllByStaff_StaffIdOrderByCreatedAtDesc(savedStaff.getStaffId()))
                .singleElement().satisfies(request -> assertThat(request.getStatus()).isEqualTo(LeaveStatus.PENDING));

        reservationService.createBooking(customer.getUserId(), savedStaff.getStaffId(), house.getHouseId(), bookingDate,
                List.of(service.getServiceId()), null);

        Long leaveId = leaves.findAllByStaff_StaffIdOrderByCreatedAtDesc(savedStaff.getStaffId()).getFirst().getLeaveId();
        leaveManagement.reviewLeaveRequest(leaveId, admin.getUserId(), LeaveStatus.APPROVED);

        LocalDateTime laterStart = bookingDate.plusHours(4);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> reservationService.createBooking(customer.getUserId(),
                savedStaff.getStaffId(), house.getHouseId(), laterStart, List.of(service.getServiceId()), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Nhân viên đã được duyệt nghỉ trong ngày bắt đầu này.");
    }

    @Test
    void adminReassignmentPreservesOriginalStaffAndCanCancelConfirmedBooking() {
        String suffix = UUID.randomUUID().toString();
        User customer = users.saveAndFlush(user("admin-flow-customer-" + suffix, UserRole.CUSTOMER));
        User originalUser = users.saveAndFlush(user("admin-flow-original-" + suffix, UserRole.STAFF));
        User replacementUser = users.saveAndFlush(user("admin-flow-replacement-" + suffix, UserRole.STAFF));
        User admin = users.saveAndFlush(user("admin-flow-admin-" + suffix, UserRole.ADMIN));
        Staff original = new Staff(); original.setUser(originalUser); original.setStaffType(StaffType.FULL_TIME);
        Staff savedOriginal = staffAccounts.saveAndFlush(original);
        Staff replacement = new Staff(); replacement.setUser(replacementUser); replacement.setStaffType(StaffType.FULL_TIME);
        Staff savedReplacement = staffAccounts.saveAndFlush(replacement);
        House house = houses.saveAndFlush(house(customer.getUserId(), "Admin flow " + suffix));
        Services service = services.saveAndFlush(service());
        LocalDateTime start = LocalDateTime.now(ZoneOffset.ofHours(7)).plusDays(5)
                .withHour(10).withMinute(0).withSecond(0).withNano(0);
        Long bookingId = reservationService.createBooking(customer.getUserId(), savedOriginal.getStaffId(),
                house.getHouseId(), start, List.of(service.getServiceId()), null);

        adminBookingManagement.reassignConfirmedBooking(bookingId, admin.getUserId(), savedReplacement.getStaffId());
        assertThat(jdbc.queryForMap("SELECT staff_id, original_staff_id FROM dbo.Bookings WHERE booking_id=?", bookingId))
                .containsEntry("staff_id", savedReplacement.getStaffId())
                .containsEntry("original_staff_id", savedOriginal.getStaffId());

        adminBookingManagement.cancelConfirmedBooking(bookingId, admin.getUserId(), "Admin integration cancellation");
        assertThat(jdbc.queryForMap("SELECT status, cancelled_by, cancellation_reason FROM dbo.Bookings WHERE booking_id=?", bookingId))
                .containsEntry("status", "CANCELLED")
                .containsEntry("cancelled_by", admin.getUserId())
                .containsEntry("cancellation_reason", "Admin integration cancellation");
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
