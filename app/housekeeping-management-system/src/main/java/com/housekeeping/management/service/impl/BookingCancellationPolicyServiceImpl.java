package com.housekeeping.management.service.impl;

import com.housekeeping.management.dto.cancellation.CancellationDto.*;
import com.housekeeping.management.entity.*;
import com.housekeeping.management.enums.BookingStatus;
import com.housekeeping.management.enums.PaymentMethod;
import com.housekeeping.management.enums.PaymentStatus;
import com.housekeeping.management.enums.UserStatus;
import com.housekeeping.management.repository.*;
import com.housekeeping.management.service.BookingCancellationPolicyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;

@Service
@RequiredArgsConstructor
@Slf4j
public class BookingCancellationPolicyServiceImpl implements BookingCancellationPolicyService {

    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final BigDecimal MIN_FEE_TIER_2H_6H = BigDecimal.valueOf(30_000);
    private static final BigDecimal MIN_FEE_TIER_UNDER_2H = BigDecimal.valueOf(50_000);

    private final BookingRepository bookingRepository;
    private final UserRepository userRepository;
    private final UserWalletRepository walletRepository;
    private final WalletTransactionRepository walletTxRepository;
    private final CancellationLogRepository cancelLogRepository;
    private final PaymentPersistenceRepository paymentRepository;

