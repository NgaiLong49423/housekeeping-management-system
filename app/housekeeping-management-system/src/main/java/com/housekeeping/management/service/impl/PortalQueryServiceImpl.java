package com.housekeeping.management.service.impl;

import com.housekeeping.management.enums.ServiceStatus;
import static com.housekeeping.management.dto.response.PortalViews.*;

import com.housekeeping.management.enums.BookingStatus;
import com.housekeeping.management.enums.FeedbackTopic;
import com.housekeeping.management.enums.PaymentStatus;
import com.housekeeping.management.enums.UserStatus;
import com.housekeeping.management.entity.*;
import com.housekeeping.management.repository.*;
import com.housekeeping.management.service.FeedbackService;
import com.housekeeping.management.service.BookingCancellationService;
import com.housekeeping.management.service.PortalQueryService;
import com.housekeeping.management.web.PortalUser;
import java.time.*;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** JPA-backed read model for portal pages. */
@Service
@Transactional(readOnly = true)
public class PortalQueryServiceImpl implements PortalQueryService {
    private static final ZoneOffset VN = ZoneOffset.ofHours(7);
    private final BookingPersistenceRepository bookings;
    private final BookingDetailRepository details;
    private final BookingImageRepository images;
    private final ServicesRepository services;
    private final StaffAccountRepository staffRepository;
    private final UserRepository users;
    private final StaffProfileRepository profiles;
    private final StaffFollowRepository follows;
    private final StaffAvailabilityRepository availabilities;
    private final PaymentPersistenceRepository payments;
    private final FeedbackRepository feedbackRepository;
    private final FeedbackService feedback;

    public PortalQueryServiceImpl(BookingPersistenceRepository bookings, BookingDetailRepository details,
            BookingImageRepository images, ServicesRepository services, StaffAccountRepository staffRepository,
            UserRepository users, StaffProfileRepository profiles, StaffFollowRepository follows,
            StaffAvailabilityRepository availabilities, PaymentPersistenceRepository payments,
            FeedbackRepository feedbackRepository, FeedbackService feedback) {
        this.bookings = bookings; this.details = details; this.images = images; this.services = services;
        this.staffRepository = staffRepository; this.users = users; this.profiles = profiles;
        this.follows = follows; this.availabilities = availabilities; this.payments = payments;
        this.feedbackRepository = feedbackRepository; this.feedback = feedback;
    }

    public List<FeedbackCard> feedback() { return feedback.findAll(); }
    public List<ServiceCard> services() { return services(null); }
    public List<ServiceCard> services(PortalUser user) {
        boolean includeInactive = user != null && user.isAdmin();
        return (includeInactive ? services.findAllByOrderByServiceNameAsc()
                : services.findAllByStatusOrderByServiceNameAsc(ServiceStatus.ACTIVE)).stream()
                .map(s -> new ServiceCard(s.getServiceId(), s.getServiceName(), s.getDescription(),
                        s.getDurationMinutes(), s.getPrice(), s.getImageUrl(), s.getStatus() == ServiceStatus.ACTIVE)).toList();
    }

    public List<StaffCard> staff(PortalUser user) {
        Long customerId = user != null && "CUSTOMER".equals(user.getRole()) ? user.getUserId() : -1L;
        boolean includeInactive = user != null && user.isAdmin();
        List<Staff> staffRows = includeInactive ? staffRepository.findAllByOrderByUser_FullNameAsc()
                : staffRepository.findAllByUser_StatusOrderByUser_FullNameAsc(UserStatus.ACTIVE);
        Map<Long, StaffProfile> profileByStaff = profiles.findAllByStaffIdIn(staffRows.stream().map(Staff::getStaffId).toList()).stream().collect(Collectors.toMap(
                StaffProfile::getStaffId, Function.identity(), (a, b) -> a));
        Set<Long> favoriteIds = follows.findAllById_CustomerId(customerId).stream()
                .map(f -> f.getId().getStaffId()).collect(Collectors.toSet());
        return staffRows.stream()
                .map(s -> { StaffProfile p = profileByStaff.get(s.getStaffId());
                    return new StaffCard(s.getStaffId(), s.getUser().getFullName(), s.getUser().getPhone(),
                            favoriteIds.contains(s.getStaffId()), s.getStaffType().name(),
                            s.getUser().getStatus() == UserStatus.ACTIVE, p == null ? null : p.getAvatarUrl(), p == null ? null : p.getBio());
                }).toList();
    }

