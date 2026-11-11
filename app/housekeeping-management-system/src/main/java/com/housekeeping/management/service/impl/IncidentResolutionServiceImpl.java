package com.housekeeping.management.service.impl;

import com.housekeeping.management.dto.request.PortalForms.*;
import com.housekeeping.management.dto.response.PortalViews.*;
import com.housekeeping.management.entity.*;
import com.housekeeping.management.repository.*;
import com.housekeeping.management.service.*;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class IncidentResolutionServiceImpl implements IncidentResolutionService {
    private final FeedbackResolutionPlanRepository plans;
    private final FeedbackResolutionOptionRepository options;
    private final FeedbackResolutionActionRepository actions;
    private final FeedbackWorkOrderRepository workOrders;
    private final BookingFinancialRepository financialEntries;
    private final FeedbackCaseService cases;
    private final BookingFinancialService financials;
    private final AccountAuthorizationService authorization;
    private final StaffManagementService staff;

    public IncidentResolutionServiceImpl(FeedbackResolutionPlanRepository plans, FeedbackResolutionOptionRepository options,
            FeedbackResolutionActionRepository actions, FeedbackWorkOrderRepository workOrders,
            BookingFinancialRepository financialEntries, FeedbackCaseService cases, BookingFinancialService financials,
            AccountAuthorizationService authorization, StaffManagementService staff) {
        this.plans=plans; this.options=options; this.actions=actions; this.workOrders=workOrders;
        this.financialEntries=financialEntries; this.cases=cases; this.financials=financials;
        this.authorization=authorization; this.staff=staff;
    }

    @Override @Transactional(readOnly=true)
    public List<ResolutionPlanCard> customerPlans(Long caseId, Long bookingId, Long customerId) {
        if(!cases.customerOwnsCase(caseId,customerId) || !cases.belongsToBooking(caseId,bookingId))
            throw new AccessDeniedException("Hồ sơ không thuộc booking của bạn.");
        return planCards(caseId);
    }

    @Override @Transactional(readOnly=true)
    public List<ResolutionPlanCard> adminPlans(Long caseId, Long adminId) {
        authorization.requireActiveAdmin(adminId);
        if(cases.adminCaseById(caseId,adminId)==null) throw new IllegalArgumentException("Hồ sơ không tồn tại.");
        return planCards(caseId);
    }

    @Override @Transactional
    public Long createPlan(Long caseId, Long adminId, ResolutionPlanForm form) {
        authorization.requireActiveAdmin(adminId);
        if(authorization.isManager(adminId)) throw new AccessDeniedException("Manager chỉ duyệt phương án đã được khách chọn.");
        if(form==null || form.options()==null || form.options().isEmpty() || form.options().size()>3)
            throw new IllegalArgumentException("Mỗi phiên bản cần từ 1 đến 3 phương án.");
        IncidentCard incident=cases.adminCaseById(caseId,adminId);
        if(incident==null || "RESOLVED".equals(incident.status())) throw new IllegalStateException("Hồ sơ không tồn tại hoặc đã đóng.");
        FeedbackResolutionPlan previous=plans.findFirstByCaseIdOrderByVersionNoDesc(caseId).orElse(null);
        if(previous!=null && !Set.of("NEEDS_REVISION","RETURNED","CUSTOMER_DISPUTED").contains(previous.getStatus()))
            throw new IllegalStateException("Chỉ tạo phiên bản mới khi khách yêu cầu điều chỉnh hoặc Manager trả bổ sung.");
        if(!Set.of("VERIFIED","NOT_SUBSTANTIATED").contains(incident.status()))
            throw new IllegalStateException("Admin cần hoàn tất bước xác minh trước khi gửi phương án.");
        FeedbackResolutionPlan plan=new FeedbackResolutionPlan();
        plan.setCaseId(caseId); plan.setVersionNo(previous==null?1:previous.getVersionNo()+1);
        plan.setStatus("AWAITING_CUSTOMER"); plan.setInternalFinding(clean(form.internalFinding()));
        plan.setCreatedBy(adminId); plan.setCreatedAt(now());
        plan=plans.saveAndFlush(plan);
        int number=0;
        for(ResolutionOptionForm optionForm:form.options()) {
            validateOption(optionForm);
            FeedbackResolutionOption option=new FeedbackResolutionOption(); option.setPlanId(plan.getPlanId());
            option.setOptionNo(++number); option.setTitle(optionForm.title().trim()); option.setDescription(optionForm.description().trim());
            option=options.saveAndFlush(option);
            if(optionForm.actions()!=null) for(ResolutionActionForm actionForm:optionForm.actions()) {
                FeedbackResolutionAction action=new FeedbackResolutionAction(); action.setOptionId(option.getOptionId());
                action.setActionType(actionForm.type()); action.setDescription(actionForm.description().trim());
                action.setDueAt(actionForm.dueAt()); action.setAmount(actionForm.amount());
                action.setCalculationBasis(clean(actionForm.calculationBasis())); actions.save(action);
            }
        }
        cases.addAdminMessage(caseId,adminId,"Công ty đã gửi phương án xử lý phiên bản "+plan.getVersionNo()+". Vui lòng xem và chọn một phương án hoặc yêu cầu trao đổi.",false);
        return plan.getPlanId();
    }

    @Override @Transactional
    public void customerChoice(Long caseId, Long planId, Long bookingId, Long customerId, CustomerResolutionChoiceForm form) {
        if(!cases.customerOwnsCase(caseId,customerId) || !cases.belongsToBooking(caseId,bookingId))
            throw new AccessDeniedException("Hồ sơ không thuộc booking của bạn.");
        FeedbackResolutionPlan plan=plans.findByIdForUpdate(planId).filter(p->p.getCaseId().equals(caseId))
                .orElseThrow(()->new IllegalArgumentException("Phiên bản phương án không tồn tại."));
        if(!"AWAITING_CUSTOMER".equals(plan.getStatus())) throw new IllegalStateException("Phiên bản này không còn chờ khách phản hồi.");
        if("DISCUSS".equals(form.decision())) {
            if(form.message()==null || form.message().isBlank()) throw new IllegalArgumentException("Hãy ghi nội dung cần trao đổi.");
            plan.setCustomerResponse(clean(form.message())); plan.setStatus("NEEDS_REVISION");
            plan.setRespondedAt(now()); plans.save(plan); return;
        }
        FeedbackResolutionOption selected=options.findById(form.optionId()).filter(o->o.getPlanId().equals(planId))
                .orElseThrow(()->new IllegalArgumentException("Hãy chọn một phương án thuộc phiên bản này."));
        boolean payout=actions.findAllByOptionIdOrderByActionIdAsc(selected.getOptionId()).stream()
                .anyMatch(a->"CUSTOMER_PAYOUT".equals(a.getActionType()));
        if(payout && !Set.of("CASH","BANK_TRANSFER").contains(form.payoutMethod()))
            throw new IllegalArgumentException("Chọn tiền mặt hoặc chuyển khoản cho phương án có khoản chi.");
        plan.setSelectedOptionId(selected.getOptionId()); plan.setCustomerPayoutMethod(payout?form.payoutMethod():null);
        plan.setCustomerResponse(clean(form.message())); plan.setRespondedAt(now()); plan.setStatus("MANAGER_REVIEW"); plans.save(plan);
    }

    @Override @Transactional
    public void managerDecision(Long planId, Long managerId, boolean approve, String reason) {
        authorization.requireManager(managerId);
        FeedbackResolutionPlan plan=plans.findByIdForUpdate(planId).orElseThrow(()->new IllegalArgumentException("Phương án không tồn tại."));
        if(!"MANAGER_REVIEW".equals(plan.getStatus()) || plan.getSelectedOptionId()==null)
            throw new IllegalStateException("Chỉ duyệt phương án khách đã chọn.");
        if(!approve && (reason==null || reason.isBlank())) throw new IllegalArgumentException("Ghi lý do trả Admin bổ sung.");
        plan.setManagerId(managerId); plan.setManagerDecisionAt(now()); plan.setManagerReason(clean(reason));
        if(!approve) { plan.setStatus("RETURNED"); plans.save(plan); return; }
        IncidentCard incident=cases.adminCaseById(plan.getCaseId(),managerId);
        if(incident==null) throw new IllegalStateException("Không đọc được booking của hồ sơ.");
        List<FeedbackResolutionAction> selectedActions=actions.findAllByOptionIdOrderByActionIdAsc(plan.getSelectedOptionId());
        for(FeedbackResolutionAction action:selectedActions) {
            if("CUSTOMER_PAYOUT".equals(action.getActionType())) {
                financials.recordManagerApprovedResolutionPayout(incident.bookingId(),plan.getCaseId(),action.getActionId(),
                        action.getAmount(),action.getDescription()+". Căn cứ: "+Objects.toString(action.getCalculationBasis(),"đã được Manager duyệt"),
                        plan.getCreatedBy(),managerId,plan.getCustomerPayoutMethod());
            } else {
                FeedbackWorkOrder order=new FeedbackWorkOrder(); order.setCaseId(plan.getCaseId()); order.setActionId(action.getActionId());
                order.setScope(action.getDescription()); order.setStatus("OPEN"); order.setCreatedBy(plan.getCreatedBy()); order.setCreatedAt(now());
                workOrders.save(order);
            }
        }
        plan.setStatus("IN_PROGRESS"); plans.save(plan);
    }

    @Override @Transactional(readOnly=true)
    public List<WorkOrderCard> workOrders(Long caseId, Long adminId, boolean customer, Long actorId) {
        if(customer) { if(!cases.customerOwnsCase(caseId,actorId)) throw new AccessDeniedException("Hồ sơ không thuộc bạn."); }
        else authorization.requireActiveAdmin(adminId);
        return workOrders.findAllByCaseIdOrderByWorkOrderIdAsc(caseId).stream().map(IncidentResolutionServiceImpl::workCard).toList();
    }

    @Override @Transactional
    public void scheduleWorkOrder(Long workOrderId, Long adminId, WorkOrderForm form) {
        authorization.requireActiveAdmin(adminId);
        if(authorization.isManager(adminId)) throw new AccessDeniedException("Admin phụ trách tổ chức thực hiện công việc.");
        FeedbackWorkOrder order=workOrders.findFirstByWorkOrderId(workOrderId).orElseThrow(()->new IllegalArgumentException("Phiếu không tồn tại."));
        if(!"OPEN".equals(order.getStatus()) && !"SCHEDULED".equals(order.getStatus())) throw new IllegalStateException("Phiếu đã hoàn tất hoặc có phản hồi của khách.");
        if(form.assignedStaffId()!=null) staff.findActiveByStaffId(form.assignedStaffId());
        order.setAssignedStaffId(form.assignedStaffId()); order.setScheduledAt(form.scheduledAt()); order.setScope(form.scope().trim());
        order.setEstimatedCost(form.estimatedCost()); order.setEstimatedMaterials(clean(form.estimatedMaterials())); order.setStatus("SCHEDULED");
        workOrders.save(order);
    }

    @Override @Transactional
    public void completeWorkOrder(Long workOrderId, Long adminId, WorkOrderCompletionForm form) {
        authorization.requireActiveAdmin(adminId);
        FeedbackWorkOrder order=workOrders.findFirstByWorkOrderId(workOrderId).orElseThrow(()->new IllegalArgumentException("Phiếu không tồn tại."));
        if(!Set.of("OPEN","SCHEDULED").contains(order.getStatus())) throw new IllegalStateException("Phiếu không ở trạng thái có thể cập nhật.");
        order.setActualCost(form.actualCost()); order.setActualMaterials(clean(form.actualMaterials()));
        order.setCompletionEvidence(form.evidence().trim()); order.setCompletedAt(now()); order.setStatus("COMPLETED"); workOrders.save(order);
    }

    @Override @Transactional
    public void customerWorkOrderResponse(Long workOrderId, Long bookingId, Long customerId, CustomerWorkOrderResponseForm form) {
        FeedbackWorkOrder order=workOrders.findFirstByWorkOrderId(workOrderId).orElseThrow(()->new IllegalArgumentException("Phiếu không tồn tại."));
        if(!cases.customerOwnsCase(order.getCaseId(),customerId) || !cases.belongsToBooking(order.getCaseId(),bookingId))
            throw new AccessDeniedException("Booking không thuộc tài khoản của bạn.");
        if(!"COMPLETED".equals(order.getStatus())) throw new IllegalStateException("Công việc chưa được Admin ghi nhận hoàn tất.");
        order.setCustomerConfirmation(form.decision()); order.setCustomerFeedback(clean(form.message()));
        order.setStatus("CONFIRMED".equals(form.decision())?"CUSTOMER_CONFIRMED":"DISPUTED"); workOrders.save(order);
        if("DISPUTED".equals(order.getStatus())) {
            plans.findByIdForUpdate(plans.findFirstByCaseIdOrderByVersionNoDesc(order.getCaseId()).orElseThrow().getPlanId())
                    .ifPresent(plan->{plan.setStatus("CUSTOMER_DISPUTED"); plan.setCompletionFeedback(clean(form.message())); plans.save(plan);});
        }
    }

    @Override @Transactional
    public void customerConfirmResolution(Long caseId, Long planId, Long bookingId, Long customerId, boolean confirm, String message) {
        if(!cases.customerOwnsCase(caseId,customerId) || !cases.belongsToBooking(caseId,bookingId))
            throw new AccessDeniedException("Hồ sơ không thuộc booking của bạn.");
        FeedbackResolutionPlan plan=plans.findByIdForUpdate(planId).filter(p->p.getCaseId().equals(caseId))
                .orElseThrow(()->new IllegalArgumentException("Phương án không tồn tại."));
        if(!"IN_PROGRESS".equals(plan.getStatus()) && !"AWAITING_CUSTOMER_CONFIRMATION".equals(plan.getStatus()))
            throw new IllegalStateException("Hồ sơ chưa ở bước xác nhận kết quả.");
        List<FeedbackResolutionAction> selected=actions.findAllByOptionIdOrderByActionIdAsc(plan.getSelectedOptionId());
        for(FeedbackResolutionAction action:selected) {
            if("INTERNAL_WORK".equals(action.getActionType())) {
                FeedbackWorkOrder order=workOrders.findByActionId(action.getActionId()).orElseThrow(()->new IllegalStateException("Thiếu phiếu thực hiện đã duyệt."));
                if(!"CUSTOMER_CONFIRMED".equals(order.getStatus())) throw new IllegalStateException("Cần hoàn tất và được khách xác nhận mọi phiếu công việc trước khi đóng hồ sơ.");
            } else {
                BookingFinancialEntry entry=financialEntries.findFirstByResolutionActionId(action.getActionId()).orElseThrow(()->new IllegalStateException("Thiếu khoản chi đã được duyệt."));
                if(!"PAID".equals(entry.getStatus())) throw new IllegalStateException("Khoản tiền phải được Admin quyết toán trước khi xác nhận hoàn tất.");
            }
        }
        if(!confirm && (message==null||message.isBlank())) throw new IllegalArgumentException("Hãy mô tả nội dung chưa được giải quyết.");
        plan.setCompletionFeedback(clean(message)); plan.setCustomerCompletedAt(now());
        if(!confirm) { plan.setStatus("CUSTOMER_DISPUTED"); plans.save(plan); return; }
        plan.setStatus("COMPLETED"); plans.save(plan);
        cases.resolveAfterCustomerConfirmation(caseId,customerId);
    }

    private List<ResolutionPlanCard> planCards(Long caseId) {
        return plans.findAllByCaseIdOrderByVersionNoDesc(caseId).stream().map(plan -> {
            List<ResolutionOptionCard> optionCards=options.findAllByPlanIdOrderByOptionNoAsc(plan.getPlanId()).stream().map(option -> {
                List<ResolutionActionCard> actionCards=actions.findAllByOptionIdOrderByActionIdAsc(option.getOptionId()).stream().map(action ->
                        new ResolutionActionCard(action.getActionId(),action.getActionType(),action.getDescription(),action.getDueAt(),action.getAmount(),
                                action.getCalculationBasis(),financialEntries.findFirstByResolutionActionId(action.getActionId()).map(BookingFinancialEntry::getEntryId).orElse(null))).toList();
                return new ResolutionOptionCard(option.getOptionId(),option.getOptionNo(),option.getTitle(),option.getDescription(),actionCards);
            }).toList();
            return new ResolutionPlanCard(plan.getPlanId(),plan.getCaseId(),plan.getVersionNo(),plan.getStatus(),plan.getInternalFinding(),
                    plan.getCustomerResponse(),plan.getCompletionFeedback(),plan.getCustomerCompletedAt(),plan.getSelectedOptionId(),plan.getCustomerPayoutMethod(),plan.getCreatedBy(),plan.getCreatedAt(),
                    plan.getManagerId(),plan.getManagerReason(),optionCards);
        }).toList();
    }
    private static WorkOrderCard workCard(FeedbackWorkOrder e) { return new WorkOrderCard(e.getWorkOrderId(),e.getCaseId(),e.getActionId(),
            e.getAssignedStaffId(),e.getScheduledAt(),e.getScope(),e.getEstimatedCost(),e.getEstimatedMaterials(),e.getActualCost(),
            e.getActualMaterials(),e.getCompletionEvidence(),e.getStatus(),e.getCustomerConfirmation(),e.getCustomerFeedback()); }
    private static void validateOption(ResolutionOptionForm option) {
        if(option==null || option.title()==null || option.title().isBlank() || option.description()==null || option.description().isBlank())
            throw new IllegalArgumentException("Mỗi phương án cần tên và mô tả rõ ràng.");
        if(option.actions()!=null) for(ResolutionActionForm action:option.actions()) {
            if(action==null || !Set.of("INTERNAL_WORK","CUSTOMER_PAYOUT").contains(action.type()) || action.description()==null || action.description().isBlank())
                throw new IllegalArgumentException("Mỗi bước xử lý phải có loại và mô tả.");
            if("CUSTOMER_PAYOUT".equals(action.type()) && (action.amount()==null || action.amount().signum()<=0 || action.calculationBasis()==null || action.calculationBasis().isBlank()))
                throw new IllegalArgumentException("Khoản chi cần số tiền và căn cứ tính rõ ràng.");
            if("INTERNAL_WORK".equals(action.type()) && action.amount()!=null) throw new IllegalArgumentException("Chi phí nội bộ được ghi trong phiếu thực hiện, không phải khoản chi cho khách.");
        }
    }
    private static String clean(String value) { return value==null || value.isBlank()?null:value.trim(); }
    private static LocalDateTime now() { return LocalDateTime.now(ZoneOffset.ofHours(7)).withNano(0); }
}
