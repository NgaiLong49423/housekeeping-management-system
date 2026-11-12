package com.housekeeping.management.service.impl;

import com.housekeeping.management.dto.AccountLockRules;
import com.housekeeping.management.enums.UserStatus;
import com.housekeeping.management.dto.response.PortalViews.CustomerAccountCard;
import com.housekeeping.management.entity.User;
import com.housekeeping.management.repository.CustomerAccountRepository;
import com.housekeeping.management.service.AccountAuthorizationService;
import com.housekeeping.management.service.BookingQueryService;
import com.housekeeping.management.service.BookingQueryService.CustomerBookingCounts;
import com.housekeeping.management.service.CustomerManagementService;
import com.housekeeping.management.service.HouseService;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CustomerManagementServiceImpl implements CustomerManagementService {
    private static final int MAX_KEYWORD_LENGTH = 100;
    private static final CustomerBookingCounts NO_BOOKINGS = new CustomerBookingCounts(0, 0);

    private final AccountAuthorizationService authorization;
    private final CustomerAccountRepository customers;
    private final BookingQueryService bookings;
    private final HouseService houses;

    public CustomerManagementServiceImpl(AccountAuthorizationService authorization, CustomerAccountRepository customers,
            BookingQueryService bookings, HouseService houses) {
        this.authorization = authorization;
        this.customers = customers;
        this.bookings = bookings;
        this.houses = houses;
    }

    @Override
    @Transactional(readOnly = true)
    public List<CustomerAccountCard> findCustomers(Long adminId, String keyword) {
        authorization.requireActiveAdmin(adminId);
        Map<Long, CustomerBookingCounts> bookingCounts = bookings.bookingCountsByCustomer();
        Map<Long, Long> houseCounts = houses.activeHouseCountsByCustomer();
        return customers.search(likePattern(keyword)).stream().map(customer -> {
            CustomerBookingCounts counts = bookingCounts.getOrDefault(customer.getUserId(), NO_BOOKINGS);
            return new CustomerAccountCard(customer.getUserId(), customer.getFullName(), customer.getEmail(),
                    customer.getPhone(), customer.getStatus() == UserStatus.ACTIVE, customer.getStatusReason(),
                    customer.getStatusChangedAt(), houseCounts.getOrDefault(customer.getUserId(), 0L),
                    counts.total(), counts.unfinished());
        }).toList();
    }

    @Override
    @Transactional
    public void lock(Long adminId, Long customerId, String reason) {
        authorization.requireActiveAdmin(adminId);
        String lockReason = AccountLockRules.requireReason(reason);
        // Khóa dòng Customer trước khi kiểm tra Booking: luồng đặt lịch cũng khóa dòng này,
        // nên không thể có Booking mới chen vào giữa lúc kiểm tra và lúc khóa.
        User customer = findCustomerForUpdate(customerId);
        if (customer.getStatus() != UserStatus.ACTIVE) {
            throw new IllegalStateException("Tài khoản Customer này đã bị khóa.");
        }
        if (bookings.hasUnfinishedBookingForCustomer(customerId)) {
            throw new IllegalStateException("Không thể khóa khi Customer còn Booking đã xác nhận hoặc đang chờ thanh toán. "
                    + "Hãy hoàn tất hoặc hủy các Booking đó trước.");
        }
        customer.lock(lockReason, adminId, AccountLockRules.now());
    }

    @Override
    @Transactional
    public void unlock(Long adminId, Long customerId) {
        authorization.requireActiveAdmin(adminId);
        User customer = findCustomerForUpdate(customerId);
        if (customer.getStatus() == UserStatus.ACTIVE) {
            throw new IllegalStateException("Tài khoản Customer này đang hoạt động.");
        }
        customer.unlock(adminId, AccountLockRules.now());
    }

    private User findCustomerForUpdate(Long customerId) {
        return customers.findCustomerForUpdate(customerId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy tài khoản Customer."));
    }

    private static String likePattern(String keyword) {
        if (keyword == null || keyword.isBlank()) return null;
        String trimmed = keyword.strip().toLowerCase(Locale.ROOT);
        if (trimmed.length() > MAX_KEYWORD_LENGTH) trimmed = trimmed.substring(0, MAX_KEYWORD_LENGTH);
        String escaped = trimmed.replace("[", "[[]").replace("%", "[%]").replace("_", "[_]");
        return "%" + escaped + "%";
    }
}
