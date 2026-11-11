package com.housekeeping.management.service.impl;

import com.housekeeping.management.service.PaymentService;
import com.housekeeping.management.service.BookingReservationService;

import com.housekeeping.management.repository.PaymentPersistenceRepository;
import com.housekeeping.management.entity.Payment;
import com.housekeeping.management.domain.PaymentMethod;
import com.housekeeping.management.domain.PaymentStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PaymentServiceImpl implements PaymentService {
    private static final ZoneOffset BUSINESS_OFFSET = ZoneOffset.ofHours(7);
    private static final java.time.format.DateTimeFormatter ORDER_CODE_TIME =
            java.time.format.DateTimeFormatter.ofPattern("yyMMddHHmmss");
    private final BookingReservationService bookings;
    private final PaymentPersistenceRepository payments;

    public PaymentServiceImpl(BookingReservationService bookings, PaymentPersistenceRepository payments) {
        this.bookings = bookings;
        this.payments = payments;
    }

    @Transactional
    public OnlineCheckout prepareOnlineCheckout(Long bookingId, Long customerId) {
        var booking = bookings.lockBookingForPayment(bookingId)
                .orElseThrow(() -> new IllegalArgumentException("Lịch hẹn không tồn tại."));
        if (!booking.customerId().equals(customerId)) {
            throw new AccessDeniedException("Lịch hẹn không thuộc tài khoản của bạn.");
        }
        if (!"AWAITING_PAYMENT".equals(booking.status())) {
            throw new IllegalStateException("Chỉ có thể thanh toán lịch đang chờ thanh toán.");
        }

        List<Payment> attempts = payments.findByBookingIdOrderByCreatedAtDescPaymentIdDesc(bookingId);
        if (attempts.stream().anyMatch(payment -> payment.getStatus() == PaymentStatus.SUCCESS)) {
            throw new IllegalStateException("Lịch hẹn đã được thanh toán.");
        }
        if (!attempts.isEmpty()) {
            var latest = attempts.getFirst();
            if (latest.getPaymentMethod() == PaymentMethod.ONLINE && latest.getStatus() == PaymentStatus.PENDING) {
                return new OnlineCheckout(Long.parseLong(latest.getTransactionCode()), onlineAmount(latest.getAmount()),
                        latest.getCreatedAt(), true);
            }
        }

        BigDecimal amount = bookings.amountForPayment(bookingId);
        long onlineAmount = onlineAmount(amount);
        LocalDateTime createdAt = now();
        Payment pending = new Payment();
        pending.setBookingId(bookingId);
        pending.setAmount(amount);
        pending.setPaymentMethod(PaymentMethod.ONLINE);
        pending.setStatus(PaymentStatus.PENDING);
        pending.setCreatedAt(createdAt);
        pending.setSimulated(true);
        pending = payments.saveAndFlush(pending);
        long orderCode = onlineOrderCode(createdAt, pending.getPaymentId());
        pending.setTransactionCode(Long.toString(orderCode));
        payments.save(pending);
        return new OnlineCheckout(orderCode, onlineAmount, createdAt, false);
    }

    /**
     * yyMMddHHmmss of the attempt followed by the last five sequence digits. The gateway terminal is shared by
     * every team database and rejects an order code reused on the same day, while each database's sequence
     * restarts after a local reset; the creation time keeps codes apart across databases and resets.
     */
    private static long onlineOrderCode(LocalDateTime createdAt, long paymentId) {
        if (paymentId < 1) throw new IllegalStateException("Không cấp được mã đơn thanh toán trực tuyến hợp lệ.");
        return Long.parseLong(ORDER_CODE_TIME.format(createdAt) + String.format(java.util.Locale.ROOT, "%05d", paymentId % 100_000));
    }

    public Optional<OnlinePayment> findOnlinePayment(long orderCode) {
        return payments.findByTransactionCode(Long.toString(orderCode)).filter(payment -> payment.getPaymentMethod() == PaymentMethod.ONLINE)
                .map(PaymentServiceImpl::onlinePayment);
    }

    @Transactional
    public void confirmCashReceived(Long bookingId, Long assignedStaffId) {
        var booking = bookings.lockBookingForPayment(bookingId)
                .orElseThrow(() -> new IllegalArgumentException("Lịch hẹn không tồn tại."));
        if (!booking.staffId().equals(assignedStaffId)) {
            throw new AccessDeniedException("Chỉ nhân viên được phân công mới xác nhận tiền mặt.");
        }
        if (!"AWAITING_PAYMENT".equals(booking.status())) {
            throw new IllegalStateException("Lịch hẹn chưa ở trạng thái chờ thanh toán.");
        }
        List<Payment> attempts = payments.findByBookingIdOrderByCreatedAtDescPaymentIdDesc(bookingId);
        if (attempts.stream().anyMatch(payment -> payment.getStatus() == PaymentStatus.SUCCESS)) {
            throw new IllegalStateException("Lịch hẹn đã có thanh toán thành công.");
        }
        payments.failPendingOnlineAttempts(bookingId);
        Payment cashPayment = new Payment();
        cashPayment.setBookingId(bookingId);
        cashPayment.setAmount(bookings.amountForPayment(bookingId));
        cashPayment.setPaymentMethod(PaymentMethod.CASH);
        cashPayment.setStatus(PaymentStatus.SUCCESS);
        cashPayment.setPaidAt(now());
        cashPayment.setCreatedAt(cashPayment.getPaidAt());
        payments.save(cashPayment);
        if (bookings.completeBookingAfterPayment(bookingId) != 1) {
            throw new IllegalStateException("Trạng thái lịch hẹn vừa thay đổi; giao dịch đã được hoàn tác.");
        }
    }

    @Transactional
    public void confirmOnlinePayment(long orderCode, BigDecimal amount) {
        OnlinePayment payment = lockOnlinePayment(orderCode, amount);
        if (PaymentStatus.SUCCESS.name().equals(payment.status())) return;
        var booking = bookings.lockBookingForPayment(payment.bookingId())
                .orElseThrow(() -> new IllegalArgumentException("Lịch hẹn thanh toán không tồn tại."));
        if (payments.existsByBookingIdAndStatus(payment.bookingId(), PaymentStatus.SUCCESS)) {
            payments.markFailed(payment.paymentId());
            return;
        }
        if (!"AWAITING_PAYMENT".equals(booking.status())) {
            throw new IllegalStateException("Lịch hẹn không còn chờ thanh toán trực tuyến.");
        }
        if (payments.markSuccess(payment.paymentId(), now()) != 1 || bookings.completeBookingAfterPayment(payment.bookingId()) != 1) {
            throw new IllegalStateException("Không thể xác nhận giao dịch; trạng thái thanh toán đã thay đổi.");
        }
    }

    @Transactional
    public void failOnlinePayment(long orderCode, BigDecimal amount) {
        OnlinePayment payment = lockOnlinePayment(orderCode, amount);
        if (PaymentStatus.SUCCESS.name().equals(payment.status())) return;
        payments.markFailed(payment.paymentId());
    }

    private OnlinePayment lockOnlinePayment(long orderCode, BigDecimal amount) {
        Long bookingId = payments.findByTransactionCode(Long.toString(orderCode))
                .map(Payment::getBookingId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy giao dịch thanh toán trực tuyến."));
        bookings.lockBookingForPayment(bookingId)
                .orElseThrow(() -> new IllegalArgumentException("Lịch hẹn thanh toán không tồn tại."));
        Payment payment = payments.findByTransactionCodeForUpdate(Long.toString(orderCode))
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy giao dịch thanh toán trực tuyến."));
        if (payment.getPaymentMethod() != PaymentMethod.ONLINE || payment.getAmount().compareTo(amount) != 0) {
            throw new IllegalArgumentException("Thông tin giao dịch trực tuyến không khớp với yêu cầu đã lưu.");
        }
        return onlinePayment(payment);
    }

    private static long onlineAmount(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalStateException("Số tiền thanh toán trực tuyến phải lớn hơn 0.");
        }
        try {
            return amount.longValueExact();
        } catch (ArithmeticException invalidVndAmount) {
            throw new IllegalStateException("Số tiền thanh toán phải là số nguyên VND.", invalidVndAmount);
        }
    }

    private static LocalDateTime now() {
        return OffsetDateTime.now(BUSINESS_OFFSET).withNano(0).toLocalDateTime();
    }

    @Override
    @Transactional(readOnly = true)
    public PaymentService.AccountingTotals accountingTotals() {
        return new PaymentService.AccountingTotals(payments.sumSuccessfulRealPayments(),
                payments.sumSuccessfulSimulatedPayments());
    }

    @Override
    @Transactional(readOnly = true)
    public List<PaymentService.AccountingPayment> successfulPaymentsForAccounting() {
        return payments.findAllByStatusOrderByPaidAtDescPaymentIdDesc(PaymentStatus.SUCCESS).stream()
                .map(payment -> new PaymentService.AccountingPayment(payment.getPaymentId(), payment.getBookingId(),
                        payment.getAmount(), payment.getPaymentMethod().name(), payment.getTransactionCode(),
                        payment.getPaidAt(), payment.isSimulated()))
                .toList();
    }

    private static OnlinePayment onlinePayment(Payment payment) {
        return new OnlinePayment(payment.getPaymentId(), payment.getBookingId(), payment.getAmount(),
                payment.getPaymentMethod().name(), payment.getStatus().name(), payment.getTransactionCode(), payment.getCreatedAt());
    }


}
