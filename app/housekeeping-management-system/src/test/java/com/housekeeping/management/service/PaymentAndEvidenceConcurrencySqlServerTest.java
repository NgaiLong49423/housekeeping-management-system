package com.housekeeping.management.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.housekeeping.management.SqlServerIntegrationTest;
import com.housekeeping.management.domain.BookingStatus;
import com.housekeeping.management.domain.PaymentStatus;
import com.housekeeping.management.domain.entity.Admin;
import com.housekeeping.management.domain.entity.Booking;
import com.housekeeping.management.domain.entity.BookingDetail;
import com.housekeeping.management.domain.entity.Customer;
import com.housekeeping.management.domain.entity.Evidences;
import com.housekeeping.management.domain.entity.Payment;
import com.housekeeping.management.domain.entity.Services;
import com.housekeeping.management.domain.entity.Staff;
import com.housekeeping.management.domain.entity.WorkSchedule;
import com.housekeeping.management.repository.AdminRepository;
import com.housekeeping.management.repository.BookingDetailRepository;
import com.housekeeping.management.repository.BookingRepository;
import com.housekeeping.management.repository.CustomerRepository;
import com.housekeeping.management.repository.EvidencesRepository;
import com.housekeeping.management.repository.PaymentRepository;
import com.housekeeping.management.repository.ServicesRepository;
import com.housekeeping.management.repository.StaffRepository;
import com.housekeeping.management.repository.WorkScheduleRepository;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Future;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.dao.DataIntegrityViolationException;

@SpringBootTest
class PaymentAndEvidenceConcurrencySqlServerTest extends SqlServerIntegrationTest {
    @Autowired
    private EvidenceStorageService evidenceStorageService;

    @Autowired
    private BookingCompletionService bookingCompletionService;

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

    @Autowired
    private BookingDetailRepository bookingDetailRepository;

    @Autowired
    private EvidencesRepository evidencesRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Test
    void concurrentUploadsNeverExceedFiveEvidencesForOneDetail() throws Exception {
        Fixture fixture = createFixture("upload-" + UUID.randomUUID());
        byte[] png = validPng();
        CountDownLatch ready = new CountDownLatch(6);
        CountDownLatch startTogether = new CountDownLatch(1);
        List<Future<Boolean>> results = new ArrayList<>();

        try (var executor = Executors.newFixedThreadPool(6)) {
            for (int index = 0; index < 6; index++) {
                results.add(executor.submit(() -> {
                    ready.countDown();
                    if (!startTogether.await(10, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("Concurrent evidence test did not start");
                    }
                    try {
                        evidenceStorageService.upload(
                                fixture.detailId(),
                                fixture.staffId(),
                                new MockMultipartFile("file", "proof.png", "image/png", png));
                        return true;
                    } catch (IllegalStateException limitReached) {
                        return false;
                    }
                }));
            }

            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            startTogether.countDown();
            long successfulUploads = 0;
            for (var result : results) {
                if (result.get(45, TimeUnit.SECONDS)) {
                    successfulUploads++;
                }
            }
            assertThat(successfulUploads).isEqualTo(5);
            assertThat(evidencesRepository.countByBookingDetail_BookingDetailId(fixture.detailId())).isEqualTo(5);
            for (Evidences evidence : evidencesRepository.findAll()) {
                if (evidence.getBookingDetail().getBookingDetailId().equals(fixture.detailId())) {
                    Path evidenceRoot = Path.of(
                            System.getProperty("java.io.tmpdir"), "hsf302-test-evidence");
                    assertThat(Files.isRegularFile(evidenceRoot.resolve(evidence.getStoragePath()))).isTrue();
                }
            }
        }
    }

    @Test
    void concurrentCompletionCreatesExactlyOneUnpaidPayment() throws Exception {
        Fixture fixture = createFixture("payment-" + UUID.randomUUID());
        evidenceStorageService.upload(
                fixture.detailId(),
                fixture.staffId(),
                new MockMultipartFile("file", "proof.png", "image/png", validPng()));

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch startTogether = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> complete(fixture, ready, startTogether));
            var second = executor.submit(() -> complete(fixture, ready, startTogether));
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            startTogether.countDown();
            boolean firstCompleted = first.get(30, TimeUnit.SECONDS);
            boolean secondCompleted = second.get(30, TimeUnit.SECONDS);

            assertThat((firstCompleted ? 1 : 0) + (secondCompleted ? 1 : 0)).isEqualTo(1);
        }
        var payment = paymentRepository.findByBooking_BookingId(fixture.bookingId()).orElseThrow();
        assertThat(payment.getPaymentStatus()).isEqualTo(PaymentStatus.UNPAID);
        assertThat(payment.getPaymentMethod()).isNull();
        assertThat(payment.getVerifiedAt()).isNull();
        assertThat(payment.getAmount()).isEqualByComparingTo(BigDecimal.valueOf(125_000));

