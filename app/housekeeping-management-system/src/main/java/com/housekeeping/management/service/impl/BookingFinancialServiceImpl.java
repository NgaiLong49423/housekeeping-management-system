package com.housekeeping.management.service.impl;

import com.housekeeping.management.dto.request.PortalForms.FinancialEntryForm;
import com.housekeeping.management.dto.response.PortalViews.CustomerFinancialCard;
import com.housekeeping.management.dto.response.PortalViews.FinancialEntryCard;
import com.housekeeping.management.dto.response.PortalViews.FinancialSummary;
import com.housekeeping.management.entity.BookingFinancialEntry;
import com.housekeeping.management.repository.BookingFinancialRepository;
import com.housekeeping.management.service.AccountAuthorizationService;
import com.housekeeping.management.service.BookingFinancialService;
import com.housekeeping.management.service.BookingReservationService;
import com.housekeeping.management.service.FeedbackCaseService;
import com.housekeeping.management.service.PaymentService;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BookingFinancialServiceImpl implements BookingFinancialService {
    private static final Set<String> TYPES = Set.of("REFUND", "PRICE_REDUCTION", "REMEDIATION_COST", "COMPENSATION", "STAFF_RECOVERY");
    private static final Set<String> METHODS = Set.of("ONLINE", "CASH", "BANK_TRANSFER", "OTHER");
    private static final Set<String> CUSTOMER_VISIBLE_TYPES = Set.of("REFUND", "PRICE_REDUCTION", "COMPENSATION");
    private final BookingFinancialRepository entries;
    private final BookingReservationService bookings;
    private final FeedbackCaseService cases;
    private final PaymentService payments;
    private final AccountAuthorizationService authorization;

    public BookingFinancialServiceImpl(BookingFinancialRepository entries, BookingReservationService bookings,
            FeedbackCaseService cases, PaymentService payments, AccountAuthorizationService authorization) {
        this.entries = entries;
        this.bookings = bookings;
        this.cases = cases;
        this.payments = payments;
        this.authorization = authorization;
    }

    @Override
    @Transactional
    public Long propose(Long bookingId, Long caseId, Long adminId, FinancialEntryForm form) {
        authorization.requireActiveAdmin(adminId);
        if (authorization.isManager(adminId))
            throw new IllegalStateException("Manager phê duyệt khoản tiền; Admin lập đề xuất để bảo đảm tách biệt quyền hạn.");
        if (form == null || form.entryType() == null || !TYPES.contains(form.entryType()) || form.amount() == null
                || form.amount().signum() <= 0 || form.reason() == null || form.reason().isBlank())
            throw new IllegalArgumentException("Thông tin khoản thu/chi chưa hợp lệ.");
        if (Set.of("REMEDIATION_COST", "COMPENSATION", "STAFF_RECOVERY").contains(form.entryType()) && caseId == null)
            throw new IllegalArgumentException("Chi phí sự cố, bồi thường và thu hồi nội bộ phải liên kết hồ sơ sự cố.");
        if (bookings.bookingCustomerId(bookingId).isEmpty()) throw new IllegalArgumentException("Booking không tồn tại.");
        if (caseId != null && !cases.belongsToBooking(caseId, bookingId))
            throw new IllegalArgumentException("Hồ sơ sự cố không thuộc booking này.");
        if ("STAFF_RECOVERY".equals(form.entryType()) && !cases.authorizesStaffRecovery(caseId, bookingId))
            throw new IllegalStateException("Chỉ lập khoản thu hồi nội bộ sau khi có xác minh và quyết định quản lý được duyệt.");

        BookingFinancialEntry entry = new BookingFinancialEntry();
        entry.setBookingId(bookingId);
        entry.setCaseId(caseId);
        entry.setEntryType(form.entryType());
        entry.setAmount(form.amount());
        entry.setReason(form.reason().trim());
        entry.setStatus("PENDING_APPROVAL");
        entry.setCreatedBy(adminId);
        entry.setCreatedAt(now());
        return entries.save(entry).getEntryId();
    }

    @Override
    @Transactional
    public void approve(Long entryId, Long managerId) {
        authorization.requireManager(managerId);
        if (entries.approve(entryId, managerId) != 1)
            throw new IllegalStateException("Chỉ người quản lý khác người đề xuất mới được duyệt khoản này.");
    }

    @Override
    @Transactional
    public void reject(Long entryId, Long managerId, String reason) {
        authorization.requireManager(managerId);
        if (reason == null || reason.isBlank()) throw new IllegalArgumentException("Cần ghi lý do từ chối.");
        if (entries.reject(entryId, managerId, reason.trim()) != 1)
            throw new IllegalStateException("Chỉ người quản lý khác người lập mới được từ chối khoản đang chờ duyệt.");
    }

    @Override
    @Transactional
    public void cancel(Long entryId, Long adminId, String reason) {
        authorization.requireActiveAdmin(adminId);
        if (reason == null || reason.isBlank()) throw new IllegalArgumentException("Cần ghi lý do hủy khoản.");
        if (entries.cancel(entryId, adminId, reason.trim()) != 1)
            throw new IllegalStateException("Không thể hủy khoản đã chi, hoàn hoặc đã bị từ chối.");
    }

    @Override
    @Transactional
    public void settle(Long entryId, Long adminId, String method, String reference, boolean simulated) {
        authorization.requireActiveAdmin(adminId);
        if (method == null || !METHODS.contains(method) || reference == null || reference.isBlank())
            throw new IllegalArgumentException("Cần phương thức thanh toán và mã tham chiếu trước khi xác nhận.");
        String status = simulated ? "SIMULATED" : "PAID";
        if (entries.settle(entryId, adminId, method, reference.trim(), simulated, status) != 1)
            throw new IllegalStateException("Khoản tiền cần được duyệt và có tệp hóa đơn/biên nhận trước khi ghi nhận.");
    }

    @Override
    @Transactional(readOnly = true)
    public List<FinancialEntryCard> forBooking(Long bookingId, Long actorId) {
        authorization.requireActiveAdmin(actorId);
        return entries.findByBookingIdOrderByCreatedAtAscEntryIdAsc(bookingId).stream().map(BookingFinancialServiceImpl::card).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public FinancialSummary summary(Long actorId) {
        authorization.requireActiveAdmin(actorId);
        var financialPaid = entries.sumPaidByTypes(List.of("REFUND", "PRICE_REDUCTION"));
        var remediation = entries.sumPaidByTypes(List.of("REMEDIATION_COST", "COMPENSATION"));
        var recovery = entries.sumPaidByTypes(List.of("STAFF_RECOVERY"));
        var paymentTotals = payments.accountingTotals();
        var refundsAndReductions = financialPaid;
        var net = paymentTotals.paidAmount().subtract(refundsAndReductions).subtract(remediation).add(recovery);
        return new FinancialSummary(paymentTotals.paidAmount(), refundsAndReductions, remediation, recovery, net,
                entries.sumSimulated().add(paymentTotals.simulatedAmount()));
    }

    @Override
    @Transactional(readOnly = true)
    public List<FinancialEntryCard> all(Long actorId) {
        authorization.requireActiveAdmin(actorId);
        return entries.findAllByOrderByCreatedAtDescEntryIdDesc().stream().map(BookingFinancialServiceImpl::card).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<CustomerFinancialCard> customerVisible(Long bookingId, Long customerId) {
        Long ownerId = bookings.bookingCustomerId(bookingId).orElse(null);
        if (!customerId.equals(ownerId)) return List.of();
        return entries.findByBookingIdAndEntryTypeInOrderByCreatedAtAscEntryIdAsc(bookingId, CUSTOMER_VISIBLE_TYPES)
                .stream().map(entry -> new CustomerFinancialCard(entry.getEntryType(), entry.getAmount(), entry.getStatus(),
                        entry.getPaymentMethod(), entry.getTransactionReference(), entry.getPaidAt())).toList();
    }

    private static FinancialEntryCard card(BookingFinancialEntry entry) {
        return new FinancialEntryCard(entry.getEntryId(), entry.getBookingId(), entry.getCaseId(), entry.getEntryType(),
                entry.getAmount(), entry.getReason(), entry.getStatus(), entry.getPaymentMethod(),
                entry.getTransactionReference(), entry.getReceiptPath(), entry.isSimulated(), entry.getCreatedBy(),
                entry.getApprovedBy(), entry.getSettledBy(), entry.getRejectionReason(), entry.getCancellationReason(),
                entry.getCreatedAt(), entry.getPaidAt());
    }

    private static LocalDateTime now() {
        return LocalDateTime.now(ZoneOffset.ofHours(7)).withNano(0);
    }
}