    @Override
    @Transactional(readOnly = true)
    public PreviewResponse previewCancellation(Long bookingId, Long customerId) {
        Booking booking = getAndValidateOwnership(bookingId, customerId);
        validateCancellableState(booking);

        LocalDateTime now = LocalDateTime.now(BUSINESS_ZONE);
        FeeCalculation calc = calculateFee(booking, "PREVIEW", now);

        boolean canReschedule = checkCanReschedule(booking, now);
        String rescheduleMsg = canReschedule
                ? "Bạn còn 1 lần dời lịch miễn phí thay vì hủy đơn."
                : "Đơn hàng không đủ điều kiện dời lịch.";

        return new PreviewResponse(
                booking.getBookingId(),
                booking.getTotalAmount(),
                calc.fee(),
                calc.refund(),
                calc.unpaidDebt(),
                calc.minutesUntilStart(),
                calc.notice(),
                canReschedule,
                rescheduleMsg
        );
    }

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public CancelResponse executeCancellation(Long bookingId, Long customerId, CancelRequest request) {
        // 1. Khoá độc quyền với Pessimistic Lock để triệt tiêu Race Condition
        Booking booking = bookingRepository.findByIdWithLock(bookingId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy đơn hàng."));

        if (!booking.getCustomerId().equals(customerId)) {
            throw new AccessDeniedException("Bạn không có quyền hủy đơn này.");
        }

        // 2. Chặn nếu ca làm việc đã bắt đầu hoặc hoàn thành
        validateCancellableState(booking);

        LocalDateTime now = LocalDateTime.now(BUSINESS_ZONE);
        String reasonCode = request != null && request.reasonCode() != null
                ? request.reasonCode().trim().toUpperCase() : "OTHER";
        String reasonNote = request != null ? request.reasonNote() : "";

        FeeCalculation calc = calculateFee(booking, reasonCode, now);

        // 3. Xử lý Dòng tiền (Online vs Tiền mặt COD)
        User customer = userRepository.findById(customerId)
                .orElseThrow(() -> new IllegalStateException("Không tìm thấy thông tin khách hàng."));

        if (calc.isOnlinePaid()) {
            // Thanh toán trước: hoàn phần dư vào ví khách
            if (calc.refund().compareTo(BigDecimal.ZERO) > 0) {
                creditWallet(customer.getUserId(), calc.refund(), "CANCEL_REFUND", booking.getBookingId(),
                        "Hoàn tiền dư sau hủy đơn #" + booking.getBookingId());
            }
        } else {
            // Tiền mặt COD: ghi nhận nợ phạt nếu phát sinh phí
            if (calc.fee().compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal currentDebt = customer.getUnpaidPenaltyDebt() != null
                        ? customer.getUnpaidPenaltyDebt() : BigDecimal.ZERO;
                customer.setUnpaidPenaltyDebt(currentDebt.add(calc.fee()));
            }
        }

        // 4. Chia hoa hồng đền bù 80% cho CTV
        if (booking.getStaffId() != null && calc.workerCompensation().compareTo(BigDecimal.ZERO) > 0) {
            creditWallet(booking.getStaffId(), calc.workerCompensation(), "WORKER_COMPENSATION", booking.getBookingId(),
                    "Đền bù khách hủy đơn #" + booking.getBookingId());
        }

        // 5. Cập nhật trạng thái đơn hàng
        booking.setStatus(BookingStatus.CANCELLED);
        booking.setCancellationFee(calc.fee());
        booking.setRefundAmount(calc.refund());
        booking.setCancelledAt(now);
        booking.setCancelledBy(customerId);
        booking.setCancellationReason(reasonCode + (reasonNote.isBlank() ? "" : ": " + reasonNote));
        bookingRepository.save(booking);

        // 6. Ghi nhật ký Hủy đơn (Audit Trail)
        CancellationLog logEntry = CancellationLog.builder()
                .bookingId(booking.getBookingId())
                .customerId(customerId)
                .workerId(booking.getStaffId())
                .cancelledByType("CUSTOMER")
                .reasonCode(reasonCode)
                .reasonNote(reasonNote)
                .bookingTotalAmount(booking.getTotalAmount())
                .cancellationFee(calc.fee())
                .workerCompensation(calc.workerCompensation())
                .platformFee(calc.platformFee())
                .refundAmount(calc.refund())
                .deltaMinutesToStart(calc.minutesUntilStart())
                .isWorkerFault(calc.isWorkerFault())
                .createdAt(now)
                .build();
        cancelLogRepository.save(logEntry);

        // 7. Quy tắc chống "Bom" đơn (Anti-Fraud / Abuse Control)
        boolean codDisabled = false;
        boolean suspended = false;
        String antiFraudWarning = "";

        if (calc.fee().compareTo(BigDecimal.ZERO) > 0 && !calc.isWorkerFault()) {
            LocalDateTime thirtyDaysAgo = now.minusDays(30);
            long lateCancels = cancelLogRepository.countLateCancellationsWithinDays(customerId, thirtyDaysAgo);

            if (lateCancels >= 3) {
                customer.setStatus(UserStatus.SUSPENDED);
                customer.setSuspendedUntil(now.plusDays(7));
                customer.setAllowCod(false);
                suspended = true;
                codDisabled = true;
                antiFraudWarning = " Tài khoản của bạn bị tạm ngưng 7 ngày do đã hủy đơn sát giờ 3 lần trong 30 ngày.";
                log.warn("Tài khoản user {} bị tạm ngưng 7 ngày do hủy đơn quá 3 lần.", customerId);
            } else if (lateCancels == 2) {
                customer.setAllowCod(false);
                codDisabled = true;
                antiFraudWarning = " Phương thức tiền mặt (COD) đã bị khóa do bạn hủy đơn sát giờ lần thứ 2. Vui lòng thanh toán trực tuyến cho các đơn sau.";
                log.warn("Tài khoản user {} bị tắt quyền COD do hủy đơn trễ lần 2.", customerId);
            } else if (lateCancels == 1) {
                antiFraudWarning = " Cảnh báo: Việc hủy đơn sát giờ làm có thể ảnh hưởng đến quyền thanh toán tiền mặt cho các lần đặt tiếp theo.";
            }
        }
        userRepository.save(customer);

        return new CancelResponse(
                booking.getBookingId(),
                BookingStatus.CANCELLED.name(),
                calc.fee(),
                calc.refund(),
                customer.getUnpaidPenaltyDebt(),
                codDisabled,
                suspended,
                "Hủy đơn thành công." + antiFraudWarning
        );
    }

    // ====================== CORE CALCULATION ENGINE ======================
    private FeeCalculation calculateFee(Booking booking, String reasonCode, LocalDateTime now) {
        LocalDateTime scheduledStart = booking.getStartTime();
        Duration delta = Duration.between(now, scheduledStart);
        long deltaMinutes = delta.toMinutes();
        BigDecimal total = booking.getTotalAmount() != null ? booking.getTotalAmount() : BigDecimal.ZERO;

        boolean isOnlinePaid = isBookingPaidOnline(booking.getBookingId());

        // Edge case 1: Đơn chưa có CTV nhận hoặc đang PENDING/AWAITING_PAYMENT -> Miễn phí hủy 100%
        if (booking.getStaffId() == null || booking.getStatus() == BookingStatus.AWAITING_PAYMENT) {
            return new FeeCalculation(BigDecimal.ZERO, total, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                    deltaMinutes, false, isOnlinePaid, "Đơn chưa có nhân viên nhận ca. Miễn phí hủy 100%.");
        }

        // Edge case 2: Lỗi do CTV đến trễ quá 15 phút chưa check-in
        if ("WORKER_LATE".equalsIgnoreCase(reasonCode)) {
            boolean workerArrived = booking.getStaffArrivedAt() != null
                    || booking.getCustomerArrivedAt() != null
                    || booking.getArrivalVerifiedAt() != null;
            if (now.isAfter(scheduledStart.plusMinutes(15)) && !workerArrived && booking.getWorkStartedAt() == null) {
                return new FeeCalculation(BigDecimal.ZERO, total, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                        deltaMinutes, true, isOnlinePaid, "Hủy miễn phí do nhân viên đến trễ quá 15 phút.");
            }
        }

        // 1. Grace Period: Trong vòng 10 phút kể từ lúc tạo đơn VÀ còn cách giờ làm > 1 tiếng
        Duration sinceCreated = Duration.between(booking.getCreatedAt() != null ? booking.getCreatedAt() : now, now);
        if (sinceCreated.toMinutes() <= 10 && deltaMinutes > 60) {
            return new FeeCalculation(BigDecimal.ZERO, total, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                    deltaMinutes, false, isOnlinePaid, "Hủy trong khoảng thời gian ân hạn 10 phút. Miễn phí 100%.");
        }

        BigDecimal fee;
        String notice;

        // 2. Trước giờ làm > 6 tiếng (delta_t > 360 phút)
        if (deltaMinutes > 360) {
            fee = BigDecimal.ZERO;
            notice = "Hủy trước giờ làm hơn 6 tiếng: Miễn phí 100%.";
        }
        // 3. Từ 2 đến 6 tiếng trước giờ làm (120 <= delta_t <= 360 phút)
        else if (deltaMinutes >= 120) {
            fee = total.multiply(BigDecimal.valueOf(0.25)).max(MIN_FEE_TIER_2H_6H);
            fee = fee.min(total);
            notice = "Hủy từ 2h - 6h trước giờ làm: Phí 25% (tối thiểu 30.000đ).";
        }
        // 4. Dưới 2 tiếng trước giờ làm (0 < delta_t < 120 phút)
        else if (deltaMinutes > 0 && booking.getStaffArrivedAt() == null && booking.getCustomerArrivedAt() == null) {
            fee = total.multiply(BigDecimal.valueOf(0.50)).max(MIN_FEE_TIER_UNDER_2H);
            fee = fee.min(total);
            notice = "Hủy dưới 2h trước giờ làm: Phí 50% (tối thiểu 50.000đ).";
        }
        // 5. Đã đến giờ làm hoặc CTV đã check-in
        else {
            fee = total;
            notice = "Đã đến giờ làm hoặc nhân viên đã có mặt: Phí hủy 100%.";
        }

        fee = fee.setScale(0, RoundingMode.HALF_UP);
        BigDecimal refund = isOnlinePaid ? total.subtract(fee).max(BigDecimal.ZERO) : BigDecimal.ZERO;
        BigDecimal unpaidDebt = !isOnlinePaid ? fee : BigDecimal.ZERO;

        // Phân chia doanh thu từ phí hủy: 80% CTV, 20% hệ thống
        BigDecimal workerCompensation = fee.multiply(BigDecimal.valueOf(0.80)).setScale(0, RoundingMode.HALF_UP);
        BigDecimal platformFee = fee.subtract(workerCompensation);

        return new FeeCalculation(fee, refund, unpaidDebt, workerCompensation, platformFee,
                deltaMinutes, false, isOnlinePaid, notice);
    }

    private boolean isBookingPaidOnline(Long bookingId) {
        return paymentRepository.findByBookingIdOrderByCreatedAtDescPaymentIdDesc(bookingId)
                .stream()
                .findFirst()
                .map(p -> p.getPaymentMethod() == PaymentMethod.ONLINE && p.getStatus() == PaymentStatus.SUCCESS)
                .orElse(false);
    }

    private void creditWallet(Long userId, BigDecimal amount, String type, Long refId, String note) {
        UserWallet wallet = walletRepository.findByUserIdForUpdate(userId)
                .orElseGet(() -> walletRepository.save(new UserWallet(userId, BigDecimal.ZERO)));

        BigDecimal before = wallet.getBalance() != null ? wallet.getBalance() : BigDecimal.ZERO;
        BigDecimal after = before.add(amount);
        wallet.setBalance(after);
        wallet.setUpdatedAt(LocalDateTime.now(BUSINESS_ZONE));
        walletRepository.save(wallet);

        WalletTransaction tx = WalletTransaction.builder()
                .walletId(wallet.getWalletId())
                .userId(userId)
                .amount(amount)
                .balanceBefore(before)
                .balanceAfter(after)
                .type(type)
                .referenceId(refId)
                .note(note)
                .createdAt(LocalDateTime.now(BUSINESS_ZONE))
                .build();
        walletTxRepository.save(tx);
    }

    private boolean checkCanReschedule(Booking booking, LocalDateTime now) {
        Duration delta = Duration.between(now, booking.getStartTime());
        int rescheduleCount = booking.getRescheduleCount() != null ? booking.getRescheduleCount() : 0;
        return delta.toMinutes() >= 120
                && rescheduleCount == 0
                && booking.getStaffId() != null
                && booking.getWorkStartedAt() == null
                && booking.getStatus() != BookingStatus.COMPLETED
                && booking.getStatus() != BookingStatus.CANCELLED;
    }

    private Booking getAndValidateOwnership(Long bookingId, Long customerId) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy đơn hàng."));
        if (!booking.getCustomerId().equals(customerId)) {
            throw new AccessDeniedException("Bạn không có quyền thao tác trên đơn này.");
        }
        return booking;
    }

    private void validateCancellableState(Booking booking) {
        if (booking.getWorkStartedAt() != null) {
            throw new IllegalStateException("Công việc đã bắt đầu nên không thể hủy lịch.");
        }
        if (booking.getStatus() == BookingStatus.COMPLETED) {
            throw new IllegalStateException("Đơn hàng đã hoàn thành, không thể hủy.");
        }
        if (booking.getStatus() == BookingStatus.CANCELLED) {
            throw new IllegalStateException("Đơn hàng này đã được hủy trước đó.");
        }
    }

    private record FeeCalculation(
            BigDecimal fee,
            BigDecimal refund,
            BigDecimal unpaidDebt,
            BigDecimal workerCompensation,
            BigDecimal platformFee,
            long minutesUntilStart,
            boolean isWorkerFault,
            boolean isOnlinePaid,
            String notice
    ) {}
}
