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
        when(entries.approve(3L,5L)).thenReturn(1);
        service.approve(3L,5L);
        verify(entries).approve(3L,5L);
    }

    @Test void staffRecoveryRequiresSeparateManagerApprovedResponsibilityDecision() {
        when(bookings.bookingCustomerId(1L)).thenReturn(Optional.of(55L));
        when(cases.belongsToBooking(8L,1L)).thenReturn(true);
        when(cases.authorizesStaffRecovery(8L,1L)).thenReturn(false);
        var form=new FinancialEntryForm("STAFF_RECOVERY",new BigDecimal("1000"),"Khoản thu hồi",null,null,false);
        assertThatThrownBy(()->service.propose(1L,8L,2L,form)).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("quyết định quản lý");
        verify(entries,never()).save(any());
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
        when(entries.findByIdForUpdate(9L)).thenReturn(Optional.of(entry));
        when(payoutResponses.findByEntryIdForUpdate(9L)).thenReturn(Optional.empty());
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
        entry.setEntryType("COMPENSATION"); entry.setStatus("APPROVED");
        when(entries.findByIdForUpdate(9L)).thenReturn(Optional.of(entry));
        when(payoutResponses.findByEntryIdForUpdate(9L)).thenReturn(Optional.empty());
        service.respondToCompensationOffer(42L,9L,7L,
                new com.housekeeping.management.dto.request.PortalForms.CustomerCompensationResponseForm(
                        "DISCUSS",null,null,null,null));
        var captor=org.mockito.ArgumentCaptor.forClass(FinancialPayoutResponse.class);
        verify(payoutResponses).save(captor.capture());
        org.assertj.core.api.Assertions.assertThat(captor.getValue().getResponseStatus()).isEqualTo("NEEDS_DISCUSSION");
        org.assertj.core.api.Assertions.assertThat(captor.getValue().getEncryptedAccountDetails()).isNull();
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
        entry.setEntryType("COMPENSATION"); entry.setStatus("APPROVED");
        when(entries.findByIdForUpdate(9L)).thenReturn(Optional.of(entry));
        when(payoutResponses.findByEntryIdForUpdate(9L)).thenReturn(Optional.empty());
        assertThatThrownBy(()->service.settle(9L,2L,"CASH","SIM-1",true))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("khoản chi thật");
        assertThatThrownBy(()->service.settle(9L,2L,"CASH","REF-1",false))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("xác nhận");

        FinancialPayoutResponse response=new FinancialPayoutResponse();
        response.setEntryId(9L); response.setResponseStatus("ACCEPTED"); response.setPayoutMethod("BANK_TRANSFER");
        response.setEncryptedAccountDetails(cipher.encrypt(new PayoutAccountDetailsCipher.AccountDetails("ACB","NGUYEN VAN A","123456789")));
        when(payoutResponses.findByEntryIdForUpdate(9L)).thenReturn(Optional.of(response));
        when(entries.settle(9L,2L,"BANK_TRANSFER","REF-2",false,"PAID")).thenReturn(1);
        service.settle(9L,2L,"BANK_TRANSFER","REF-2",false);
        org.assertj.core.api.Assertions.assertThat(response.getEncryptedAccountDetails()).isNull();
        verify(payoutResponses).save(response);
    }
}
