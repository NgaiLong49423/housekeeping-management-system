package com.housekeeping.management.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.housekeeping.management.dto.request.PortalForms.FinancialEntryForm;
import com.housekeeping.management.repository.BookingFinancialRepository;
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
    BookingFinancialService service=new com.housekeeping.management.service.impl.BookingFinancialServiceImpl(
            entries,bookings,cases,payments,authorization);

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
        when(entries.settle(5L,2L,"BANK_TRANSFER","TX-1",false,"PAID")).thenReturn(0);
        assertThatThrownBy(()->service.settle(5L,2L,"BANK_TRANSFER","TX-1",false))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("hóa đơn/biên nhận");
    }
}
