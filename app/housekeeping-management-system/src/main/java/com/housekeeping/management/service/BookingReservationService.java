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
    private final HouseService houseService;
    private final BookingPersistenceRepository bookings;

    public BookingReservationService(UserRepository users, StaffAccountRepository staffAccounts,
            HouseRepository houses, ServicesRepository services, HouseService houseService,
            BookingPersistenceRepository bookings) {
        this.users = users;
        this.staffAccounts = staffAccounts;
        this.houses = houses;
        this.services = services;
        this.houseService = houseService;
        this.bookings = bookings;
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
        if (!houseService.isWithinServiceRadius(house.getLatitude(), house.getLongitude())) {
            throw new IllegalArgumentException("Địa chỉ nhà nằm ngoài bán kính phục vụ của công ty.");
        }
        Staff staff = staffAccounts.findActiveByStaffIdForUpdate(staffId)
                .orElseThrow(() -> new IllegalArgumentException("Nhân viên không tồn tại hoặc không hoạt động."));
        if (!bookings.companyAcceptsStart(start.toLocalDate(), start.toLocalTime())) {
            throw new IllegalArgumentException("Thời gian bắt đầu nằm ngoài giờ hoạt động của công ty.");
        }
        if (bookings.isOnApprovedLeave(staffId, start.toLocalDate())) {
            throw new IllegalArgumentException("Nhân viên đã được duyệt nghỉ trong ngày bắt đầu này.");
        }
        if (staff.getStaffType() == StaffType.PART_TIME
                && !bookings.hasPartTimeAvailability(staffId, start.toLocalDate(), start.toLocalTime())) {
            throw new IllegalArgumentException("Nhân viên part-time chưa đăng ký khoảng sẵn sàng cho giờ bắt đầu này.");
        }

        List<BookingPersistenceRepository.ServiceSnapshot> selectedServices = selectedServices(requestedServiceIds);
        int totalDurationMinutes = selectedServices.stream().mapToInt(BookingPersistenceRepository.ServiceSnapshot::durationMinutes)
                .reduce(0, Math::addExact);
        BigDecimal totalAmount = selectedServices.stream().map(BookingPersistenceRepository.ServiceSnapshot::price)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        LocalDateTime end = start.plusMinutes(totalDurationMinutes);
        if (bookings.hasConflictingBooking(staffId, start, end)) {
            throw new IllegalArgumentException("Lịch của nhân viên bị trùng hoặc cách lịch khác dưới 60 phút.");
        }

        Long bookingId = bookings.insertBooking(customer.getUserId(), staff.getStaffId(), house.getHouseId(),
                house.getAddress(), house.getLatitude(), house.getLongitude(), house.getAddressNote(),
                start, end, totalDurationMinutes, totalAmount, cleanAreaNote(areaNote), now());
        bookings.insertDetails(bookingId, selectedServices);
        return bookingId;
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
        if (start.getSecond() != 0 || start.getNano() != 0 || start.getMinute() % 30 != 0) {
            throw new IllegalArgumentException("Giờ bắt đầu phải trùng mốc 30 phút.");
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
