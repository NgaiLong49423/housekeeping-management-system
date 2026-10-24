package com.housekeeping.management.service;

import static com.housekeeping.management.dto.request.PortalForms.*;
import com.housekeeping.management.entity.Services;
import com.housekeeping.management.entity.Staff;
import com.housekeeping.management.entity.User;
import com.housekeeping.management.domain.StaffType;
import com.housekeeping.management.domain.UserRole;
import com.housekeeping.management.domain.UserStatus;
import com.housekeeping.management.repository.ServicesRepository;
import com.housekeeping.management.repository.StaffAccountRepository;
import com.housekeeping.management.repository.StaffAvailabilityRepository;
import com.housekeeping.management.repository.BookingPersistenceRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Small missing portal operations; the existing booking/payment/evidence services remain authoritative. */
@Service
public class PortalActionsService {
    private final EntityManager em;
    private final BookingPersistenceRepository bookingPersistence;
    private final StaffAccountRepository staffAccounts;
    private final StaffAvailabilityRepository availability;
    private final ServicesRepository services;
    private final JdbcTemplate jdbc;
    private final GlobalEmailUniquenessService emailUniqueness;
    private final PasswordEncoder encoder;

    public PortalActionsService(EntityManager em, BookingPersistenceRepository bookingPersistence,
            StaffAccountRepository staffAccounts, StaffAvailabilityRepository availability, JdbcTemplate jdbc,
            GlobalEmailUniquenessService emailUniqueness, PasswordEncoder encoder,
            ServicesRepository services) {
        this.em = em; this.bookingPersistence = bookingPersistence;
        this.staffAccounts = staffAccounts; this.availability = availability;
        this.services = services;
        this.jdbc = jdbc; this.emailUniqueness = emailUniqueness; this.encoder = encoder;
    }

    public static OffsetDateTime now() { return OffsetDateTime.now(ZoneOffset.ofHours(7)).withNano(0); }

    public void registerCustomer(CustomerRegistrationForm form) {
        String hash = encoder.encode(form.password());
        emailUniqueness.executeWithUniqueEmail(form.email(), canonical -> {
            em.persist(newUser(form.name(), canonical, hash, form.phone(),
                    UserRole.CUSTOMER));
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
    public void favorite(Long staffId, Long customerId, boolean favorite) {
        Staff selected = staffAccounts.findById(staffId)
                .filter(item -> item.getUser().getStatus() == UserStatus.ACTIVE)
                .orElseThrow(() -> new IllegalArgumentException("Nhân viên không tồn tại hoặc không hoạt động."));
        User customer = em.find(User.class, customerId, LockModeType.PESSIMISTIC_WRITE);
        if (customer == null || customer.getRole() != UserRole.CUSTOMER || customer.getStatus() != UserStatus.ACTIVE) {
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
            User user = newUser(form.name(), canonical, hash, form.phone(), UserRole.STAFF);
            em.persist(user);
            em.flush();
            Staff item = new Staff();
            item.setUser(user);
            item.setStaffType(form.staffType());
            em.persist(item);
            em.flush();
            return item;
        });
    }

    private User newUser(String name, String email, String passwordHash, String phone, UserRole role) {
        User user = new User();
        user.setFullName(name.trim());
        user.setEmail(email);
        user.setPasswordHash(passwordHash);
        user.setPhone(phone.trim());
        user.setRole(role);
        user.setStatus(UserStatus.ACTIVE);
        return user;
    }

    @Transactional
    public void updateStaff(Long id, StaffUpdateForm form) {
        Staff item = staffAccounts.findById(id)
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
    public void saveAvailability(Long staffId, Long id, AvailabilityForm form) {
        if (!form.endTime().isAfter(form.startTime())) {
            throw new IllegalArgumentException("Giờ kết thúc phải sau giờ bắt đầu.");
        }
        Staff staff = staffAccounts.findActiveByStaffIdForUpdate(staffId)
                .orElseThrow(() -> new IllegalArgumentException("Nhân viên không tồn tại hoặc không hoạt động."));
        if (staff.getStaffType() != StaffType.PART_TIME) {
            throw new IllegalStateException("Nhân viên toàn thời gian làm theo giờ hoạt động công ty, không đăng ký khoảng riêng.");
        }
        if (id != null && availability.containsExistingBookingStart(id, staffId)
                && !availability.unchanged(id, staffId, form.workDate(), form.startTime(), form.endTime())) {
            throw new IllegalStateException("Không thể đổi khoảng đã có lịch hẹn bắt đầu bên trong.");
        }
        if (availability.overlapsAnotherInterval(staffId, form.workDate(), form.startTime(), form.endTime(), id)) {
            throw new IllegalArgumentException("Khoảng sẵn sàng bị chồng lên một khoảng khác trong cùng ngày.");
        }
        int affected = id == null
                ? availability.insert(staffId, form.workDate(), form.startTime(), form.endTime())
                : availability.update(id, staffId, form.workDate(), form.startTime(), form.endTime());
        if (affected != 1) throw new IllegalArgumentException("Khoảng sẵn sàng không tồn tại.");
    }

    @Transactional
    public void deleteAvailability(Long staffId, Long id) {
        Staff staff = staffAccounts.findActiveByStaffIdForUpdate(staffId)
                .orElseThrow(() -> new IllegalArgumentException("Nhân viên không tồn tại hoặc không hoạt động."));
        if (staff.getStaffType() != StaffType.PART_TIME) {
            throw new IllegalStateException("Nhân viên toàn thời gian không có khoảng sẵn sàng riêng.");
        }
        if (availability.containsExistingBookingStart(id, staffId)) {
            throw new IllegalStateException("Không thể xóa khoảng đã có lịch hẹn bắt đầu bên trong.");
        }
        if (availability.delete(id, staffId) != 1) throw new IllegalArgumentException("Khoảng sẵn sàng không tồn tại.");
    }

    @Transactional
    public void deleteStaff(Long id) {
        Staff item = staffAccounts.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Nhân viên không tồn tại."));
        item.getUser().setStatus(UserStatus.INACTIVE);
    }

    @Transactional
    public void activateStaff(Long id) {
        Staff item = staffAccounts.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Nhân viên không tồn tại."));
        item.getUser().setStatus(UserStatus.ACTIVE);
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

}
