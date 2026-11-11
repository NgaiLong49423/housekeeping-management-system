package com.housekeeping.management.service;

import com.housekeeping.management.dto.request.PortalForms.ResolutionPlanForm;
import com.housekeeping.management.dto.request.PortalForms.CustomerResolutionChoiceForm;
import com.housekeeping.management.dto.request.PortalForms.WorkOrderForm;
import com.housekeeping.management.dto.request.PortalForms.WorkOrderCompletionForm;
import com.housekeeping.management.dto.request.PortalForms.CustomerWorkOrderResponseForm;
import com.housekeeping.management.dto.response.PortalViews.ResolutionPlanCard;
import com.housekeeping.management.dto.response.PortalViews.WorkOrderCard;
import java.util.List;

public interface IncidentResolutionService {
    List<ResolutionPlanCard> customerPlans(Long caseId, Long bookingId, Long customerId);
    List<ResolutionPlanCard> adminPlans(Long caseId, Long adminId);
    Long createPlan(Long caseId, Long adminId, ResolutionPlanForm form);
    void customerChoice(Long caseId, Long planId, Long bookingId, Long customerId, CustomerResolutionChoiceForm form);
    void managerDecision(Long planId, Long managerId, boolean approve, String reason);
    List<WorkOrderCard> workOrders(Long caseId, Long adminId, boolean customer, Long actorId);
    void scheduleWorkOrder(Long workOrderId, Long adminId, WorkOrderForm form);
    void completeWorkOrder(Long workOrderId, Long adminId, WorkOrderCompletionForm form);
    void customerWorkOrderResponse(Long workOrderId, Long bookingId, Long customerId, CustomerWorkOrderResponseForm form);
    void customerConfirmResolution(Long caseId, Long planId, Long bookingId, Long customerId, boolean confirm, String message);
}
