package com.housekeeping.management.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.housekeeping.management.dto.request.PortalForms.FinancialEntryForm;
import com.housekeeping.management.repository.BookingFinancialRepository;
import com.housekeeping.management.repository.FinancialPayoutResponseRepository;
import com.housekeeping.management.entity.BookingFinancialEntry;
import com.housekeeping.management.entity.FinancialPayoutResponse;
import com.housekeeping.management.security.PayoutAccountDetailsCipher;
import java.util.Optional;
import java.util.List;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

class BookingFinancialServiceTest {
    BookingFinancialRepository entries=mock(BookingFinancialRepository.class);
    AccountAuthorizationService authorization=mock(AccountAuthorizationService.class);
    BookingReservationService bookings=mock(BookingReservationService.class);
    FeedbackCaseService cases=mock(FeedbackCaseService.class);
    PaymentService payments=mock(PaymentService.class);
    FinancialPayoutResponseRepository payoutResponses=mock(FinancialPayoutResponseRepository.class);
    PayoutAccountDetailsCipher cipher=new PayoutAccountDetailsCipher(
            java.util.Base64.getEncoder().encodeToString(new byte[32]));
    BookingFinancialService service=new com.housekeeping.management.service.impl.BookingFinancialServiceImpl(
            entries,bookings,cases,payments,authorization,payoutResponses,cipher);

