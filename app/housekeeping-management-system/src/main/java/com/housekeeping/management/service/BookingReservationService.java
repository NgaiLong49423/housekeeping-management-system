package com.housekeeping.management.service;

import com.housekeeping.management.domain.StaffType;
import com.housekeeping.management.domain.UserRole;
import com.housekeeping.management.domain.UserStatus;
import com.housekeeping.management.entity.Services;
import com.housekeeping.management.entity.House;
import com.housekeeping.management.entity.Staff;
import com.housekeeping.management.entity.User;
import com.housekeeping.management.repository.BookingPersistenceRepository;
import com.housekeeping.management.repository.HouseRepository;
import com.housekeeping.management.repository.ServicesRepository;
import com.housekeeping.management.repository.StaffAccountRepository;
import com.housekeeping.management.repository.UserRepository;
import java.math.BigDecimal;
import java.time.LocalTime;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BookingReservationService {
    private static final ZoneOffset BUSINESS_OFFSET = ZoneOffset.ofHours(7);
    private final UserRepository users;
    private final StaffAccountRepository staffAccounts;
    private final HouseRepository houses;
    private final ServicesRepository services;
    private final BookingPersistenceRepository bookings;
    private final HouseService houseService;
    private final VietmapClient vietmap;

    public BookingReservationService(UserRepository users, StaffAccountRepository staffAccounts,
            HouseRepository houses, ServicesRepository services,
            BookingPersistenceRepository bookings, HouseService houseService, VietmapClient vietmap) {
        this.users = users;
        this.staffAccounts = staffAccounts;
        this.houses = houses;
        this.services = services;
        this.bookings = bookings;
        this.houseService = houseService;
        this.vietmap = vietmap;
    }

    @Transactional
    public Long createBooking(Long customerId, Long staffId, Long houseId, LocalDateTime start,
            List<Long> requestedServiceIds, String areaNote) {
        User customer = users.findById(customerId)
                .filter(user -> user.getRole() == UserRole.CUSTOMER && user.getStatus() == UserStatus.ACTIVE)
                .orElseThrow(() -> new IllegalArgumentException("Tài khoản khách hàng không hợp lệ."));
        validateRequestedStart(start, now());

        // Lock order is always House then Staff. The Staff lock serializes overlap checks, including empty ranges.
        House house = houses.findLockedByHouseIdAndCustomerIdAndDeletedFalse(houseId, customerId)
                .orElseThrow(() -> new IllegalArgumentException("Nhà không tồn tại, đã ẩn hoặc không thuộc tài khoản của bạn."));
        Staff staff = staffAccounts.findActiveByStaffIdForUpdate(staffId)
                .orElseThrow(() -> new IllegalArgumentException("Nhân viên không tồn tại hoặc không hoạt động."));
        bookings.lockCompanySettings();
        List<BookingPersistenceRepository.ServiceSnapshot> selectedServices = selectedServices(requestedServiceIds);
        int totalDurationMinutes = selectedServices.stream().mapToInt(BookingPersistenceRepository.ServiceSnapshot::durationMinutes)
                .reduce(0, Math::addExact);
        BigDecimal totalAmount = selectedServices.stream().map(BookingPersistenceRepository.ServiceSnapshot::price)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        LocalDateTime end = start.plusMinutes(totalDurationMinutes);
        if (end.toLocalDate().isAfter(start.toLocalDate())) throw new IllegalArgumentException("Booking phải kết thúc trong cùng ngày và khung giờ hoạt động.");
        if (!bookings.companyAcceptsInterval(start.toLocalDate(), start.toLocalTime(), end.toLocalTime())) {
            throw new IllegalArgumentException("Toàn bộ lịch phải kết thúc trong cùng một khung giờ hoạt động của công ty.");
        }
        if (bookings.isOnApprovedLeave(staffId, start.toLocalDate())) throw new IllegalArgumentException("Nhân viên đã được duyệt nghỉ trong ngày bắt đầu này.");
        if (staff.getStaffType() == StaffType.PART_TIME && !bookings.partTimeAcceptsInterval(staffId, start.toLocalDate(), start.toLocalTime(), end.toLocalTime())) {
            throw new IllegalArgumentException("Toàn bộ lịch phải nằm trong một khoảng sẵn sàng đang hiệu lực của nhân viên.");
        }
        if (house.getLatitude() == null || house.getLongitude() == null) throw new IllegalArgumentException("Địa chỉ này chưa có tọa độ VietMap. Hãy cập nhật địa chỉ bằng kết quả gợi ý.");
        if (!houseService.isWithinServiceRadius(house.getLatitude(), house.getLongitude())) throw new IllegalArgumentException("Địa chỉ nằm ngoài bán kính phục vụ hiện tại của công ty.");
        if (hasInsufficientTravelGap(staffId, start, end, house.getLatitude(), house.getLongitude(), null)) {
            throw new IllegalArgumentException("Lịch của nhân viên bị trùng hoặc chưa đủ khoảng nghỉ/tuyến đường di chuyển.");
        }
        bookings.validateStillOpen(start, now());

        Long bookingId = bookings.insertBooking(customer.getUserId(), staff.getStaffId(), house.getHouseId(),
                house.getAddress(), house.getLatitude(), house.getLongitude(), house.getAddressNote(),
                start, end, totalDurationMinutes, totalAmount, cleanAreaNote(areaNote), now());
        bookings.insertDetails(bookingId, selectedServices);
        return bookingId;
    }

    public boolean hasInsufficientTravelGap(Long staffId, LocalDateTime start, LocalDateTime end,
            BigDecimal latitude, BigDecimal longitude, Long exceptBookingId) {
        for (BookingPersistenceRepository.AppointmentPoint adjacent : bookings.adjacentAppointments(staffId, start, end, exceptBookingId)) {
            boolean previous = !adjacent.end().isAfter(start);
            LocalDateTime from = previous ? adjacent.end() : end;
            LocalDateTime to = previous ? start : adjacent.start();
            if (adjacent.latitude() == null || adjacent.longitude() == null) throw new IllegalStateException("Thiếu tọa độ lịch gần nhất nên không tính được tuyến. Vui lòng thử lại hoặc nhờ Admin cập nhật địa chỉ.");
            int route = vietmap.motorcycleMinutes(previous ? adjacent.latitude() : latitude,
                    previous ? adjacent.longitude() : longitude, previous ? latitude : adjacent.latitude(),
                    previous ? longitude : adjacent.longitude());
            int required = Math.max(60, Math.addExact(route, 15));
            if (java.time.Duration.between(from, to).toMinutes() < required) return true;
        }
        return false;
    }

    static void validateRequestedStart(LocalDateTime start, LocalDateTime now) {
        if (start == null) {
            throw new IllegalArgumentException("Hãy chọn giờ bắt đầu.");
        }
        if (start.isBefore(now.plusMinutes(120))) {
            throw new IllegalArgumentException("Lịch phải được đặt trước giờ bắt đầu ít nhất 120 phút.");
        }
        if (start.isAfter(now.plusDays(30))) {
            throw new IllegalArgumentException("Chỉ có thể đặt lịch trong 30 ngày tới.");
        }
        if (start.getSecond() != 0 || start.getNano() != 0 || start.getMinute() % 15 != 0) {
            throw new IllegalArgumentException("Giờ bắt đầu chỉ được chọn phút 00, 15, 30 hoặc 45.");
        }
    }

    private List<BookingPersistenceRepository.ServiceSnapshot> selectedServices(List<Long> requestedIds) {
        if (requestedIds == null || requestedIds.isEmpty()) {
            throw new IllegalArgumentException("Hãy chọn ít nhất một dịch vụ hợp lệ.");
        }
        Map<Long, Services> byId = services.findAllById(requestedIds).stream()
                .collect(Collectors.toMap(Services::getServiceId, Function.identity()));
        List<BookingPersistenceRepository.ServiceSnapshot> selected = new ArrayList<>(requestedIds.size());
        for (Long id : requestedIds) {
            Services service = byId.get(id);
            if (service == null || service.getStatus() != Services.Status.ACTIVE) {
                throw new IllegalArgumentException("Một dịch vụ đã ngừng hoạt động hoặc không tồn tại.");
            }
            selected.add(new BookingPersistenceRepository.ServiceSnapshot(service.getServiceId(),
                    service.getServiceName(), service.getPrice(), service.getDurationMinutes()));
        }
        return List.copyOf(selected);
    }

    private String cleanAreaNote(String areaNote) {
        return areaNote == null || areaNote.isBlank() ? null : areaNote.trim();
    }

    private LocalDateTime now() {
        return LocalDateTime.now(BUSINESS_OFFSET).withNano(0);
    }
}
