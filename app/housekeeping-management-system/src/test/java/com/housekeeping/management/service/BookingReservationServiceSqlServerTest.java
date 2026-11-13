package com.housekeeping.management.service;

import com.housekeeping.management.enums.ServiceStatus;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.housekeeping.management.SqlServerIntegrationTest;
import com.housekeeping.management.enums.StaffType;
import com.housekeeping.management.enums.UserRole;
import com.housekeeping.management.enums.UserStatus;
import com.housekeeping.management.enums.LeaveStatus;
import com.housekeeping.management.dto.request.PortalForms.LeaveRequestForm;
import com.housekeeping.management.entity.Services;
import com.housekeeping.management.entity.House;
import com.housekeeping.management.entity.Staff;
import com.housekeeping.management.entity.User;
import com.housekeeping.management.integration.vietmap.VietmapClient;
import com.housekeeping.management.repository.BookingPersistenceRepository;
import com.housekeeping.management.repository.BookingAvailabilityRepository;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
class BookingReservationServiceSqlServerTest extends SqlServerIntegrationTest {
    @MockitoBean private VietmapClient vietmap;
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
    @Autowired private StaffManagementService staffManagement;
    @Autowired private BookingPersistenceRepository bookingPersistence;
    @Autowired private CompanySettingsService companySettings;
    @Autowired private BookingAvailabilityRepository bookingAvailability;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PlatformTransactionManager transactionManager;

