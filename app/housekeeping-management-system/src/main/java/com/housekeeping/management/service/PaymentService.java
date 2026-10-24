package com.housekeeping.management.service;

import com.housekeeping.management.repository.BookingPersistenceRepository;
import com.housekeeping.management.repository.PaymentPersistenceRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PaymentService {
    private static final ZoneOffset BUSINESS_OFFSET = ZoneOffset.ofHours(7);
    private final BookingPersistenceRepository bookings;
    private final PaymentPersistenceRepository payments;

    public PaymentService(BookingPersistenceRepository bookings, PaymentPersistenceRepository payments) {
        this.bookings = bookings;
        this.payments = payments;
    }

    @Transactional
    public OnlineCheckout prepareOnlineCheckout(Long bookingId, Long customerId) {
        var booking = bookings.lockBooking(bookingId)
                .orElseThrow(() -> new IllegalArgumentException("Lịch hẹn không tồn tại."));
        if (!booking.customerId().equals(customerId)) {
            throw new AccessDeniedException("Lịch hẹn không thuộc tài khoản của bạn.");
        }
        if (!"AWAITING_PAYMENT".equals(booking.status())) {
            throw new IllegalStateException("Chỉ có thể thanh toán lịch đang chờ thanh toán.");
        }

        List<PaymentPersistenceRepository.PaymentState> attempts = payments.findForBookingUpdate(bookingId);
        if (attempts.stream().anyMatch(payment -> "SUCCESS".equals(payment.status()))) {
            throw new IllegalStateException("Lịch hẹn đã được thanh toán.");
        }
        if (!attempts.isEmpty()) {
            var latest = attempts.getFirst();
            if ("ONLINE".equals(latest.method()) && "PENDING".equals(latest.status())) {
                return new OnlineCheckout(Long.parseLong(latest.transactionCode()), onlineAmount(latest.amount()), true);
            }
        }

        BigDecimal amount = bookings.bookingAmount(bookingId);
        long onlineAmount = onlineAmount(amount);
        Long orderCode = payments.nextPayosOrderCode();
        if (orderCode == null || orderCode < 1 || orderCode > Integer.MAX_VALUE) {
            throw new IllegalStateException("Không cấp được mã đơn thanh toán PayOS hợp lệ.");
        }
        payments.insertPendingOnline(bookingId, amount, orderCode, now());
        return new OnlineCheckout(orderCode, onlineAmount, false);
    }

    @Transactional
    public void confirmCashReceived(Long bookingId, Long assignedStaffId) {
        var booking = bookings.lockBooking(bookingId)
                .orElseThrow(() -> new IllegalArgumentException("Lịch hẹn không tồn tại."));
        if (!booking.staffId().equals(assignedStaffId)) {
            throw new AccessDeniedException("Chỉ nhân viên được phân công mới xác nhận tiền mặt.");
        }
        if (!"AWAITING_PAYMENT".equals(booking.status())) {
            throw new IllegalStateException("Lịch hẹn chưa ở trạng thái chờ thanh toán.");
        }
        List<PaymentPersistenceRepository.PaymentState> attempts = payments.findForBookingUpdate(bookingId);
        if (attempts.stream().anyMatch(payment -> "SUCCESS".equals(payment.status()))) {
            throw new IllegalStateException("Lịch hẹn đã có thanh toán thành công.");
        }
        payments.failPendingOnlineAttempts(bookingId);
        payments.insertCashSuccess(bookingId, bookings.bookingAmount(bookingId), now());
        if (payments.completeBooking(bookingId) != 1) {
            throw new IllegalStateException("Trạng thái lịch hẹn vừa thay đổi; giao dịch đã được hoàn tác.");
        }
    }

    @Transactional
    public void confirmOnlinePayment(long orderCode, BigDecimal amount) {
        PaymentPersistenceRepository.PaymentState payment = lockOnlinePayment(orderCode, amount);
        if ("SUCCESS".equals(payment.status())) return;
        var booking = bookings.lockBooking(payment.bookingId())
                .orElseThrow(() -> new IllegalArgumentException("Lịch hẹn thanh toán không tồn tại."));
        if (payments.hasSuccessfulPayment(payment.bookingId())) {
            payments.markFailed(payment.paymentId());
            return;
        }
        if (!"AWAITING_PAYMENT".equals(booking.status())) {
            throw new IllegalStateException("Lịch hẹn không còn chờ thanh toán trực tuyến.");
        }
        if (payments.markSuccess(payment.paymentId(), now()) != 1 || payments.completeBooking(payment.bookingId()) != 1) {
            throw new IllegalStateException("Không thể xác nhận giao dịch; trạng thái thanh toán đã thay đổi.");
        }
    }

    @Transactional
    public void failOnlinePayment(long orderCode, BigDecimal amount) {
        PaymentPersistenceRepository.PaymentState payment = lockOnlinePayment(orderCode, amount);
        if ("SUCCESS".equals(payment.status())) return;
        payments.markFailed(payment.paymentId());
    }

    private PaymentPersistenceRepository.PaymentState lockOnlinePayment(long orderCode, BigDecimal amount) {
        Long bookingId = payments.findByOrderCode(orderCode)
                .map(PaymentPersistenceRepository.PaymentState::bookingId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy giao dịch PayOS."));
        bookings.lockBooking(bookingId)
                .orElseThrow(() -> new IllegalArgumentException("Lịch hẹn thanh toán không tồn tại."));
        PaymentPersistenceRepository.PaymentState payment = payments.findByOrderCodeForUpdate(orderCode)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy giao dịch PayOS."));
        if (!"ONLINE".equals(payment.method()) || payment.amount().compareTo(amount) != 0) {
            throw new IllegalArgumentException("Thông tin giao dịch PayOS không khớp với yêu cầu đã lưu.");
        }
        return payment;
    }

    private static long onlineAmount(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalStateException("PayOS yêu cầu số tiền thanh toán lớn hơn 0.");
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

    public record OnlineCheckout(long orderCode, long amount, boolean recoverExisting) { }
}
