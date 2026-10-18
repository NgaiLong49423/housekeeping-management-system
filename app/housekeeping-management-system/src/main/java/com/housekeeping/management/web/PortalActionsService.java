package com.housekeeping.management.web;

import static com.housekeeping.management.web.PortalForms.*;
import com.housekeeping.management.domain.BookingStatus;
import com.housekeeping.management.domain.entity.*;
import com.housekeeping.management.repository.*;
import com.housekeeping.management.service.GlobalEmailUniquenessService;
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
    private final BookingRepository bookings;
    private final StaffRepository staff;
    private final ServicesRepository services;
    private final WorkScheduleRepository schedules;
    private final JdbcTemplate jdbc;
    private final GlobalEmailUniquenessService emailUniqueness;
    private final PasswordEncoder encoder;

    public PortalActionsService(EntityManager em, BookingRepository bookings, StaffRepository staff,
            ServicesRepository services, WorkScheduleRepository schedules, JdbcTemplate jdbc,
            GlobalEmailUniquenessService emailUniqueness, PasswordEncoder encoder) {
        this.em = em; this.bookings = bookings; this.staff = staff; this.services = services;
        this.schedules = schedules; this.jdbc = jdbc; this.emailUniqueness = emailUniqueness; this.encoder = encoder;
    }

    public static OffsetDateTime now() { return OffsetDateTime.now(ZoneOffset.ofHours(7)).withNano(0); }

    @Transactional
    public void cancel(Long bookingId, Long customerId) {
        Booking booking = ownedBooking(bookingId, customerId);
        if (booking.getBookingStatus() != BookingStatus.CONFIRMED || now().isAfter(booking.getStartAt().minusHours(2))) {
            throw new IllegalStateException("Chỉ có thể hủy lịch đã xác nhận trước giờ bắt đầu ít nhất 2 giờ.");
        }
        booking.setBookingStatus(BookingStatus.CANCELLED);
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
        Customer customer = em.find(Customer.class, customerId, LockModeType.PESSIMISTIC_WRITE);
        if (customer == null) { throw new AccessDeniedException("Tài khoản không còn tồn tại."); }
        Staff selected = staff.findById(staffId).orElseThrow(() -> new IllegalArgumentException("Nhân viên không tồn tại."));
        FavoriteStaffId id = new FavoriteStaffId(customerId, staffId);
        FavoriteStaff existing = em.find(FavoriteStaff.class, id);
        if (favorite && existing == null) {
            FavoriteStaff item = new FavoriteStaff(); item.setId(id); item.setCustomer(customer);
            item.setStaff(selected); item.setCreatedAt(now()); em.persist(item);
        } else if (!favorite && existing != null) { em.remove(existing); }
    }

    public void createStaff(StaffCreateForm form) {
        String hash = encoder.encode(form.password());
        emailUniqueness.executeWithUniqueEmail(form.email(), canonical -> {
            Staff item = new Staff(); item.setDisplayName(form.name().trim()); item.setEmail(canonical);
            item.setPasswordHash(hash); item.setPhoneNumber(form.phone()); return staff.saveAndFlush(item);
        });
    }

    @Transactional
    public void updateStaff(Long id, StaffUpdateForm form) {
        Staff item = staff.findById(id).orElseThrow(() -> new IllegalArgumentException("Nhân viên không tồn tại."));
        item.setDisplayName(form.name().trim()); item.setPhoneNumber(form.phone());
    }

    @Transactional
    public void saveService(Long id, ServiceForm form) {
        Services item = id == null ? new Services() : services.findById(id).orElseThrow(() -> new IllegalArgumentException("Dịch vụ không tồn tại."));
        item.setServiceName(form.name().trim()); item.setDescription(form.description());
        item.setDurationMinutes(form.duration()); item.setBasePrice(form.price()); services.saveAndFlush(item);
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
        Staff item = staff.findByIdForUpdate(id).orElseThrow(() -> new IllegalArgumentException("Nhân viên không tồn tại."));
        staff.delete(item); staff.flush(); // NO ACTION foreign keys reject dependent data; no cascading delete.
    }

    @Transactional
    public void deleteService(Long id) {
        Services item = services.findById(id).orElseThrow(() -> new IllegalArgumentException("Dịch vụ không tồn tại."));
        services.delete(item); services.flush();
    }

    private Booking ownedBooking(Long id, Long customerId) {
        Booking booking = bookings.findByIdForUpdate(id).orElseThrow(() -> new IllegalArgumentException("Lịch hẹn không tồn tại."));
        if (!booking.getCustomer().getCustomerId().equals(customerId)) { throw new AccessDeniedException("Lịch hẹn không thuộc tài khoản của bạn."); }
        return booking;
    }
}