        Payment duplicate = new Payment();
        duplicate.setBooking(bookingRepository.findById(fixture.bookingId()).orElseThrow());
        duplicate.setPaymentStatus(PaymentStatus.UNPAID);
        duplicate.setAmount(BigDecimal.valueOf(125_000));
        duplicate.setCreatedAt(OffsetDateTime.now(ZoneOffset.ofHours(7)).withNano(0));
        assertThatThrownBy(() -> paymentRepository.saveAndFlush(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private boolean complete(Fixture fixture, CountDownLatch ready, CountDownLatch startTogether)
            throws InterruptedException {
        ready.countDown();
        if (!startTogether.await(10, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Concurrent completion test did not start");
        }
        try {
            bookingCompletionService.completeBooking(fixture.bookingId(), fixture.staffId());
            return true;
        } catch (IllegalStateException alreadyCompleted) {
            return false;
        }
    }

    private Fixture createFixture(String suffix) {
        Customer customer = new Customer();
        customer.setDisplayName("Customer " + suffix);
        customer.setEmail(suffix + "-customer@example.test");
        customer.setPasswordHash("integration-test-hash");
        customer = customerRepository.saveAndFlush(customer);

        Staff staff = new Staff();
        staff.setDisplayName("Staff " + suffix);
        staff.setEmail(suffix + "-staff@example.test");
        staff.setPasswordHash("integration-test-hash");
        staff = staffRepository.saveAndFlush(staff);

        Admin admin = new Admin();
        admin.setDisplayName("Admin " + suffix);
        admin.setEmail(suffix + "-admin@example.test");
        admin.setPasswordHash("integration-test-hash");
        admin = adminRepository.saveAndFlush(admin);

        Services service = new Services();
        service.setServiceName("Service " + suffix);
        service.setDurationMinutes(60);
        service.setBasePrice(BigDecimal.valueOf(125_000));
        service = servicesRepository.saveAndFlush(service);

        OffsetDateTime startAt = OffsetDateTime.now(ZoneOffset.ofHours(7))
                .minusHours(2).withSecond(0).withNano(0);
        WorkSchedule schedule = new WorkSchedule();
        schedule.setStaff(staff);
        schedule.setManagedByAdmin(admin);
        schedule.setAvailableStart(startAt.minusHours(1));
        schedule.setAvailableEnd(startAt.plusHours(3));
        schedule = workScheduleRepository.saveAndFlush(schedule);

        Booking booking = new Booking();
        booking.setCustomer(customer);
        booking.setStartAt(startAt);
        booking.setEndAt(startAt.plusMinutes(60));
        booking.setBookingStatus(BookingStatus.CONFIRMED);
        booking.setCreatedAt(OffsetDateTime.now(ZoneOffset.ofHours(7)).withNano(0));
        booking = bookingRepository.saveAndFlush(booking);

        BookingDetail detail = new BookingDetail();
        detail.setBooking(booking);
        detail.setWorkSchedule(schedule);
        detail.setService(service);
        detail.setSequenceNo(1);
        detail.setDurationMinutesSnapshot(60);
        detail.setUnitPriceSnapshot(BigDecimal.valueOf(125_000));
        detail = bookingDetailRepository.saveAndFlush(detail);
        return new Fixture(booking.getBookingId(), detail.getBookingDetailId(), staff.getStaffId());
    }

    private byte[] validPng() throws Exception {
        BufferedImage image = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, "png", output);
        return output.toByteArray();
    }

    private record Fixture(Long bookingId, Long detailId, Long staffId) {
    }
}
