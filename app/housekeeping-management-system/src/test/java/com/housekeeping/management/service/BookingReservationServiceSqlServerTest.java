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
import com.housekeeping.management.repository.BookingPersistenceRepository;
import com.housekeeping.management.repository.HouseRepository;
import com.housekeeping.management.repository.ServicesRepository;
import com.housekeeping.management.repository.StaffAccountRepository;
import com.housekeeping.management.repository.StaffAvailabilityRepository;
import com.housekeeping.management.repository.StaffLeaveRepository;
import com.housekeeping.management.repository.UserRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
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
    @Autowired private StaffAvailabilityRepository availability;
    @Autowired private LeaveManagementService leaveManagement;
    @Autowired private AdminBookingManagementService adminBookingManagement;
    @Autowired private BookingCompletionService bookingCompletion;
    @Autowired private PaymentService paymentService;
    @Autowired private FeedbackService feedbackService;
    @Autowired private BookingPersistenceRepository bookingPersistence;
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
    void sqlServerConflictQueryRejectsOverlapAnd59MinuteGapButAllowsExact60MinuteGap() {
        String suffix = UUID.randomUUID().toString();
        User customer = users.saveAndFlush(user("buffer-customer-" + suffix, UserRole.CUSTOMER));
        User staffUser = users.saveAndFlush(user("buffer-staff-" + suffix, UserRole.STAFF));
        Staff staff = new Staff();
        staff.setUser(staffUser);
        staff.setStaffType(StaffType.FULL_TIME);
        Staff savedStaff = staffAccounts.saveAndFlush(staff);
        House house = houses.saveAndFlush(house(customer.getUserId(), "Buffer " + suffix));
        Services service = services.saveAndFlush(service());
        LocalDateTime existingStart = LocalDateTime.now(ZoneOffset.ofHours(7)).plusDays(4)
                .withHour(10).withMinute(0).withSecond(0).withNano(0);

        Long bookingId = reservationService.createBooking(customer.getUserId(), savedStaff.getStaffId(), house.getHouseId(),
                existingStart, List.of(service.getServiceId()), null);

        assertThat(bookingPersistence.hasConflictingBooking(savedStaff.getStaffId(),
                existingStart.plusMinutes(30), existingStart.plusMinutes(90))).isTrue();

        LocalDateTime nextSlot = existingStart.plusHours(2);
        jdbc.update("UPDATE dbo.Bookings SET end_time=? WHERE booking_id=?", existingStart.plusMinutes(61), bookingId);
        assertThat(bookingPersistence.hasConflictingBooking(savedStaff.getStaffId(),
                nextSlot, nextSlot.plusMinutes(60))).isTrue();

        jdbc.update("UPDATE dbo.Bookings SET end_time=? WHERE booking_id=?", existingStart.plusMinutes(60), bookingId);
        assertThat(bookingPersistence.hasConflictingBooking(savedStaff.getStaffId(),
                nextSlot, nextSlot.plusMinutes(60))).isFalse();
    }

    @Test
    void sameHouseAndTimeCanBeBookedWithDifferentStaff() {
        String suffix = UUID.randomUUID().toString();
        User customer = users.saveAndFlush(user("different-staff-customer-" + suffix, UserRole.CUSTOMER));
        User firstStaffUser = users.saveAndFlush(user("different-staff-first-" + suffix, UserRole.STAFF));
        User secondStaffUser = users.saveAndFlush(user("different-staff-second-" + suffix, UserRole.STAFF));
        Staff firstStaff = new Staff();
        firstStaff.setUser(firstStaffUser);
        firstStaff.setStaffType(StaffType.FULL_TIME);
        Staff savedFirstStaff = staffAccounts.saveAndFlush(firstStaff);
        Staff secondStaff = new Staff();
        secondStaff.setUser(secondStaffUser);
        secondStaff.setStaffType(StaffType.FULL_TIME);
        Staff savedSecondStaff = staffAccounts.saveAndFlush(secondStaff);
        House house = houses.saveAndFlush(house(customer.getUserId(), "Different staff " + suffix));
        Services service = services.saveAndFlush(service());
        LocalDateTime start = LocalDateTime.now(ZoneOffset.ofHours(7)).plusDays(4)
                .withHour(10).withMinute(0).withSecond(0).withNano(0);

        Long firstBookingId = reservationService.createBooking(customer.getUserId(), savedFirstStaff.getStaffId(),
                house.getHouseId(), start, List.of(service.getServiceId()), null);
        Long secondBookingId = reservationService.createBooking(customer.getUserId(), savedSecondStaff.getStaffId(),
                house.getHouseId(), start, List.of(service.getServiceId()), null);

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM dbo.Bookings WHERE booking_id IN (?, ?) AND status='CONFIRMED'",
                Integer.class, firstBookingId, secondBookingId)).isEqualTo(2);
    }

    @Test
    void partTimeBookingUsesSqlServerTimeValuesAndMayEndAfterAvailability() {
        String suffix = UUID.randomUUID().toString();
        User customer = users.saveAndFlush(user("part-time-customer-" + suffix, UserRole.CUSTOMER));
        User staffUser = users.saveAndFlush(user("part-time-staff-" + suffix, UserRole.STAFF));
        Staff staff = new Staff();
        staff.setUser(staffUser);
        staff.setStaffType(StaffType.PART_TIME);
        Staff savedStaff = staffAccounts.saveAndFlush(staff);
        House house = houses.saveAndFlush(house(customer.getUserId(), "Part-time " + suffix));
        Services service = service();
        service.setDurationMinutes(120);
        Services savedService = services.saveAndFlush(service);
        LocalDate workDate = LocalDate.now(ZoneOffset.ofHours(7)).plusDays(4);

        assertThat(availability.insert(savedStaff.getStaffId(), workDate, LocalTime.of(8, 0),
                LocalTime.of(9, 0))).isEqualTo(1);
        Long availabilityId = jdbc.queryForObject("SELECT availability_id FROM dbo.Staff_Availability WHERE staff_id=?",
                Long.class, savedStaff.getStaffId());

        assertThat(availability.overlapsAnotherInterval(savedStaff.getStaffId(), workDate,
                LocalTime.of(8, 30), LocalTime.of(9, 30), null)).isTrue();
        assertThat(availability.overlapsAnotherInterval(savedStaff.getStaffId(), workDate,
                LocalTime.of(9, 0), LocalTime.of(10, 0), null)).isFalse();
        assertThat(availability.unchanged(availabilityId, savedStaff.getStaffId(), workDate,
                LocalTime.of(8, 0), LocalTime.of(9, 0))).isTrue();

        Long bookingId = reservationService.createBooking(customer.getUserId(), savedStaff.getStaffId(),
                house.getHouseId(), workDate.atTime(8, 30), List.of(savedService.getServiceId()), null);

        assertThat(bookingPersistence.findBooking(bookingId).orElseThrow().endTime())
                .isEqualTo(workDate.atTime(10, 30));
        assertThat(bookingPersistence.hasPartTimeAvailability(savedStaff.getStaffId(), workDate,
                LocalTime.of(9, 0))).isFalse();
    }

    @Test
    void bookingSnapshotsRemainUnchangedWhenHouseAndServiceAreEditedLater() {
        String suffix = UUID.randomUUID().toString();
        User customer = users.saveAndFlush(user("snapshot-customer-" + suffix, UserRole.CUSTOMER));
        User staffUser = users.saveAndFlush(user("snapshot-staff-" + suffix, UserRole.STAFF));
        Staff staff = new Staff();
        staff.setUser(staffUser);
        staff.setStaffType(StaffType.FULL_TIME);
        Staff savedStaff = staffAccounts.saveAndFlush(staff);
        House house = houses.saveAndFlush(house(customer.getUserId(), "Before edit " + suffix));
        house.setAddress("Địa chỉ ban đầu");
        house.setAddressNote("Ghi chú ban đầu");
        houses.saveAndFlush(house);
        Services service = services.saveAndFlush(service());
        service.setServiceName("Dịch vụ ban đầu");
        service.setPrice(new BigDecimal("125000.00"));
        services.saveAndFlush(service);
        LocalDateTime start = LocalDateTime.now(ZoneOffset.ofHours(7)).plusDays(4)
                .withHour(10).withMinute(0).withSecond(0).withNano(0);

        Long bookingId = reservationService.createBooking(customer.getUserId(), savedStaff.getStaffId(),
                house.getHouseId(), start, List.of(service.getServiceId()), null);

        house.setAddress("Địa chỉ đã sửa");
        house.setAddressNote("Ghi chú đã sửa");
        houses.saveAndFlush(house);
        service.setServiceName("Dịch vụ đã sửa");
        service.setPrice(new BigDecimal("999999.00"));
        service.setDurationMinutes(120);
        service.setStatus(Services.Status.INACTIVE);
        services.saveAndFlush(service);

        assertThat(jdbc.queryForMap("""
                SELECT b.address_snapshot, b.address_note_snapshot, d.service_name_snapshot,
                       d.unit_price, d.duration_minutes
                FROM dbo.Bookings b
                JOIN dbo.Booking_Details d ON d.booking_id=b.booking_id
                WHERE b.booking_id=?
                """, bookingId))
                .containsEntry("address_snapshot", "Địa chỉ ban đầu")
                .containsEntry("address_note_snapshot", "Ghi chú ban đầu")
                .containsEntry("service_name_snapshot", "Dịch vụ ban đầu")
                .containsEntry("unit_price", new BigDecimal("125000.00"))
                .containsEntry("duration_minutes", 60);
    }

    @Test
    void concurrentFeedbackSubmissionsPersistOnlyOneFeedbackForCompletedBooking() throws Exception {
        String suffix = UUID.randomUUID().toString();
        User customer = users.saveAndFlush(user("feedback-customer-" + suffix, UserRole.CUSTOMER));
        User staffUser = users.saveAndFlush(user("feedback-staff-" + suffix, UserRole.STAFF));
        Staff staff = new Staff();
        staff.setUser(staffUser);
        staff.setStaffType(StaffType.FULL_TIME);
        Staff savedStaff = staffAccounts.saveAndFlush(staff);
        House house = houses.saveAndFlush(house(customer.getUserId(), "Feedback " + suffix));
        Services service = services.saveAndFlush(service());
        LocalDateTime start = LocalDateTime.now(ZoneOffset.ofHours(7)).plusDays(4)
                .withHour(10).withMinute(0).withSecond(0).withNano(0);
        Long bookingId = reservationService.createBooking(customer.getUserId(), savedStaff.getStaffId(),
                house.getHouseId(), start, List.of(service.getServiceId()), null);
        bookingCompletion.completeBooking(bookingId, savedStaff.getStaffId());
        paymentService.confirmCashReceived(bookingId, savedStaff.getStaffId());

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch startTogether = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> submitFeedback(bookingId, customer.getUserId(),
                    "Nhân viên làm việc cẩn thận.", ready, startTogether));
            var second = executor.submit(() -> submitFeedback(bookingId, customer.getUserId(),
                    "Dịch vụ rất tốt.", ready, startTogether));
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            startTogether.countDown();
            assertThat((first.get(30, TimeUnit.SECONDS) ? 1 : 0)
                    + (second.get(30, TimeUnit.SECONDS) ? 1 : 0)).isEqualTo(1);
        }

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM dbo.Feedbacks WHERE booking_id=?", Integer.class, bookingId))
                .isEqualTo(1);
    }

    private boolean submitFeedback(Long bookingId, Long customerId, String content,
            CountDownLatch ready, CountDownLatch startTogether) throws InterruptedException {
        ready.countDown();
        if (!startTogether.await(10, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Concurrent feedback test did not start");
        }
        try {
            feedbackService.submit(bookingId, customerId, content);
            return true;
        } catch (IllegalStateException alreadySubmitted) {
            return false;
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
