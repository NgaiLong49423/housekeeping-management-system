package com.housekeeping.management.service;

import com.housekeeping.management.dto.cancellation.CancellationDto.*;
import com.housekeeping.management.entity.*;
import com.housekeeping.management.enums.BookingStatus;
import com.housekeeping.management.enums.PaymentMethod;
import com.housekeeping.management.enums.PaymentStatus;
import com.housekeeping.management.enums.UserStatus;
import com.housekeeping.management.repository.*;
import com.housekeeping.management.service.impl.BookingCancellationPolicyServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BookingCancellationPolicyServiceTest {

    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    @Mock
    private BookingRepository bookingRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private UserWalletRepository walletRepository;
    @Mock
    private WalletTransactionRepository walletTxRepository;
    @Mock
    private CancellationLogRepository cancelLogRepository;
    @Mock
    private PaymentPersistenceRepository paymentRepository;

    @InjectMocks
    private BookingCancellationPolicyServiceImpl service;

    private User customer;
    private Booking booking;

    @BeforeEach
    void setUp() {
        customer = new User();
        customer.setUserId(1L);
        customer.setFullName("Nguyen Van A");
        customer.setEmail("customer@test.com");
        customer.setStatus(UserStatus.ACTIVE);
        customer.setUnpaidPenaltyDebt(BigDecimal.ZERO);
        customer.setAllowCod(true);

        booking = new Booking();
        booking.setBookingId(100L);
        booking.setCustomerId(1L);
        booking.setStaffId(5L);
        booking.setStatus(BookingStatus.CONFIRMED);
        booking.setTotalAmount(BigDecimal.valueOf(200_000));
        booking.setCreatedAt(LocalDateTime.now(BUSINESS_ZONE).minusHours(2));
        booking.setStartTime(LocalDateTime.now(BUSINESS_ZONE).plusHours(7)); // > 6h by default
    }

    @Test
    void previewMoreThan6HoursBefore_isFree() {
        when(bookingRepository.findById(100L)).thenReturn(Optional.of(booking));
        when(paymentRepository.findByBookingIdOrderByCreatedAtDescPaymentIdDesc(100L)).thenReturn(List.of());

        PreviewResponse preview = service.previewCancellation(100L, 1L);

        assertThat(preview.cancellationFee()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(preview.canReschedule()).isTrue();
    }

    @Test
    void cancelBetween2And6Hours_charges25Percent() {
        booking.setStartTime(LocalDateTime.now(BUSINESS_ZONE).plusHours(4));
        when(bookingRepository.findByIdWithLock(100L)).thenReturn(Optional.of(booking));
        when(userRepository.findById(1L)).thenReturn(Optional.of(customer));
        when(paymentRepository.findByBookingIdOrderByCreatedAtDescPaymentIdDesc(100L)).thenReturn(List.of());

        UserWallet workerWallet = new UserWallet(5L, BigDecimal.ZERO);
        when(walletRepository.findByUserIdForUpdate(5L)).thenReturn(Optional.of(workerWallet));

        CancelResponse response = service.executeCancellation(100L, 1L, new CancelRequest("PERSONAL", "Busy"));

        // 200.000 * 25% = 50.000
        assertThat(response.cancellationFee()).isEqualByComparingTo(BigDecimal.valueOf(50_000));
        // COD mode -> debt added to customer
        assertThat(response.unpaidDebt()).isEqualByComparingTo(BigDecimal.valueOf(50_000));
        assertThat(customer.getUnpaidPenaltyDebt()).isEqualByComparingTo(BigDecimal.valueOf(50_000));

        // CTV compensated 80% of 50.000 = 40.000
        assertThat(workerWallet.getBalance()).isEqualByComparingTo(BigDecimal.valueOf(40_000));
        verify(cancelLogRepository).save(any(CancellationLog.class));
    }

    @Test
    void cancelUnder2Hours_charges50Percent_andRefundsOnline() {
        booking.setStartTime(LocalDateTime.now(BUSINESS_ZONE).plusMinutes(90));
        when(bookingRepository.findByIdWithLock(100L)).thenReturn(Optional.of(booking));
        when(userRepository.findById(1L)).thenReturn(Optional.of(customer));

        Payment paidOnline = new Payment();
        paidOnline.setPaymentMethod(PaymentMethod.ONLINE);
        paidOnline.setStatus(PaymentStatus.SUCCESS);
        when(paymentRepository.findByBookingIdOrderByCreatedAtDescPaymentIdDesc(100L)).thenReturn(List.of(paidOnline));

        UserWallet custWallet = new UserWallet(1L, BigDecimal.ZERO);
        when(walletRepository.findByUserIdForUpdate(1L)).thenReturn(Optional.of(custWallet));
        UserWallet workerWallet = new UserWallet(5L, BigDecimal.ZERO);
        when(walletRepository.findByUserIdForUpdate(5L)).thenReturn(Optional.of(workerWallet));

        CancelResponse response = service.executeCancellation(100L, 1L, new CancelRequest("PERSONAL", "Cannot make it"));

        // 200.000 * 50% = 100.000
        assertThat(response.cancellationFee()).isEqualByComparingTo(BigDecimal.valueOf(100_000));
        // Remaining 100.000 refunded to customer wallet
        assertThat(response.refundAmount()).isEqualByComparingTo(BigDecimal.valueOf(100_000));
        assertThat(custWallet.getBalance()).isEqualByComparingTo(BigDecimal.valueOf(100_000));
        // Worker gets 80% = 80.000
        assertThat(workerWallet.getBalance()).isEqualByComparingTo(BigDecimal.valueOf(80_000));
    }

    @Test
    void workerLateMoreThan15Minutes_isFreeCancellation() {
        booking.setStartTime(LocalDateTime.now(BUSINESS_ZONE).minusMinutes(20)); // CTV is 20m late
        when(bookingRepository.findByIdWithLock(100L)).thenReturn(Optional.of(booking));
        when(userRepository.findById(1L)).thenReturn(Optional.of(customer));
        when(paymentRepository.findByBookingIdOrderByCreatedAtDescPaymentIdDesc(100L)).thenReturn(List.of());

        CancelResponse response = service.executeCancellation(100L, 1L, new CancelRequest("WORKER_LATE", "Staff not arrived"));

        assertThat(response.cancellationFee()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(customer.getUnpaidPenaltyDebt()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void antiFraud_disablesCodOnSecondLateCancel() {
        booking.setStartTime(LocalDateTime.now(BUSINESS_ZONE).plusHours(3));
        when(bookingRepository.findByIdWithLock(100L)).thenReturn(Optional.of(booking));
        when(userRepository.findById(1L)).thenReturn(Optional.of(customer));
        when(paymentRepository.findByBookingIdOrderByCreatedAtDescPaymentIdDesc(100L)).thenReturn(List.of());
        when(walletRepository.findByUserIdForUpdate(5L)).thenReturn(Optional.of(new UserWallet(5L, BigDecimal.ZERO)));

        // 2nd late cancel in 30 days
        when(cancelLogRepository.countLateCancellationsWithinDays(eq(1L), any())).thenReturn(2L);

        CancelResponse response = service.executeCancellation(100L, 1L, new CancelRequest("PERSONAL", "Cancel again"));

        assertThat(response.codDisabled()).isTrue();
        assertThat(customer.getAllowCod()).isFalse();
        assertThat(customer.getStatus()).isEqualTo(UserStatus.ACTIVE);
    }

    @Test
    void antiFraud_suspendsAccountOnThirdLateCancel() {
        booking.setStartTime(LocalDateTime.now(BUSINESS_ZONE).plusHours(3));
        when(bookingRepository.findByIdWithLock(100L)).thenReturn(Optional.of(booking));
        when(userRepository.findById(1L)).thenReturn(Optional.of(customer));
        when(paymentRepository.findByBookingIdOrderByCreatedAtDescPaymentIdDesc(100L)).thenReturn(List.of());
        when(walletRepository.findByUserIdForUpdate(5L)).thenReturn(Optional.of(new UserWallet(5L, BigDecimal.ZERO)));

        // 3rd late cancel in 30 days
        when(cancelLogRepository.countLateCancellationsWithinDays(eq(1L), any())).thenReturn(3L);

        CancelResponse response = service.executeCancellation(100L, 1L, new CancelRequest("PERSONAL", "Third cancel"));

        assertThat(response.accountSuspended()).isTrue();
        assertThat(customer.getStatus()).isEqualTo(UserStatus.SUSPENDED);
        assertThat(customer.getSuspendedUntil()).isNotNull();
    }

    @Test
    void rejectsCancellationWhenWorkHasStarted() {
        booking.setWorkStartedAt(LocalDateTime.now(BUSINESS_ZONE).minusMinutes(10));
        when(bookingRepository.findByIdWithLock(100L)).thenReturn(Optional.of(booking));

        assertThatThrownBy(() -> service.executeCancellation(100L, 1L, new CancelRequest("PERSONAL", "")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Công việc đã bắt đầu");
    }

    @Test
    void rejectsWhenNotOwner() {
        when(bookingRepository.findByIdWithLock(100L)).thenReturn(Optional.of(booking));

        assertThatThrownBy(() -> service.executeCancellation(100L, 999L, new CancelRequest("PERSONAL", "")))
                .isInstanceOf(AccessDeniedException.class);
    }
}