    @BeforeEach
    void seedCompanyHoursWhenEmpty() {
        org.mockito.Mockito.when(vietmap.motorcycleMinutes(org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.any())).thenReturn(1);
        org.mockito.Mockito.when(vietmap.motorcycleRoute(org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.any()))
                .thenReturn(new VietmapClient.MotorcycleRoute(new java.math.BigDecimal("1000"),1));
        org.mockito.Mockito.when(vietmap.motorcycleMinutesBeforeArrival(org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.any())).thenReturn(1);
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
    void simplifiedFeedbackMigrationKeepsTwentyEightBusinessTablesAndDropsWorkflows() {
        // 23 bảng sau V25, thêm Staff_Incident_Action_Evidence, Feedback_Case_Verifications,
        // Staff_Recovery_Installments và V34 thêm UserWallets, WalletTransactions, CancellationLogs.
        Integer tables=jdbc.queryForObject("""
                SELECT COUNT(*) FROM sys.tables t JOIN sys.schemas s ON s.schema_id=t.schema_id
                WHERE s.name='dbo' AND t.name<>'flyway_primary_schema_history'
                """,Integer.class);
        assertThat(tables).isEqualTo(28);
        Integer v10Columns=jdbc.queryForObject("""
                SELECT COUNT(*) FROM sys.columns WHERE object_id=OBJECT_ID('dbo.Payments') AND name='is_simulated'
                """,Integer.class);
        assertThat(v10Columns).isEqualTo(1);
        Integer constraints=jdbc.queryForObject("""
                SELECT COUNT(*) FROM sys.check_constraints
                WHERE name IN ('CK_Feedback_Cases_Types','CK_Booking_Financial_Entries_Approval',
                               'CK_Booking_Financial_Entries_Paid','CK_Staff_Incident_Actions_Separation')
                """,Integer.class);
        assertThat(constraints).isEqualTo(4);
        Integer conversationTable=jdbc.queryForObject("SELECT COUNT(*) FROM sys.tables WHERE object_id=OBJECT_ID('dbo.Feedback_Case_Messages')",Integer.class);
        assertThat(conversationTable).isEqualTo(1);
        Integer messageChecks=jdbc.queryForObject("SELECT COUNT(*) FROM sys.check_constraints WHERE name IN ('CK_Feedback_Case_Messages_Role','CK_Feedback_Case_Messages_Text')",Integer.class);
        assertThat(messageChecks).isEqualTo(2);
        Integer payoutResponses=jdbc.queryForObject("SELECT COUNT(*) FROM sys.tables WHERE object_id=OBJECT_ID('dbo.Financial_Payout_Responses')",Integer.class);
        assertThat(payoutResponses).isEqualTo(1);
        Integer payoutChecks=jdbc.queryForObject("SELECT COUNT(*) FROM sys.check_constraints WHERE name IN ('CK_Financial_Payout_Responses_Status','CK_Financial_Payout_Responses_Method','CK_Financial_Payout_Responses_Choice')",Integer.class);
        assertThat(payoutChecks).isEqualTo(3);
        Integer removedWorkflowTables=jdbc.queryForObject("""
                SELECT COUNT(*) FROM sys.tables WHERE name IN ('Feedback_Resolution_Plans','Feedback_Resolution_Options',
                    'Feedback_Resolution_Actions','Feedback_Work_Orders')
                """,Integer.class);
        assertThat(removedWorkflowTables).isZero();
        Integer newCaseFields=jdbc.queryForObject("""
                SELECT COUNT(*) FROM sys.columns WHERE object_id=OBJECT_ID('dbo.Feedback_Cases')
                    AND name IN ('customer_requested_resolution','case_status')
                """,Integer.class);
        assertThat(newCaseFields).isEqualTo(2);
        Integer newPayoutFields=jdbc.queryForObject("""
                SELECT COUNT(*) FROM sys.columns WHERE object_id=OBJECT_ID('dbo.Financial_Payout_Responses')
                    AND name IN ('amount_agreed_at','payout_method_selected_at','customer_confirmed_at')
                """,Integer.class);
        assertThat(newPayoutFields).isEqualTo(3);
        Integer cases=jdbc.queryForObject("SELECT COUNT(*) FROM dbo.Feedback_Cases",Integer.class);
        Integer initialMessages=jdbc.queryForObject("SELECT COUNT(*) FROM dbo.Feedback_Case_Messages WHERE sender_role='CUSTOMER' AND legacy_imported=1",Integer.class);
        assertThat(initialMessages).isEqualTo(cases);
        Integer unlinkedEvidence=jdbc.queryForObject("SELECT COUNT(*) FROM dbo.Feedback_Case_Evidence WHERE message_id IS NULL",Integer.class);
        assertThat(unlinkedEvidence).isZero();
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
    void sqlServerOverlapQueryDetectsActualTimeOverlapWithoutAnArbitraryBuffer() {
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

        assertThat(bookingPersistence.overlappingAppointments(savedStaff.getStaffId(),
                existingStart.plusMinutes(30), existingStart.plusMinutes(90), null))
                .extracting(BookingPersistenceRepository.AppointmentPoint::bookingId).containsExactly(bookingId);
        // Booking kéo dài 60 phút (10:00-11:00): khoảng bắt đầu đúng lúc Booking kết thúc không bị tính là trùng,
        // tức là không cộng thêm thời gian đệm nào.
        assertThat(bookingPersistence.overlappingAppointments(savedStaff.getStaffId(),
                existingStart.plusMinutes(60), existingStart.plusMinutes(120), null)).isEmpty();
    }

    @Test
    void companyHoursResolveTheLatestVersionAtEveryDateBoundary() {
        Long companyId=jdbc.queryForObject("SELECT TOP (1) company_id FROM dbo.Company_Settings ORDER BY company_id",Long.class);
        LocalDate first=LocalDate.of(2040,1,1),second=LocalDate.of(2040,1,10);
        try {
            for(LocalDate version:List.of(first,second)) jdbc.batchUpdate("""
                    INSERT INTO dbo.Company_Operating_Hours(company_id,effective_from,day_of_week,period_no,is_closed,opening_time,closing_time)
                    VALUES(?,?,?,1,0,?,?)
                    """,java.util.stream.IntStream.rangeClosed(1,7).boxed().toList(),7,(statement,day)->{
                statement.setLong(1,companyId);statement.setObject(2,version);statement.setInt(3,day);
                statement.setTime(4,java.sql.Time.valueOf(version.equals(first)?"13:00:00":"16:00:00"));
                statement.setTime(5,java.sql.Time.valueOf(version.equals(first)?"17:00:00":"20:00:00"));
            });
            assertThat(companySettings.acceptsInterval(first.minusDays(1),LocalTime.of(9,0),LocalTime.of(10,0))).isTrue();
            assertThat(companySettings.acceptsInterval(first,LocalTime.of(14,0),LocalTime.of(15,0))).isTrue();
            assertThat(companySettings.acceptsInterval(first,LocalTime.of(9,0),LocalTime.of(10,0))).isFalse();
            assertThat(companySettings.acceptsInterval(second.minusDays(1),LocalTime.of(14,0),LocalTime.of(15,0))).isTrue();
            assertThat(companySettings.acceptsInterval(second,LocalTime.of(17,0),LocalTime.of(18,0))).isTrue();
            assertThat(companySettings.acceptsInterval(second,LocalTime.of(14,0),LocalTime.of(15,0))).isFalse();
        } finally {
            jdbc.update("DELETE FROM dbo.Company_Operating_Hours WHERE company_id=? AND effective_from IN (?,?)",companyId,first,second);
        }
    }

    @Test
    void availabilityLookupMapsSqlServerDateTimeValuesAndActiveStaff() {
        String suffix = UUID.randomUUID().toString();
        User staffUser = users.saveAndFlush(user("availability-query-" + suffix, UserRole.STAFF));
        Staff staff = new Staff();
        staff.setUser(staffUser);
        staff.setStaffType(StaffType.PART_TIME);
        Long staffId = staffAccounts.saveAndFlush(staff).getStaffId();
        LocalDate workDate = LocalDate.now(ZoneOffset.ofHours(7)).plusDays(4);
        availability.insert(staffId,workDate,LocalTime.of(8,0),LocalTime.of(12,0));

        assertThat(bookingAvailability.activeStaff()).anyMatch(person -> person.id().equals(staffId)
                && person.staffType().equals("PART_TIME"));
        assertThat(bookingAvailability.activeAvailability(workDate))
                .anyMatch(period -> period.staffId().equals(staffId)
                        && period.start().equals(LocalTime.of(8,0)) && period.end().equals(LocalTime.of(12,0)));
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
    void partTimeBookingUsesSqlServerTimeValuesAndMustStayInsideAvailability() {
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

        // Booking của Staff bán thời gian phải nằm trọn trong một khoảng rảnh: 8:30 + 120 phút vượt quá 9:00.
        assertThatThrownBy(() -> reservationService.createBooking(customer.getUserId(), savedStaff.getStaffId(),
                house.getHouseId(), workDate.atTime(8, 30), List.of(savedService.getServiceId()), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Toàn bộ lịch phải nằm trong một khoảng sẵn sàng đang hiệu lực của nhân viên.");

        // Kết thúc đúng 9:00 (bằng giờ kết thúc khoảng rảnh) vẫn hợp lệ.
        Services shortService = service();
        shortService.setDurationMinutes(30);
        Services savedShortService = services.saveAndFlush(shortService);
        Long bookingId = reservationService.createBooking(customer.getUserId(), savedStaff.getStaffId(),
                house.getHouseId(), workDate.atTime(8, 30), List.of(savedShortService.getServiceId()), null);

        assertThat(bookingPersistence.findBooking(bookingId).orElseThrow().endTime())
                .isEqualTo(workDate.atTime(9, 0));
        assertThat(availability.existsByStaffIdAndWorkDateAndActiveTrueAndStartTimeLessThanEqualAndEndTimeGreaterThan(
                savedStaff.getStaffId(), workDate, LocalTime.of(9, 0), LocalTime.of(9, 0))).isFalse();
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
        house.setAddressDetail("12 Nguyễn Trãi");
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
        house.setAddressDetail("99 Lê Lợi");
        house.setAddressNote("Ghi chú đã sửa");
        houses.saveAndFlush(house);
        service.setServiceName("Dịch vụ đã sửa");
        service.setPrice(new BigDecimal("999999.00"));
        service.setDurationMinutes(120);
        service.setStatus(ServiceStatus.INACTIVE);
        services.saveAndFlush(service);

        assertThat(jdbc.queryForMap("""
                SELECT b.address_snapshot, b.address_note_snapshot, d.service_name_snapshot,
                       d.unit_price, d.duration_minutes
                FROM dbo.Bookings b
                JOIN dbo.Booking_Details d ON d.booking_id=b.booking_id
                WHERE b.booking_id=?
                """, bookingId))
                .containsEntry("address_snapshot", "12 Nguyễn Trãi, Địa chỉ ban đầu")
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
        // Hoàn thành chỉ được khi Staff đã đến, đã bắt đầu làm và đã tải ít nhất một ảnh minh chứng.
        jdbc.update("UPDATE dbo.Bookings SET staff_arrived_at=?, work_started_at=? WHERE booking_id=?",
                start, start, bookingId);
        jdbc.update("INSERT INTO dbo.Booking_Images (booking_id, image_url) VALUES (?, ?)",
                bookingId, "test-evidence/" + bookingId + ".jpg");
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
            feedbackService.submit(bookingId, customerId, 4, List.of(), content);
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

        Long bookingId = reservationService.createBooking(customer.getUserId(), savedStaff.getStaffId(), house.getHouseId(),
                bookingDate, List.of(service.getServiceId()), null);

        // Không được duyệt nghỉ khi Staff còn Booking trong ngày nghỉ; phải hủy (hoặc đổi Staff) trước.
        Long leaveId = leaves.findAllByStaff_StaffIdOrderByCreatedAtDesc(savedStaff.getStaffId()).getFirst().getLeaveId();
        assertThatThrownBy(() -> leaveManagement.reviewLeaveRequest(leaveId, admin.getUserId(), LeaveStatus.APPROVED))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining(String.valueOf(bookingId));
        cancelInTransaction(bookingId, admin.getUserId());
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

    @Test
    void sqlServerPreventsStaffDeactivationUntilUnfinishedBookingIsCancelled() {
        String suffix = UUID.randomUUID().toString();
        User customer = users.saveAndFlush(user("deactivate-customer-" + suffix, UserRole.CUSTOMER));
        User staffUser = users.saveAndFlush(user("deactivate-staff-" + suffix, UserRole.STAFF));
        User admin = users.saveAndFlush(user("deactivate-admin-" + suffix, UserRole.ADMIN));
        Staff staff = new Staff();
        staff.setUser(staffUser);
        staff.setStaffType(StaffType.FULL_TIME);
        Staff savedStaff = staffAccounts.saveAndFlush(staff);
        House house = houses.saveAndFlush(house(customer.getUserId(), "Deactivate " + suffix));
        Services service = services.saveAndFlush(service());
        LocalDateTime start = LocalDateTime.now(ZoneOffset.ofHours(7)).plusDays(5)
                .withHour(10).withMinute(0).withSecond(0).withNano(0);
        Long bookingId = reservationService.createBooking(customer.getUserId(), savedStaff.getStaffId(),
                house.getHouseId(), start, List.of(service.getServiceId()), null);

        assertThatThrownBy(() -> staffManagement.deactivate(admin.getUserId(), savedStaff.getStaffId(), "Integration lock reason"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("chưa hoàn tất");
        assertThat(jdbc.queryForObject("SELECT status FROM dbo.Users WHERE user_id=?", String.class,
                staffUser.getUserId())).isEqualTo("ACTIVE");

        cancelInTransaction(bookingId, admin.getUserId());
        staffManagement.deactivate(admin.getUserId(), savedStaff.getStaffId(), "Integration lock reason");
        assertThat(jdbc.queryForObject("SELECT status FROM dbo.Users WHERE user_id=?", String.class,
                staffUser.getUserId())).isEqualTo("INACTIVE");
    }

    /** Câu lệnh hủy là @Modifying nên phải chạy trong một transaction. */
    private void cancelInTransaction(Long bookingId, Long adminId) {
        Integer cancelled = new TransactionTemplate(transactionManager).execute(status ->
                bookingPersistence.cancel(bookingId, adminId, "Integration cancellation",
                        LocalDateTime.now(ZoneOffset.ofHours(7)).withNano(0)));
        assertThat(cancelled).isEqualTo(1);
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
        house.setAddressDetail("12 " + name + " Street");
        house.setRecipientName("Test Recipient");
        house.setRecipientPhone("0900000000");
        house.setLatitude(new BigDecimal("10.0000000"));
        house.setLongitude(new BigDecimal("106.0000000"));
        return house;
    }

    private Services service() {
        Services service = new Services();
        service.setServiceName("Concurrent booking test service");
        service.setDurationMinutes(60);
        service.setPrice(new BigDecimal("100000.00"));
        service.setStatus(ServiceStatus.ACTIVE);
        return service;
    }
}
