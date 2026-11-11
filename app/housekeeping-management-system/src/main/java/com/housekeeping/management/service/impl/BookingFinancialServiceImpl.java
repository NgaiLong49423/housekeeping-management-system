package com.housekeeping.management.service.impl;

import com.housekeeping.management.dto.request.PortalForms.FinancialEntryForm;
import com.housekeeping.management.dto.request.PortalForms.CustomerCompensationResponseForm;
import com.housekeeping.management.dto.response.PortalViews.CustomerFinancialCard;
import com.housekeeping.management.dto.response.PortalViews.FinancialEntryCard;
import com.housekeeping.management.dto.response.PortalViews.FinancialSummary;
import com.housekeeping.management.dto.response.PortalViews.FinancialLedgerCard;
import com.housekeeping.management.dto.response.PortalViews.CustomerCompensationOfferCard;
import com.housekeeping.management.dto.response.PortalViews.AdminPayoutDetails;
import com.housekeeping.management.entity.BookingFinancialEntry;
import com.housekeeping.management.entity.FinancialPayoutResponse;
import com.housekeeping.management.repository.BookingFinancialRepository;
import com.housekeeping.management.repository.FinancialPayoutResponseRepository;
import com.housekeeping.management.security.PayoutAccountDetailsCipher;
import com.housekeeping.management.service.AccountAuthorizationService;
import com.housekeeping.management.service.BookingFinancialService;
import com.housekeeping.management.service.BookingReservationService;
import com.housekeeping.management.service.FeedbackCaseService;
import com.housekeeping.management.service.PaymentService;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Comparator;
import java.util.Optional;
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
    private final FinancialPayoutResponseRepository payoutResponses;
    private final PayoutAccountDetailsCipher payoutCipher;

    public BookingFinancialServiceImpl(BookingFinancialRepository entries, BookingReservationService bookings,
            FeedbackCaseService cases, PaymentService payments, AccountAuthorizationService authorization,
            FinancialPayoutResponseRepository payoutResponses, PayoutAccountDetailsCipher payoutCipher) {
        this.entries = entries;
        this.bookings = bookings;
        this.cases = cases;
        this.payments = payments;
        this.authorization = authorization;
        this.payoutResponses = payoutResponses;
        this.payoutCipher = payoutCipher;
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
        if("COMPENSATION".equals(form.entryType()) && caseId!=null) {
            var incident=cases.adminCaseById(caseId,adminId);
            if(incident!=null && incident.resolutionWorkflowRequired())
                throw new IllegalStateException("Khoản bồi thường hồ sơ mới phải nằm trong phương án khách chọn và Manager duyệt.");
        }
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
            throw new IllegalStateException("Khoản này không còn ở trạng thái chờ duyệt.");
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
        BookingFinancialEntry entry = entries.findByIdForUpdate(entryId)
                .orElseThrow(() -> new IllegalStateException("Khoản tiền không tồn tại hoặc đã thay đổi."));
        FinancialPayoutResponse payout = null;
        if ("COMPENSATION".equals(entry.getEntryType())) {
            if (simulated) throw new IllegalStateException("Bồi thường phải được ghi nhận là khoản chi thật sau khi thực hiện.");
            payout = payoutResponses.findByEntryIdForUpdate(entryId)
                    .orElseThrow(() -> new IllegalStateException("Cần chờ khách xác nhận cách nhận bồi thường trước khi quyết toán."));
            if (!"ACCEPTED".equals(payout.getResponseStatus()) || !method.equals(payout.getPayoutMethod()))
                throw new IllegalStateException("Phương thức quyết toán phải khớp với lựa chọn đã xác nhận của khách.");
        }
        String status = simulated ? "SIMULATED" : "PAID";
        if (entries.settle(entryId, adminId, method, reference.trim(), simulated, status) != 1)
            throw new IllegalStateException("Khoản tiền cần được duyệt và có tệp hóa đơn/biên nhận trước khi ghi nhận.");
        if (payout != null) {
            payout.setEncryptedAccountDetails(null);
            payoutResponses.save(payout);
        }
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
    public Optional<FinancialEntryCard> entry(Long entryId, Long actorId) {
        authorization.requireActiveAdmin(actorId);
        return entries.findById(entryId).map(BookingFinancialServiceImpl::card);
    }

    @Override
    @Transactional(readOnly = true)
    public List<FinancialLedgerCard> ledger(Long actorId) {
        authorization.requireActiveAdmin(actorId);
        List<FinancialLedgerCard> ledger = payments.successfulPaymentsForAccounting().stream()
                .map(payment -> new FinancialLedgerCard(payment.bookingId(), "PAYMENT", "Thanh toán dịch vụ",
                        payment.simulated() ? "SIMULATED" : "SUCCESS", payment.amount(), true,
                        payment.method(), payment.transactionCode(), "Doanh thu booking #" + payment.bookingId(),
                        payment.paidAt(), payment.simulated(), null))
                .collect(java.util.stream.Collectors.toCollection(java.util.ArrayList::new));
        entries.findAllByOrderByCreatedAtDescEntryIdDesc().stream().map(BookingFinancialServiceImpl::card)
                .map(entry -> new FinancialLedgerCard(entry.bookingId(), "ADJUSTMENT", entry.getTypeLabel(),
                        entry.status(), entry.amount(), "STAFF_RECOVERY".equals(entry.type()), entry.paymentMethod(),
                        entry.reference(), entry.reason(), entry.paidAt() == null ? entry.createdAt() : entry.paidAt(),
                        entry.simulated(), entry))
                .forEach(ledger::add);
        return ledger.stream().sorted(Comparator.comparing(FinancialLedgerCard::occurredAt,
                Comparator.nullsLast(Comparator.reverseOrder()))).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<CustomerCompensationOfferCard> customerCompensationOffers(Long customerId) {
        List<BookingFinancialEntry> approved = entries.findApprovedCompensationsForCustomer(customerId);
        if (approved.isEmpty()) return List.of();
        Map<Long, FinancialPayoutResponse> responses = payoutResponses.findAllByEntryIdIn(
                approved.stream().map(BookingFinancialEntry::getEntryId).toList()).stream()
                .collect(java.util.stream.Collectors.toMap(FinancialPayoutResponse::getEntryId, response -> response));
        return approved.stream().map(entry -> {
            FinancialPayoutResponse response = responses.get(entry.getEntryId());
            return new CustomerCompensationOfferCard(entry.getEntryId(), entry.getBookingId(), entry.getAmount(),
                    entry.getReason(), response == null ? "PENDING_CUSTOMER" : response.getResponseStatus(),
                    response == null ? null : response.getPayoutMethod());
        }).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<CustomerCompensationOfferCard> customerCompensationOffersForBooking(Long bookingId, Long customerId) {
        Long ownerId = bookings.bookingCustomerId(bookingId).orElse(null);
        if (!customerId.equals(ownerId)) return List.of();
        return customerCompensationOffers(customerId).stream().filter(offer -> bookingId.equals(offer.bookingId())).toList();
    }

    @Override
    @Transactional
    public void respondToCompensationOffer(Long bookingId, Long entryId, Long customerId,
            CustomerCompensationResponseForm form) {
        if (form == null || form.decision() == null)
            throw new IllegalArgumentException("Hãy chọn cách phản hồi đề nghị bồi thường.");
        Long ownerId = bookings.bookingCustomerId(bookingId).orElse(null);
        if (!customerId.equals(ownerId)) throw new org.springframework.security.access.AccessDeniedException("Booking không thuộc tài khoản của bạn.");
        BookingFinancialEntry entry = entries.findByIdForUpdate(entryId)
                .orElseThrow(() -> new IllegalArgumentException("Đề nghị bồi thường không tồn tại."));
        if (!bookingId.equals(entry.getBookingId()) || !customerId.equals(ownerId)
                || !"COMPENSATION".equals(entry.getEntryType()) || !"APPROVED".equals(entry.getStatus()))
            throw new IllegalStateException("Đề nghị này không còn chờ xác nhận.");
        FinancialPayoutResponse response = payoutResponses.findByEntryIdForUpdate(entryId).orElse(null);
        boolean detailsRequested=response!=null && "PENDING_DETAILS".equals(response.getResponseStatus());
        if (response!=null && !detailsRequested)
            throw new IllegalStateException("Bạn đã phản hồi đề nghị bồi thường này rồi.");
        if (detailsRequested && (!"ACCEPT".equals(form.decision()) || !"BANK_TRANSFER".equals(form.payoutMethod())))
            throw new IllegalStateException("Phương án đã duyệt yêu cầu bạn bổ sung thông tin chuyển khoản đã chọn.");
        if(response==null) {
            response = new FinancialPayoutResponse();
            response.setEntryId(entryId);
            response.setCustomerId(customerId);
            response.setRespondedAt(now());
        }
        if ("DISCUSS".equals(form.decision())) {
            if(detailsRequested) throw new IllegalStateException("Không thể thay đổi phương án sau khi Manager đã duyệt.");
            response.setResponseStatus("NEEDS_DISCUSSION");
        } else if ("ACCEPT".equals(form.decision())) {
            if (!Set.of("BANK_TRANSFER", "CASH").contains(form.payoutMethod()))
                throw new IllegalArgumentException("Hãy chọn chuyển khoản hoặc nhận tiền mặt.");
            response.setResponseStatus("ACCEPTED");
            response.setPayoutMethod(form.payoutMethod());
            if ("BANK_TRANSFER".equals(form.payoutMethod())) {
                String bankName = cleanRequired(form.bankName(), "Hãy nhập tên ngân hàng.");
                String accountHolder = cleanRequired(form.accountHolder(), "Hãy nhập tên chủ tài khoản.");
                String accountNumber = cleanRequired(form.accountNumber(), "Hãy nhập số tài khoản.");
                response.setEncryptedAccountDetails(payoutCipher.encrypt(
                        new PayoutAccountDetailsCipher.AccountDetails(bankName, accountHolder, accountNumber)));
            }
        } else {
            throw new IllegalArgumentException("Cách phản hồi đề nghị bồi thường không hợp lệ.");
        }
        payoutResponses.save(response);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<AdminPayoutDetails> adminPayoutDetails(Long entryId, Long adminId) {
        authorization.requireActiveAdmin(adminId);
        return payoutResponses.findByEntryId(entryId)
                .map(response -> {
                    if (!"ACCEPTED".equals(response.getResponseStatus()) || !"BANK_TRANSFER".equals(response.getPayoutMethod())
                            || response.getEncryptedAccountDetails() == null)
                        return new AdminPayoutDetails(entryId, response.getResponseStatus(), response.getPayoutMethod(), null, null, null);
                    var account = payoutCipher.decrypt(response.getEncryptedAccountDetails());
                    return new AdminPayoutDetails(entryId, response.getResponseStatus(), response.getPayoutMethod(),
                            account.bankName(), account.accountHolder(), account.accountNumber());
                });
    }

    @Override
    @Transactional
    public Long recordManagerApprovedResolutionPayout(Long bookingId, Long caseId, Long actionId,
            java.math.BigDecimal amount, String reason, Long adminId, Long managerId, String customerMethod) {
        authorization.requireManager(managerId);
        authorization.requireActiveAdmin(adminId);
        if (!Set.of("CASH", "BANK_TRANSFER").contains(customerMethod) || amount == null || amount.signum() <= 0)
            throw new IllegalArgumentException("Phương thức hoặc số tiền trong phương án đã duyệt không hợp lệ.");
        if (bookings.bookingCustomerId(bookingId).isEmpty() || !cases.belongsToBooking(caseId, bookingId))
            throw new IllegalArgumentException("Booking/hồ sơ của khoản chi không hợp lệ.");
        BookingFinancialEntry entry = new BookingFinancialEntry();
        entry.setBookingId(bookingId); entry.setCaseId(caseId); entry.setResolutionActionId(actionId);
        entry.setEntryType("COMPENSATION"); entry.setAmount(amount); entry.setReason(reason);
        entry.setStatus("APPROVED"); entry.setCreatedBy(adminId); entry.setApprovedBy(managerId);
        entry.setCreatedAt(now()); entry.setApprovedAt(now());
        BookingFinancialEntry saved=entries.save(entry);
        FinancialPayoutResponse response=new FinancialPayoutResponse();
        response.setEntryId(saved.getEntryId());
        response.setCustomerId(bookings.bookingCustomerId(bookingId).orElseThrow());
        response.setPayoutMethod(customerMethod); response.setRespondedAt(now());
        response.setResponseStatus("CASH".equals(customerMethod) ? "ACCEPTED" : "PENDING_DETAILS");
        payoutResponses.save(response);
        return saved.getEntryId();
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

    private static String cleanRequired(String value, String message) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(message);
        return value.trim();
    }

    private static LocalDateTime now() {
        return LocalDateTime.now(ZoneOffset.ofHours(7)).withNano(0);
    }
}
