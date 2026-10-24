package com.housekeeping.management.service;

import static com.housekeeping.management.dto.request.PortalForms.*;
import com.housekeeping.management.domain.BookingStatus;
import com.housekeeping.management.domain.entity.*;
import com.housekeeping.management.repository.*;
import com.housekeeping.management.repository.BookingPersistenceRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import com.housekeeping.management.repository.StaffAccountRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.housekeeping.management.service.DuplicateEmailException;

/** Small missing portal operations; the existing booking/payment/evidence services remain authoritative. */
@Service
public class PortalActionsService {
    private final EntityManager em;
    private final BookingRepository bookings;
    private final BookingPersistenceRepository bookingPersistence;
    private final StaffRepository staff;
    private final StaffAccountRepository staffAccounts;
    private final ServicesRepository services;
    private final WorkScheduleRepository schedules;
    private final JdbcTemplate jdbc;
    private final GlobalEmailUniquenessService emailUniqueness;
    private final PasswordEncoder encoder;

    public PortalActionsService(EntityManager em, BookingRepository bookings, BookingPersistenceRepository bookingPersistence,
            StaffRepository staff,
            WorkScheduleRepository schedules, JdbcTemplate jdbc,
            GlobalEmailUniquenessService emailUniqueness, PasswordEncoder encoder,
            StaffAccountRepository staffAccounts, ServicesRepository services) {
        this.em = em; this.bookings = bookings; this.bookingPersistence = bookingPersistence;
        this.staff = staff; this.staffAccounts = staffAccounts;
        this.services = services;
        this.schedules = schedules; this.jdbc = jdbc; this.emailUniqueness = emailUniqueness; this.encoder = encoder;
    }

    public static OffsetDateTime now() { return OffsetDateTime.now(ZoneOffset.ofHours(7)).withNano(0); }

    public void registerCustomer(CustomerRegistrationForm form) {
        String hash = encoder.encode(form.password());
        emailUniqueness.executeWithUniqueEmail(form.email(), canonical -> {
            em.persist(newUser(form.name(), canonical, hash, form.phone(),
                    com.housekeeping.management.domain.UserRole.CUSTOMER));
            return null;
        });
    }

    @Transactional
    public void cancel(Long bookingId, Long customerId) {
        BookingPersistenceRepository.BookingState booking = bookingPersistence.lockBooking(bookingId)
                .orElseThrow(() -> new IllegalArgumentException("Lịch hẹn không tồn tại."));
        if (!booking.customerId().equals(customerId)) {
            throw new AccessDeniedException("Lịch hẹn không thuộc tài khoản của bạn.");
        }
        if (!"CONFIRMED".equals(booking.status()) || now().toLocalDateTime().isAfter(booking.startTime().minusHours(2))) {
            throw new IllegalStateException("Chỉ có thể hủy lịch đã xác nhận trước giờ bắt đầu ít nhất 2 giờ.");
        }
        if (bookingPersistence.cancel(bookingId, customerId, "Khách hàng yêu cầu hủy lịch.", now().toLocalDateTime()) != 1) {
            throw new IllegalStateException("Trạng thái lịch hẹn vừa thay đổi. Hãy tải lại trang.");
        }
    }

    @Transactional
    public void feedback(Long bookingId, Long customerId, FeedbackForm form) {
        Booking booking = ownedBooking(bookingId, customerId);
        if (booking.getBookingStatus() != BookingStatus.COMPLETED) { throw new IllegalStateException("Chỉ đánh giá sau khi công việc hoàn thành."); }
        if (jdbc.queryForObject("SELECT COUNT(*) FROM Feedback WHERE booking_id=?", Integer.class, bookingId) != 0) {
            throw new IllegalStateException("Bạn đã đánh giá lịch hẹn này.");
        }
        Feedback feedback = new Feedback();
        feedback.setBooking(booking); feedback.setRating((byte) form.rating());
        feedback.setComment(form.comment().trim()); feedback.setCreatedAt(now()); em.persist(feedback);
    }

    @Transactional
    public void favorite(Long staffId, Long customerId, boolean favorite) {
        com.housekeeping.management.entity.Staff selected = staffAccounts.findById(staffId)
                .filter(item -> item.getUser().getStatus() == com.housekeeping.management.domain.UserStatus.ACTIVE)
                .orElseThrow(() -> new IllegalArgumentException("Nhân viên không tồn tại hoặc không hoạt động."));
        com.housekeeping.management.entity.User customer = em.find(com.housekeeping.management.entity.User.class,
                customerId, LockModeType.PESSIMISTIC_WRITE);
        if (customer == null || customer.getRole() != com.housekeeping.management.domain.UserRole.CUSTOMER
                || customer.getStatus() != com.housekeeping.management.domain.UserStatus.ACTIVE) {
            throw new AccessDeniedException("Tài khoản Customer không còn hợp lệ.");
        }
        if (favorite) {
            jdbc.update("""
                    IF NOT EXISTS (SELECT 1 FROM dbo.Staff_Follows WITH (UPDLOCK, HOLDLOCK)
                                   WHERE customer_id=? AND staff_id=?)
                        INSERT INTO dbo.Staff_Follows (customer_id, staff_id) VALUES (?, ?)
                    """, customerId, staffId, customerId, staffId);
        } else {
            jdbc.update("DELETE FROM dbo.Staff_Follows WHERE customer_id=? AND staff_id=?", customerId, staffId);
        }
    }