    public List<ScheduleCard> schedules(PortalUser user) {
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh"));
        LocalTime now = LocalTime.now(ZoneId.of("Asia/Ho_Chi_Minh")).withSecond(0).withNano(0);
        if (!Set.of("STAFF", "CUSTOMER", "ADMIN", "MANAGER").contains(user.getRole())) throw new AccessDeniedException("Vai trò không hợp lệ.");
        Map<Long, Staff> staffById = staffRepository.findAll().stream()
                .filter(s -> !"CUSTOMER".equals(user.getRole()) || s.getUser().getStatus() == UserStatus.ACTIVE)
                .collect(Collectors.toMap(Staff::getStaffId, Function.identity()));
        List<StaffAvailability> rows;
        if ("STAFF".equals(user.getRole())) rows = availabilities.findAll().stream().filter(a -> Objects.equals(a.getStaffId(), user.getAccountId())).toList();
        else if ("CUSTOMER".equals(user.getRole())) rows = availabilities.findAllByActiveTrueAndWorkDateGreaterThanEqualAndWorkDateLessThan(today, today.plusDays(31))
                .stream().filter(a -> !a.getWorkDate().equals(today) || a.getStartTime().isAfter(now)).toList();
        else rows = availabilities.findAll();
        return rows.stream().sorted(Comparator.comparing(StaffAvailability::getWorkDate).thenComparing(StaffAvailability::getStartTime))
                .map(a -> { Staff s = staffById.get(a.getStaffId()); if (s == null) return null;
                    LocalDateTime start = LocalDateTime.of(a.getWorkDate(), a.getStartTime()), end = LocalDateTime.of(a.getWorkDate(), a.getEndTime());
                    return new ScheduleCard(a.getAvailabilityId(), a.getStaffId(), s.getUser().getFullName(), start.atOffset(VN), end.atOffset(VN), a.isActive());
                }).filter(Objects::nonNull).toList();
    }

    public boolean isPartTime(Long staffId) { return staffRepository.findById(staffId).map(s -> "PART_TIME".equals(s.getStaffType().name())).orElse(false); }
    public List<BusyInterval> busyIntervals() { return bookings.findAllByStatusInAndEndTimeAfterOrderByStartTime(
            List.of(BookingStatus.CONFIRMED, BookingStatus.AWAITING_PAYMENT), LocalDateTime.now(VN))
            .stream().map(b -> new BusyInterval(b.getStaffId(), b.getStartTime().atOffset(VN), b.getEndTime().atOffset(VN))).toList(); }