    @Test void compensationMustBeLinkedToIncidentCase() {
        FinancialEntryForm form=new FinancialEntryForm("COMPENSATION",new BigDecimal("100000"),"Bồi thường đồ bị vỡ",null,null,false);
        assertThatThrownBy(()->service.propose(1L,null,2L,form)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("hồ sơ sự cố");
        verifyNoInteractions(entries,bookings,cases,payments);
    }

    @Test void managerCannotProposeAnEntryThatTheyMustApprove() {
        when(authorization.isManager(7L)).thenReturn(true);
        var form=new FinancialEntryForm("COMPENSATION",new BigDecimal("100000"),"Bồi thường đồ bị vỡ",null,null,false);
        assertThatThrownBy(()->service.propose(1L,8L,7L,form)).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Admin lập đề xuất");
        verifyNoInteractions(entries,bookings,cases,payments);
    }

    @Test void onlyIncidentManagerCanApproveMoney() {
        doThrow(new AccessDeniedException("manager required")).when(authorization).requireManager(10L);
        assertThatThrownBy(()->service.approve(3L,10L)).isInstanceOf(AccessDeniedException.class);
        verify(entries,never()).approve(anyLong(),anyLong());
    }

    @Test void managerCanApprovePendingEntryWithoutASecondManagerAccount() {
        var entry=new BookingFinancialEntry(); entry.setEntryId(3L); entry.setCaseId(8L); entry.setEntryType("COMPENSATION");
        var customerApproval=new FinancialPayoutResponse(); customerApproval.setEntryId(3L);
        customerApproval.setResponseStatus("AGREED_AMOUNT"); customerApproval.setAmountAgreedAt(java.time.LocalDateTime.now());
        when(entries.findByIdForUpdate(3L)).thenReturn(Optional.of(entry));
        when(payoutResponses.findByEntryId(3L)).thenReturn(Optional.of(customerApproval));
        when(entries.approve(3L,5L)).thenReturn(1);
        service.approve(3L,5L);
        verify(entries).approve(3L,5L);
        verify(cases).updateLifecycle(8L,"WAITING_PAYOUT");
    }

    @Test void managerCannotApproveCompensationUntilCustomerAgreesToAmount() {
        var entry=new BookingFinancialEntry(); entry.setEntryId(3L); entry.setCaseId(8L); entry.setEntryType("COMPENSATION");
        when(entries.findByIdForUpdate(3L)).thenReturn(Optional.of(entry));
        when(payoutResponses.findByEntryId(3L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.approve(3L,5L)).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("khách đồng ý");
        verify(entries,never()).approve(anyLong(),anyLong());
    }

    @Test void customerAgreementLocksTheAmountAndMovesTheCaseToManager() {
        when(bookings.bookingCustomerId(42L)).thenReturn(Optional.of(7L));
        var entry=new BookingFinancialEntry(); entry.setEntryId(9L); entry.setBookingId(42L);
        entry.setCaseId(11L); entry.setEntryType("COMPENSATION"); entry.setStatus("PROPOSED");
        when(entries.findByIdForUpdate(9L)).thenReturn(Optional.of(entry));
        when(payoutResponses.findByEntryIdForUpdate(9L)).thenReturn(Optional.empty());

        service.respondToCompensationOffer(42L,9L,7L,
                new com.housekeeping.management.dto.request.PortalForms.CustomerCompensationResponseForm(
                        "AGREE",null,null,null,null));

        org.assertj.core.api.Assertions.assertThat(entry.getStatus()).isEqualTo("PENDING_APPROVAL");
        var captured=org.mockito.ArgumentCaptor.forClass(FinancialPayoutResponse.class);
        verify(payoutResponses).save(captured.capture());
        org.assertj.core.api.Assertions.assertThat(captured.getValue().getResponseStatus()).isEqualTo("AGREED_AMOUNT");
        org.assertj.core.api.Assertions.assertThat(captured.getValue().getAmountAgreedAt()).isNotNull();
        org.assertj.core.api.Assertions.assertThat(captured.getValue().getPayoutMethod()).isNull();
        org.assertj.core.api.Assertions.assertThat(captured.getValue().getEncryptedAccountDetails()).isNull();
        verify(cases).updateLifecycle(11L,"WAITING_MANAGER");
    }

    @Test void staffRecoveryRequiresSeparateManagerApprovedResponsibilityDecision() {
        when(bookings.bookingCustomerId(1L)).thenReturn(Optional.of(55L));
        when(cases.belongsToBooking(8L,1L)).thenReturn(true);
        when(cases.authorizesStaffRecovery(8L,1L)).thenReturn(false);
        var form=new FinancialEntryForm("STAFF_RECOVERY",new BigDecimal("1000"),"Khoản thu hồi",null,null,false);
        assertThatThrownBy(()->service.propose(1L,8L,2L,form)).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("khoản chi/thu chung");
        verify(entries,never()).save(any());
    }

    @Test void missingItemReplacementProposalCanFollowCustomerDiscussionAfterInitialSearch() {
        when(bookings.bookingCustomerId(1L)).thenReturn(Optional.of(7L));
        when(cases.belongsToBooking(8L,1L)).thenReturn(true);
        when(cases.hasSufficientEvidence(8L)).thenReturn(true);
        when(cases.adminCaseById(8L,3L)).thenReturn(new com.housekeeping.management.dto.response.PortalViews.IncidentCard(
                8L,9L,1L,7L,2L,List.of("MISSING_PROPERTY"),"Món đồ chưa tìm thấy","INVESTIGATING",null,null,
                null,false,null,null,null,null,null,java.time.LocalDateTime.now(),null));
        when(entries.existsByCaseIdAndEntryTypeAndStatusIn(eq(8L),eq("COMPENSATION"),anyCollection()))
                .thenReturn(true,false);
        when(entries.save(any())).thenAnswer(call -> call.getArgument(0));
        var form=new FinancialEntryForm("COMPENSATION",new BigDecimal("150000"),"Đề xuất sau khi đã tìm kiếm",null,null,false);

        service.propose(1L,8L,3L,form);

        verify(entries).save(any(BookingFinancialEntry.class));
        verify(cases).updateLifecycle(8L,"WAITING_CUSTOMER");
    }

    @Test void settlementRequiresApprovedEntryAndStoredReceipt() {
        var entry=new BookingFinancialEntry(); entry.setEntryId(5L); entry.setEntryType("REFUND");
        when(entries.findByIdForUpdate(5L)).thenReturn(Optional.of(entry));
        when(entries.settle(5L,2L,"BANK_TRANSFER","TX-1",false,"PAID")).thenReturn(0);
        assertThatThrownBy(()->service.settle(5L,2L,"BANK_TRANSFER","TX-1",false))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("hóa đơn/biên nhận");
    }

    @Test void ledgerIncludesPaymentsAndAdjustmentRowsWithoutConflatingThem() {
        when(payments.successfulPaymentsForAccounting()).thenReturn(java.util.List.of(
                new PaymentService.AccountingPayment(10L,42L,new BigDecimal("150000"),"CASH",null,
                        java.time.LocalDateTime.parse("2026-11-01T10:00:00"),false)));
        when(entries.findAllByOrderByCreatedAtDescEntryIdDesc()).thenReturn(java.util.List.of());
        var ledger=service.ledger(3L);
        org.assertj.core.api.Assertions.assertThat(ledger).hasSize(1);
        org.assertj.core.api.Assertions.assertThat(ledger.getFirst().bookingId()).isEqualTo(42L);
        org.assertj.core.api.Assertions.assertThat(ledger.getFirst().sourceType()).isEqualTo("PAYMENT");
        org.assertj.core.api.Assertions.assertThat(ledger.getFirst().amount()).isEqualByComparingTo("150000");
    }

    @Test void customerBankChoiceIsStoredEncryptedOnlyAfterApprovedOffer() {
        when(bookings.bookingCustomerId(42L)).thenReturn(Optional.of(7L));
        var entry=new BookingFinancialEntry(); entry.setEntryId(9L); entry.setBookingId(42L);
        entry.setEntryType("COMPENSATION"); entry.setStatus("APPROVED");
        var agreed=new FinancialPayoutResponse(); agreed.setEntryId(9L); agreed.setCustomerId(7L);
        agreed.setResponseStatus("AGREED_AMOUNT"); agreed.setAmountAgreedAt(java.time.LocalDateTime.now());
        when(entries.findByIdForUpdate(9L)).thenReturn(Optional.of(entry));
        when(payoutResponses.findByEntryIdForUpdate(9L)).thenReturn(Optional.of(agreed));
        service.respondToCompensationOffer(42L,9L,7L,
                new com.housekeeping.management.dto.request.PortalForms.CustomerCompensationResponseForm(
                        "ACCEPT","BANK_TRANSFER","ACB","NGUYEN VAN A","123456789"));
        var captor=org.mockito.ArgumentCaptor.forClass(FinancialPayoutResponse.class);
        verify(payoutResponses).save(captor.capture());
        var response=captor.getValue();
        org.assertj.core.api.Assertions.assertThat(response.getEncryptedAccountDetails()).doesNotContain("123456789");
        org.assertj.core.api.Assertions.assertThat(cipher.decrypt(response.getEncryptedAccountDetails()).accountNumber()).isEqualTo("123456789");
    }

    @Test void customerCanAskToDiscussWithoutProvidingBankDetails() {
        when(bookings.bookingCustomerId(42L)).thenReturn(Optional.of(7L));
        var entry=new BookingFinancialEntry(); entry.setEntryId(9L); entry.setBookingId(42L);
        entry.setEntryType("COMPENSATION"); entry.setStatus("PROPOSED"); entry.setCaseId(9L);
        when(entries.findByIdForUpdate(9L)).thenReturn(Optional.of(entry));
        when(payoutResponses.findByEntryIdForUpdate(9L)).thenReturn(Optional.empty());
        service.respondToCompensationOffer(42L,9L,7L,
                new com.housekeeping.management.dto.request.PortalForms.CustomerCompensationResponseForm(
                        "DISCUSS",null,null,null,null,"Tôi chưa đồng ý số tiền vì chưa rõ căn cứ sửa chữa."));
        var captor=org.mockito.ArgumentCaptor.forClass(FinancialPayoutResponse.class);
        verify(payoutResponses).save(captor.capture());
        org.assertj.core.api.Assertions.assertThat(captor.getValue().getResponseStatus()).isEqualTo("NEEDS_DISCUSSION");
        org.assertj.core.api.Assertions.assertThat(captor.getValue().getEncryptedAccountDetails()).isNull();
        verify(cases).updateLifecycle(9L,"INVESTIGATING");
        verify(cases).addCustomerMessage(9L,42L,7L,"Tôi chưa đồng ý số tiền vì chưa rõ căn cứ sửa chữa.",false);
    }

    @Test void customerCannotRespondToAnotherCustomersBooking() {
        when(bookings.bookingCustomerId(42L)).thenReturn(Optional.of(8L));
        assertThatThrownBy(()->service.respondToCompensationOffer(42L,9L,7L,
                new com.housekeeping.management.dto.request.PortalForms.CustomerCompensationResponseForm(
                        "ACCEPT","CASH",null,null,null)))
                .isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(entries,payoutResponses);
    }

    @Test void compensationCannotSettleUntilCustomerAcceptsAndSensitiveDetailsAreDeletedAfterSettlement() {
        var entry=new BookingFinancialEntry(); entry.setEntryId(9L); entry.setBookingId(42L);
        entry.setCaseId(11L); entry.setEntryType("COMPENSATION"); entry.setStatus("APPROVED");
        when(entries.findByIdForUpdate(9L)).thenReturn(Optional.of(entry));
        when(cases.staffInvestigationFinalized(11L)).thenReturn(true);
        when(cases.hasSufficientEvidence(11L)).thenReturn(true);
        when(payoutResponses.findByEntryIdForUpdate(9L)).thenReturn(Optional.empty());
        assertThatThrownBy(()->service.settle(9L,2L,"CASH","SIM-1",true))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("khoản chi thật");
        assertThatThrownBy(()->service.settle(9L,2L,"CASH","REF-1",false))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("Chờ khách chọn");

        FinancialPayoutResponse response=new FinancialPayoutResponse();
        response.setEntryId(9L); response.setResponseStatus("ACCEPTED"); response.setPayoutMethod("BANK_TRANSFER");
        response.setEncryptedAccountDetails(cipher.encrypt(new PayoutAccountDetailsCipher.AccountDetails("ACB","NGUYEN VAN A","123456789")));
        when(payoutResponses.findByEntryIdForUpdate(9L)).thenReturn(Optional.of(response));
        when(entries.settle(9L,2L,"BANK_TRANSFER","REF-2",false,"PAID")).thenReturn(1);
        service.settle(9L,2L,"BANK_TRANSFER","REF-2",false);
        org.assertj.core.api.Assertions.assertThat(response.getResponseStatus()).isEqualTo("SETTLED");
        org.assertj.core.api.Assertions.assertThat(response.getEncryptedAccountDetails()).isNull();
        verify(payoutResponses).save(response);
    }

    @Test void compensationSettlementWaitsForFinalStaffResponsibilityDecision() {
        var entry=new BookingFinancialEntry(); entry.setEntryId(19L); entry.setBookingId(42L);
        entry.setCaseId(11L); entry.setEntryType("COMPENSATION"); entry.setStatus("APPROVED");
        when(entries.findByIdForUpdate(19L)).thenReturn(Optional.of(entry));
        when(cases.staffInvestigationFinalized(11L)).thenReturn(false);

        assertThatThrownBy(() -> service.settle(19L,2L,"CASH","REF-19",false))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("Manager chốt cuối trách nhiệm Staff");
        verify(entries, never()).settle(anyLong(),anyLong(),anyString(),anyString(),anyBoolean(),anyString());
    }

    @Test void cancellingAcceptedTransferProposalClearsEncryptedBankDetails() {
        var entry=new BookingFinancialEntry(); entry.setEntryId(12L); entry.setCaseId(11L);
        entry.setEntryType("COMPENSATION"); entry.setStatus("APPROVED");
        var response=new FinancialPayoutResponse(); response.setEntryId(12L); response.setCustomerId(7L);
        response.setResponseStatus("ACCEPTED"); response.setPayoutMethod("BANK_TRANSFER");
        response.setEncryptedAccountDetails(cipher.encrypt(
                new PayoutAccountDetailsCipher.AccountDetails("ACB","Customer","123456789")));
        when(entries.findByIdForUpdate(12L)).thenReturn(Optional.of(entry));
        when(entries.cancel(12L,3L,"Cần lập lại")).thenReturn(1);
        when(payoutResponses.findByEntryIdForUpdate(12L)).thenReturn(Optional.of(response));

        service.cancel(12L,3L,"Cần lập lại");

        org.assertj.core.api.Assertions.assertThat(response.getResponseStatus()).isEqualTo("NEEDS_DISCUSSION");
        org.assertj.core.api.Assertions.assertThat(response.getPayoutMethod()).isNull();
        org.assertj.core.api.Assertions.assertThat(response.getEncryptedAccountDetails()).isNull();
        verify(payoutResponses).save(response);
        verify(cases).updateLifecycle(11L,"INVESTIGATING");
    }
}
