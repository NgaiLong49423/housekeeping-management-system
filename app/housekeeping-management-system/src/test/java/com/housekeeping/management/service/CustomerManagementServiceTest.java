package com.housekeeping.management.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.housekeeping.management.domain.UserRole;
import com.housekeeping.management.domain.UserStatus;
import com.housekeeping.management.entity.User;
import com.housekeeping.management.repository.CustomerAccountRepository;
import com.housekeeping.management.service.BookingQueryService.CustomerBookingCounts;
import com.housekeeping.management.service.impl.CustomerManagementServiceImpl;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

class CustomerManagementServiceTest {
    private final AccountAuthorizationService authorization = mock(AccountAuthorizationService.class);
    private final CustomerAccountRepository customers = mock(CustomerAccountRepository.class);
    private final BookingQueryService bookings = mock(BookingQueryService.class);
    private final HouseService houses = mock(HouseService.class);
    private final CustomerManagementService service =
            new CustomerManagementServiceImpl(authorization, customers, bookings, houses);

    @Test
    void lockRequiresAReasonBeforeTouchingTheAccount() {
        assertThatThrownBy(() -> service.lock(3L, 31L, null)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("lý do");
        assertThatThrownBy(() -> service.lock(3L, 31L, "   ")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.lock(3L, 31L, "abc")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.lock(3L, 31L, "x".repeat(501))).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(customers, bookings);
    }

    @Test
    void lockStoresTheNormalizedReasonActorAndTime() {
        User customer = customer(UserStatus.ACTIVE);
        when(customers.findCustomerForUpdate(31L)).thenReturn(Optional.of(customer));

        service.lock(3L, 31L, "  Nhiều lần   hủy lịch\n sát giờ  ");

        assertThat(customer.getStatus()).isEqualTo(UserStatus.INACTIVE);
        assertThat(customer.getStatusReason()).isEqualTo("Nhiều lần hủy lịch sát giờ");
        assertThat(customer.getStatusChangedBy()).isEqualTo(3L);
        assertThat(customer.getStatusChangedAt()).isNotNull();
        verify(authorization).requireActiveAdmin(3L);
    }

    @Test
    void customerWithUnfinishedBookingsCannotBeLocked() {
        User customer = customer(UserStatus.ACTIVE);
        when(customers.findCustomerForUpdate(31L)).thenReturn(Optional.of(customer));
        when(bookings.hasUnfinishedBookingForCustomer(31L)).thenReturn(true);

        assertThatThrownBy(() -> service.lock(3L, 31L, "Nhiều lần hủy lịch sát giờ"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("chờ thanh toán");
        assertThat(customer.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(customer.getStatusReason()).isNull();
    }

    @Test
    void unlockClearsTheReasonAndRecordsWhoUnlocked() {
        User customer = customer(UserStatus.ACTIVE);
        customer.lock("Nhiều lần hủy lịch sát giờ", 3L, java.time.LocalDateTime.of(2026, 11, 1, 8, 0));
        when(customers.findCustomerForUpdate(31L)).thenReturn(Optional.of(customer));

        service.unlock(5L, 31L);

        assertThat(customer.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(customer.getStatusReason()).isNull();
        assertThat(customer.getStatusChangedBy()).isEqualTo(5L);
    }

    @Test
    void onlyActiveAdminsOrManagersCanManageCustomers() {
        doThrow(new AccessDeniedException("admin required")).when(authorization).requireActiveAdmin(31L);

        assertThatThrownBy(() -> service.findCustomers(31L, null)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.lock(31L, 32L, "Nhiều lần hủy lịch sát giờ")).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.unlock(31L, 32L)).isInstanceOf(AccessDeniedException.class);
        verify(customers, never()).findCustomerForUpdate(any());
    }

    @Test
    void nonCustomerAccountsAreNotFound() {
        when(customers.findCustomerForUpdate(7L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.lock(3L, 7L, "Nhiều lần hủy lịch sát giờ"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Customer");
    }

    @Test
    void listShowsBookingAndAddressCountsAndEscapesSearchWildcards() {
        User active = customer(UserStatus.ACTIVE);
        User locked = customer(UserStatus.ACTIVE);
        locked.setUserId(32L);
        locked.lock("Nhiều lần hủy lịch sát giờ", 3L, java.time.LocalDateTime.of(2026, 11, 1, 8, 0));
        when(customers.search("%50[%] off%")).thenReturn(List.of(active, locked));
        when(bookings.bookingCountsByCustomer()).thenReturn(Map.of(31L, new CustomerBookingCounts(4, 1)));
        when(houses.activeHouseCountsByCustomer()).thenReturn(Map.of(31L, 2L));

        var cards = service.findCustomers(3L, "  50% OFF ");

        assertThat(cards).hasSize(2);
        assertThat(cards.get(0).active()).isTrue();
        assertThat(cards.get(0).bookingCount()).isEqualTo(4);
        assertThat(cards.get(0).unfinishedBookingCount()).isEqualTo(1);
        assertThat(cards.get(0).houseCount()).isEqualTo(2);
        assertThat(cards.get(1).active()).isFalse();
        assertThat(cards.get(1).statusReason()).isEqualTo("Nhiều lần hủy lịch sát giờ");
        assertThat(cards.get(1).bookingCount()).isZero();
    }

    private User customer(UserStatus status) {
        User user = new User();
        user.setUserId(31L);
        user.setFullName("Đỗ Ngọc Hân");
        user.setEmail("customer2@example.test");
        user.setPhone("0938502002");
        user.setRole(UserRole.CUSTOMER);
        user.setStatus(status);
        return user;
    }
}