    public void createStaff(StaffCreateForm form) {
        String hash = encoder.encode(form.password());
        emailUniqueness.executeWithUniqueEmail(form.email(), canonical -> {
            com.housekeeping.management.entity.User user = newUser(form.name(), canonical, hash, form.phone(),
                    com.housekeeping.management.domain.UserRole.STAFF);
            em.persist(user);
            em.flush();
            com.housekeeping.management.entity.Staff item = new com.housekeeping.management.entity.Staff();
            item.setUser(user);
            item.setStaffType(form.staffType());
            em.persist(item);
            em.flush();
            return item;
        });
    }

    private com.housekeeping.management.entity.User newUser(String name, String email, String passwordHash,
            String phone, com.housekeeping.management.domain.UserRole role) {
        com.housekeeping.management.entity.User user = new com.housekeeping.management.entity.User();
        user.setFullName(name.trim());
        user.setEmail(email);
        user.setPasswordHash(passwordHash);
        user.setPhone(phone.trim());
        user.setRole(role);
        user.setStatus(com.housekeeping.management.domain.UserStatus.ACTIVE);
        return user;
    }

    @Transactional
    public void updateStaff(Long id, StaffUpdateForm form) {
        com.housekeeping.management.entity.Staff item = staffAccounts.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Nhân viên không tồn tại."));
        item.getUser().setFullName(form.name().trim());
        if (form.phone() != null && !form.phone().isBlank()) {
            item.getUser().setPhone(form.phone().trim());
        }
    }

    @Transactional
    public void saveService(Long id, ServiceForm form) {
        Services item = id == null ? new Services()
                : services.findById(id).orElseThrow(() -> new IllegalArgumentException("Dịch vụ không tồn tại."));
        item.setServiceName(form.name().trim());
        item.setDescription(form.description());
        item.setDurationMinutes(form.duration());
        item.setPrice(form.price());
        if (item.getStatus() == null) item.setStatus(Services.Status.ACTIVE);
        services.saveAndFlush(item);
    }

    @Transactional
    public void saveSchedule(Long id, Long adminId, ScheduleForm form) {
        if (!form.endAt().isAfter(form.startAt())) { throw new IllegalArgumentException("Giờ kết thúc phải sau giờ bắt đầu."); }
        // Same Staff lock as reservations prevents schedule changes racing a new booking.
        WorkSchedule item = id == null ? new WorkSchedule() : schedules.findById(id).orElseThrow(() -> new IllegalArgumentException("Lịch không tồn tại."));
        Long originalStaffId = id == null ? form.staffId() : item.getStaff().getStaffId();
        staff.findByIdForUpdate(originalStaffId).orElseThrow(() -> new IllegalArgumentException("Nhân viên không tồn tại."));
        if (!originalStaffId.equals(form.staffId())) { throw new IllegalArgumentException("Hãy tạo khoảng sẵn sàng mới khi đổi nhân viên."); }
        if (id != null) {
            var proposedStart = form.startAt().atOffset(ZoneOffset.ofHours(7));
            var proposedEnd = form.endAt().atOffset(ZoneOffset.ofHours(7));
            boolean invalidatesExistingDetail = jdbc.query("""
                    SELECT b.start_at, d.duration_minutes_snapshot,
                           (SELECT COALESCE(SUM(p.duration_minutes_snapshot),0) FROM Booking_Detail p
                            WHERE p.booking_id=d.booking_id AND p.sequence_no<d.sequence_no) AS preceding_minutes
                    FROM Booking_Detail d JOIN Booking b ON b.booking_id=d.booking_id WHERE d.work_schedule_id=?
                    """, (rs, row) -> {
                        OffsetDateTime detailStart = rs.getObject(1, OffsetDateTime.class).plusMinutes(rs.getLong(3));
                        OffsetDateTime detailEnd = detailStart.plusMinutes(rs.getInt(2));
                        return detailStart.isBefore(proposedStart) || detailEnd.isAfter(proposedEnd);
                    }, id).stream().anyMatch(Boolean::booleanValue);
            if (invalidatesExistingDetail) {
                throw new IllegalStateException("Khoảng mới không còn bao phủ lịch hẹn đã đặt. Hãy giữ thời gian đã đặt hoặc tạo khoảng mới.");
            }
        }
        item.setStaff(em.getReference(Staff.class, form.staffId())); item.setManagedByAdmin(em.getReference(Admin.class, adminId));
        item.setAvailableStart(form.startAt().atOffset(ZoneOffset.ofHours(7)));
        item.setAvailableEnd(form.endAt().atOffset(ZoneOffset.ofHours(7))); schedules.saveAndFlush(item);
    }

    @Transactional
    public void deleteStaff(Long id) {
        com.housekeeping.management.entity.Staff item = staffAccounts.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Nhân viên không tồn tại."));
        item.getUser().setStatus(com.housekeeping.management.domain.UserStatus.INACTIVE);
    }

    @Transactional
    public void activateStaff(Long id) {
        com.housekeeping.management.entity.Staff item = staffAccounts.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Nhân viên không tồn tại."));
        item.getUser().setStatus(com.housekeeping.management.domain.UserStatus.ACTIVE);
    }

    @Transactional
    public void deleteService(Long id) {
        Services item = services.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Dịch vụ không tồn tại."));
        item.setStatus(Services.Status.INACTIVE);
    }

    @Transactional
    public void activateService(Long id) {
        Services item = services.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Dịch vụ không tồn tại."));
        item.setStatus(Services.Status.ACTIVE);
    }

    private Booking ownedBooking(Long id, Long customerId) {
        Booking booking = bookings.findByIdForUpdate(id).orElseThrow(() -> new IllegalArgumentException("Lịch hẹn không tồn tại."));
        if (!booking.getCustomer().getCustomerId().equals(customerId)) { throw new AccessDeniedException("Lịch hẹn không thuộc tài khoản của bạn."); }
        return booking;
    }
}
