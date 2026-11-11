package com.housekeeping.management.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.housekeeping.management.dto.request.PortalForms.*;
import com.housekeeping.management.dto.response.PortalViews.IncidentCard;
import com.housekeeping.management.entity.*;
import com.housekeeping.management.repository.*;
import com.housekeeping.management.service.impl.IncidentResolutionServiceImpl;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class IncidentResolutionServiceTest {
    private FeedbackResolutionPlanRepository plans;
    private FeedbackResolutionOptionRepository options;
    private FeedbackResolutionActionRepository actions;
    private FeedbackWorkOrderRepository workOrders;
    private BookingFinancialRepository financialEntries;
    private FeedbackCaseService cases;
    private BookingFinancialService financials;
    private AccountAuthorizationService authorization;
    private StaffManagementService staff;
    private IncidentResolutionService service;

    @BeforeEach void setUp() {
        plans=mock(FeedbackResolutionPlanRepository.class); options=mock(FeedbackResolutionOptionRepository.class);
        actions=mock(FeedbackResolutionActionRepository.class); workOrders=mock(FeedbackWorkOrderRepository.class);
        financialEntries=mock(BookingFinancialRepository.class); cases=mock(FeedbackCaseService.class);
        financials=mock(BookingFinancialService.class); authorization=mock(AccountAuthorizationService.class);
        staff=mock(StaffManagementService.class);
        service=new IncidentResolutionServiceImpl(plans,options,actions,workOrders,financialEntries,cases,financials,authorization,staff);
    }

    @Test void adminCanSendAtMostThreeVersionedOptionsAndFinancialActionsNeedBasis() {
        when(cases.adminCaseById(7L,3L)).thenReturn(incident("VERIFIED"));
        when(plans.findFirstByCaseIdOrderByVersionNoDesc(7L)).thenReturn(Optional.empty());
        AtomicLong ids=new AtomicLong(100);
        when(plans.saveAndFlush(any())).thenAnswer(call->{FeedbackResolutionPlan plan=call.getArgument(0);plan.setPlanId(ids.getAndIncrement());return plan;});
        when(options.saveAndFlush(any())).thenAnswer(call->{FeedbackResolutionOption option=call.getArgument(0);option.setOptionId(ids.getAndIncrement());return option;});
        when(actions.save(any())).thenAnswer(call->call.getArgument(0));
        var payout=new ResolutionActionForm("CUSTOMER_PAYOUT","Hoàn trả một phần",null,new BigDecimal("250000"),"Theo hóa đơn đã đối chiếu");
        var work=new ResolutionActionForm("INTERNAL_WORK","Dọn lại khu vực bếp",null,null,null);
        var form=new ResolutionPlanForm("Đã đối chiếu ảnh và trao đổi với Staff",List.of(
                new ResolutionOptionForm("Dọn lại","Công ty quay lại dọn khu vực chưa đạt.",List.of(work)),
                new ResolutionOptionForm("Hoàn một phần","Hoàn 250.000 đồng và kết thúc dịch vụ.",List.of(payout))));

        Long planId=service.createPlan(7L,3L,form);

        assertThat(planId).isEqualTo(100L);
        verify(options,times(2)).saveAndFlush(any());
        verify(actions,times(2)).save(any());
        verify(cases).addAdminMessage(eq(7L),eq(3L),contains("phiên bản 1"),eq(false));
        assertThatThrownBy(()->service.createPlan(7L,3L,new ResolutionPlanForm(null,List.of(
                new ResolutionOptionForm("Không hợp lệ","Thiếu căn cứ",List.of(new ResolutionActionForm("CUSTOMER_PAYOUT","Chi",null,BigDecimal.TEN,null)))))))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("căn cứ tính");
    }

    @Test void customerSelectionIsExactlyOneOptionAndOnlyThenMovesToManager() {
        FeedbackResolutionPlan plan=new FeedbackResolutionPlan(); plan.setPlanId(30L); plan.setCaseId(7L); plan.setStatus("AWAITING_CUSTOMER");
        FeedbackResolutionOption option=new FeedbackResolutionOption(); option.setOptionId(301L); option.setPlanId(30L);
        FeedbackResolutionAction payout=new FeedbackResolutionAction(); payout.setActionType("CUSTOMER_PAYOUT");
        when(cases.customerOwnsCase(7L,9L)).thenReturn(true); when(cases.belongsToBooking(7L,8L)).thenReturn(true);
        when(plans.findByIdForUpdate(30L)).thenReturn(Optional.of(plan)); when(options.findById(301L)).thenReturn(Optional.of(option));
        when(actions.findAllByOptionIdOrderByActionIdAsc(301L)).thenReturn(List.of(payout));

        service.customerChoice(7L,30L,8L,9L,new CustomerResolutionChoiceForm("SELECT",301L,"BANK_TRANSFER","Tôi đồng ý."));

        assertThat(plan.getStatus()).isEqualTo("MANAGER_REVIEW");
        assertThat(plan.getSelectedOptionId()).isEqualTo(301L);
        assertThat(plan.getCustomerPayoutMethod()).isEqualTo("BANK_TRANSFER");
        assertThatThrownBy(()->service.customerChoice(7L,30L,8L,9L,new CustomerResolutionChoiceForm("SELECT",999L,"CASH",null)))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("không còn chờ");
    }

    @Test void managerCannotApproveUnselectedPlanAndCanApproveOnlyAfterCustomerSelection() {
        FeedbackResolutionPlan plan=new FeedbackResolutionPlan(); plan.setPlanId(30L); plan.setCaseId(7L); plan.setStatus("MANAGER_REVIEW"); plan.setSelectedOptionId(301L); plan.setCreatedBy(3L);
        FeedbackResolutionAction work=new FeedbackResolutionAction(); work.setActionId(501L); work.setOptionId(301L); work.setActionType("INTERNAL_WORK"); work.setDescription("Dọn lại");
        when(plans.findByIdForUpdate(30L)).thenReturn(Optional.of(plan)); when(cases.adminCaseById(7L,5L)).thenReturn(incident("VERIFIED"));
        when(actions.findAllByOptionIdOrderByActionIdAsc(301L)).thenReturn(List.of(work));
        when(workOrders.save(any())).thenAnswer(call->call.getArgument(0));

        service.managerDecision(30L,5L,true,null);

        assertThat(plan.getStatus()).isEqualTo("IN_PROGRESS");
        verify(workOrders).save(argThat(order->order.getActionId().equals(501L)&&order.getStatus().equals("OPEN")));
    }

    @Test void customerCannotConfirmUntilApprovedWorkOrderIsCustomerConfirmed() {
        FeedbackResolutionPlan plan=new FeedbackResolutionPlan(); plan.setPlanId(30L); plan.setCaseId(7L); plan.setStatus("IN_PROGRESS"); plan.setSelectedOptionId(301L);
        FeedbackResolutionAction work=new FeedbackResolutionAction(); work.setActionId(501L); work.setActionType("INTERNAL_WORK");
        FeedbackWorkOrder order=new FeedbackWorkOrder(); order.setStatus("COMPLETED");
        when(cases.customerOwnsCase(7L,9L)).thenReturn(true); when(cases.belongsToBooking(7L,8L)).thenReturn(true);
        when(plans.findByIdForUpdate(30L)).thenReturn(Optional.of(plan)); when(actions.findAllByOptionIdOrderByActionIdAsc(301L)).thenReturn(List.of(work));
        when(workOrders.findByActionId(501L)).thenReturn(Optional.of(order));

        assertThatThrownBy(()->service.customerConfirmResolution(7L,30L,8L,9L,true,null))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("xác nhận mọi phiếu công việc");
    }

    private static IncidentCard incident(String status) {
        return new IncidentCard(7L,6L,8L,9L,10L,List.of("CLEANLINESS"),"Mô tả",status,null,null,null,false,
                null,"NOT_PROPOSED",null,null,null,null,null);
    }
}