    public List<BookingCard> bookings(PortalUser user) {
        String role = user.getRole();
        if (!Set.of("CUSTOMER", "STAFF", "ADMIN", "MANAGER").contains(role)) throw new AccessDeniedException("Vai trò không hợp lệ.");
        List<Booking> rows = switch (role) {
            case "CUSTOMER" -> bookings.findAllByCustomerIdOrderByStartTimeDesc(user.getAccountId());
            case "STAFF" -> bookings.findAllByStaffIdOrderByStartTimeDesc(user.getAccountId());
            default -> bookings.findAll().stream().sorted(Comparator.comparing(Booking::getStartTime).reversed()).toList();
        };
        return bookingCards(rows, !"STAFF".equals(role));
    }
    public BookingCard booking(Long id, PortalUser user) {
        Booking row = bookings.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        BookingCard card = bookingCards(List.of(row), !"STAFF".equals(user.getRole())).getFirst(); authorize(card, user); return card;
    }
    public void authorize(BookingCard card, PortalUser user) {
        boolean allowed = switch (user.getRole()) { case "ADMIN", "MANAGER" -> true;
            case "CUSTOMER" -> Objects.equals(user.getAccountId(), card.customerId());
            case "STAFF" -> Objects.equals(user.getAccountId(), card.staffId()); default -> false; };
        if (!allowed) throw new AccessDeniedException("Bạn không có quyền truy cập lịch hẹn này.");
    }
    public List<DetailCard> details(Long bookingId, PortalUser user) {
        booking(bookingId, user);
        List<BookingDetail> rows = details.findAllByBookingIdOrderByBookingDetailId(bookingId);
        return java.util.stream.IntStream.range(0, rows.size()).mapToObj(i -> {
            BookingDetail d = rows.get(i);
            return new DetailCard(d.getBookingDetailId(), d.getServiceNameSnapshot(), i + 1,
                    d.getDurationMinutes(), d.getUnitPrice(), List.of());
        }).toList();
    }
    public List<EvidenceCard> bookingImages(Long bookingId, PortalUser user) {
        booking(bookingId, user);
        return images.findAllByBookingIdOrderByUploadedAtAscImageIdAsc(bookingId).stream()
                .map(i -> new EvidenceCard(i.getImageId(), time(i.getUploadedAt().atOffset(VN)))).toList();
    }
    public Long bookingIdForDetail(Long detailId, PortalUser user) {
        Long id = details.findById(detailId).map(BookingDetail::getBookingId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        booking(id, user); return id;
    }
    public String evidencePath(Long evidenceId, PortalUser user) {
        BookingImage image = images.findById(evidenceId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        booking(image.getBookingId(), user); return image.getImageUrl();
    }

    private List<BookingCard> bookingCards(List<Booking> rows, boolean showFeedback) {
        if (rows.isEmpty()) return List.of();
        List<Long> bookingIds = rows.stream().map(Booking::getBookingId).toList();
        Map<Long, User> userById = users.findAllByUserIdIn(rows.stream().map(Booking::getCustomerId).distinct().toList()).stream()
                .collect(Collectors.toMap(User::getUserId, Function.identity()));
        Map<Long, Staff> staffById = staffRepository.findAllByStaffIdIn(rows.stream().map(Booking::getStaffId).distinct().toList()).stream()
                .collect(Collectors.toMap(Staff::getStaffId, Function.identity()));
        Map<Long, Feedback> feedbackByBooking = showFeedback ? feedbackRepository.findAllByBookingIdIn(bookingIds).stream()
                .collect(Collectors.toMap(Feedback::getBookingId, Function.identity(), (a,b) -> a)) : Map.of();
        Map<Long, Payment> paymentByBooking = new HashMap<>();
        for (Payment payment : payments.findAllByBookingIdInOrderByCreatedAtDescPaymentIdDesc(bookingIds))
            paymentByBooking.putIfAbsent(payment.getBookingId(), payment);
        LocalDateTime now = LocalDateTime.now(VN);
        return rows.stream().map(b -> {
            User customer = userById.get(b.getCustomerId()); Staff staff = staffById.get(b.getStaffId());
            Feedback f = showFeedback ? feedbackByBooking.get(b.getBookingId()) : null; Payment p = paymentByBooking.get(b.getBookingId());
            return new BookingCard(b.getBookingId(), b.getCustomerId(), b.getStaffId(), customer == null ? null : customer.getFullName(),
                    staff == null ? null : staff.getUser().getFullName(), b.getStartTime().atOffset(VN), b.getEndTime().atOffset(VN),
                    b.getStatus().name(), b.getTotalAmount(), p == null ? null : p.getStatus().name(), p == null ? null : p.getPaymentMethod().name(),
                    f == null ? null : f.getContent(), f == null ? null : f.getAdminReply(), f == null ? null : f.getStatus().name(),
                    b.getAddressSnapshot(), b.getAddressNoteSnapshot(), b.getAreaNote(), b.getCancellationReason(), offset(b.getCustomerArrivedAt()),
                    customer == null ? null : customer.getPhone(), offset(b.getStaffArrivedAt()), offset(b.getIncidentReportedAt()), b.getIncidentNote(),
                    offset(b.getIncidentExpectedArrivalAt()), b.getIncidentCustomerResponse(), offset(b.getIncidentCustomerRespondedAt()), offset(b.getWorkStartedAt()),
                    b.getDelayMinutes(), b.getDelayDiscountPercent(), b.getDelayDiscountAmount(), f == null ? null : f.getRating(),
                    topics(f), topicCodes(f), offset(b.getCustomerDelayReportedAt()), offset(b.getArrivalVerifiedAt()),
                    offset(b.getArrivalVerificationRecordedAt()), b.getArrivalVerifiedBy(),
                    b.getArrivalVerificationSource(), b.getArrivalVerificationNote(),
                    "CONFIRMED".equals(b.getStatus().name()) && b.getWorkStartedAt() == null
                            && (("ACCEPTED".equals(b.getIncidentCustomerResponse())
                                    && b.getIncidentExpectedArrivalAt() != null && now.isAfter(b.getIncidentExpectedArrivalAt()))
                                || BookingCancellationService.canCustomerCancel(
                                    b.getStatus().name(), b.getStartTime(), now, b.getWorkStartedAt())));
        }).toList();
    }
    private static OffsetDateTime offset(LocalDateTime value) { return value == null ? null : value.atOffset(VN); }
    private static List<String> topics(Feedback f) { return f == null || f.getTopics() == null || f.getTopics().isBlank() ? List.of() : FeedbackTopic.labels(List.of(f.getTopics().split(","))); }
    private static List<String> topicCodes(Feedback f) { return f == null || f.getTopics() == null || f.getTopics().isBlank() ? List.of() : List.of(f.getTopics().split(",")); }
}
