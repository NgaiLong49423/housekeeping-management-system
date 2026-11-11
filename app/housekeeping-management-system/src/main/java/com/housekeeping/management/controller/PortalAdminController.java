package com.housekeeping.management.controller;

import static com.housekeeping.management.dto.request.PortalForms.*;
import com.housekeeping.management.dto.response.PortalViews;
import com.housekeeping.management.service.FeedbackService;
import com.housekeeping.management.service.FeedbackCaseService;
import com.housekeeping.management.service.BookingFinancialService;
import com.housekeeping.management.service.CaseAttachmentStorageService;
import com.housekeeping.management.service.PortalQueryService;
import com.housekeeping.management.service.AdminBookingManagementService;
import com.housekeeping.management.service.ServiceManagementService;
import com.housekeeping.management.service.StaffManagementService;
import com.housekeeping.management.service.IncidentResolutionService;
import com.housekeeping.management.web.PortalUser;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/admin")
public class PortalAdminController {
    private final PortalQueryService query;
    private final ServiceManagementService serviceManagement;
    private final StaffManagementService staffManagement;
    private final FeedbackService feedbackService;
    private final FeedbackCaseService feedbackCases;
    private final BookingFinancialService financials;
    private final CaseAttachmentStorageService attachments;
    private final AdminBookingManagementService bookingManagement;
    private final IncidentResolutionService resolutions;
    public PortalAdminController(PortalQueryService query, ServiceManagementService serviceManagement,
            StaffManagementService staffManagement, FeedbackService feedbackService, FeedbackCaseService feedbackCases, BookingFinancialService financials, CaseAttachmentStorageService attachments,
            AdminBookingManagementService bookingManagement, IncidentResolutionService resolutions) {
        this.query = query; this.serviceManagement = serviceManagement; this.staffManagement = staffManagement;
        this.feedbackService = feedbackService; this.feedbackCases = feedbackCases; this.financials = financials; this.attachments=attachments; this.bookingManagement = bookingManagement; this.resolutions=resolutions;
    }

    @GetMapping("/services")
    String services(Model model, @AuthenticationPrincipal PortalUser user) { model.addAttribute("services", query.services(user)); return "admin/services"; }

    @GetMapping("/staff")
    String staff(@AuthenticationPrincipal PortalUser user, Model model) { model.addAttribute("staff", query.staff(user)); return "admin/staff"; }

    @GetMapping("/schedules")
    String schedules(@AuthenticationPrincipal PortalUser user, Model model) {
        model.addAttribute("schedules", query.schedules(user)); return "admin/schedules";
    }

    @GetMapping("/feedback")
    String feedback(@RequestParam(defaultValue="all") String status, @RequestParam(required=false) Long bookingId,
            Model model, @AuthenticationPrincipal PortalUser user) {
        var allFeedback=query.feedback();
        var allCases=allFeedback.stream().map(item -> feedbackCases.adminCase(item.id(), user.getUserId()))
                .filter(java.util.Objects::nonNull).toList();
        var financialEntriesByCase=allCases.stream().collect(java.util.stream.Collectors.toMap(
                com.housekeeping.management.dto.response.PortalViews.IncidentCard::id,
                item -> financials.forBooking(item.bookingId(),user.getUserId()).stream()
                        .filter(entry -> item.id().equals(entry.caseId())).toList()));
        var casesByFeedback=allCases.stream().collect(java.util.stream.Collectors.toMap(
                com.housekeeping.management.dto.response.PortalViews.IncidentCard::feedbackId, item -> item));
        var queueStatusByFeedback=allFeedback.stream().collect(java.util.stream.Collectors.toMap(
                com.housekeeping.management.dto.response.PortalViews.FeedbackCard::id, item -> {
                    var incident=casesByFeedback.get(item.id());
                    boolean awaitingApproval=incident!=null && ("PENDING".equals(incident.staffActionStatus())
                            || financialEntriesByCase.getOrDefault(incident.id(), java.util.List.of()).stream()
                                    .anyMatch(entry -> java.util.Set.of("PROPOSED","PENDING_APPROVAL").contains(entry.status())));
                    if (awaitingApproval) return "pending";
                    if (incident!=null ? "RESOLVED".equals(incident.status()) : "RESOLVED".equals(item.status())) return "resolved";
                    return "processing";
                }));
        String selectedStatus=java.util.Set.of("all","processing","pending","resolved").contains(status) ? status : "all";
        var feedbackItems=allFeedback.stream()
                .filter(item -> bookingId==null || bookingId.equals(item.bookingId()))
                .filter(item -> "all".equals(selectedStatus) || selectedStatus.equals(queueStatusByFeedback.get(item.id())))
                .toList();
        model.addAttribute("feedback", feedbackItems);
        model.addAttribute("queueStatusByFeedback", queueStatusByFeedback);
        model.addAttribute("selectedStatus", selectedStatus);
        model.addAttribute("selectedBookingId", bookingId);
        model.addAttribute("feedbackCount", allFeedback.size());
        model.addAttribute("processingCount", queueStatusByFeedback.values().stream().filter("processing"::equals).count());
        model.addAttribute("pendingCount", queueStatusByFeedback.values().stream().filter("pending"::equals).count());
        model.addAttribute("resolvedCount", queueStatusByFeedback.values().stream().filter("resolved"::equals).count());
        var cases=feedbackItems.stream().map(item -> casesByFeedback.get(item.id()))
                .filter(java.util.Objects::nonNull).toList();
        model.addAttribute("incidentByFeedback", cases.stream().collect(java.util.stream.Collectors.toMap(
                com.housekeeping.management.dto.response.PortalViews.IncidentCard::feedbackId, item -> item)));
        return "admin/feedback";
    }

    @GetMapping("/feedback/booking/{bookingId}")
    String feedbackForBooking(@PathVariable Long bookingId) {
        var item=query.feedback().stream().filter(feedback -> bookingId.equals(feedback.bookingId())).findFirst()
                .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND));
        return "redirect:/admin/feedback/" + item.id();
    }

    @GetMapping("/feedback/{feedbackId}")
    String feedbackDetail(@PathVariable Long feedbackId, Model model, @AuthenticationPrincipal PortalUser user) {
        var item=query.feedback().stream().filter(feedback -> feedbackId.equals(feedback.id())).findFirst()
                .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND));
        var incident=feedbackCases.adminCase(feedbackId,user.getUserId());
        return renderFeedbackDetail(item,incident,model,user);
    }

    @GetMapping("/feedback/cases/{caseId}")
    String feedbackCaseDetail(@PathVariable Long caseId, Model model, @AuthenticationPrincipal PortalUser user) {
        var incident=feedbackCases.adminCaseById(caseId,user.getUserId());
        if(incident==null) throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND);
        var item=query.feedback().stream().filter(feedback -> incident.feedbackId().equals(feedback.id())).findFirst()
                .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND));
        return renderFeedbackDetail(item,incident,model,user);
    }

    private String renderFeedbackDetail(PortalViews.FeedbackCard item, PortalViews.IncidentCard incident,
            Model model, PortalUser user) {
        model.addAttribute("feedbackItem",item);
        model.addAttribute("incident",incident);
        model.addAttribute("assignableStaff",query.staff(user));
        if(incident!=null) {
            var entries=financials.forBooking(incident.bookingId(),user.getUserId()).stream()
                    .filter(entry -> incident.id().equals(entry.caseId())).toList();
            var payoutDetails=entries.stream().filter(entry -> "COMPENSATION".equals(entry.type()))
                    .map(entry -> financials.adminPayoutDetails(entry.id(),user.getUserId()))
                    .flatMap(java.util.Optional::stream)
                    .collect(java.util.stream.Collectors.toMap(PortalViews.AdminPayoutDetails::entryId, detail -> detail));
            boolean replied=item.adminReply()!=null && !item.adminReply().isBlank();
            boolean remedyDone=incident.remedyType()==null || incident.remedyCompleted();
            boolean noPendingEntries=entries.stream().noneMatch(entry -> java.util.Set.of("PROPOSED","PENDING_APPROVAL","APPROVED").contains(entry.status()));
            boolean requiredPaymentSettled=switch (incident.remedyType()==null?"":incident.remedyType()) {
                case "REFUND" -> entries.stream().anyMatch(entry -> "REFUND".equals(entry.type()) && java.util.Set.of("PAID","SIMULATED").contains(entry.status()));
                case "PRICE_REDUCTION" -> entries.stream().anyMatch(entry -> "PRICE_REDUCTION".equals(entry.type()) && java.util.Set.of("PAID","SIMULATED").contains(entry.status()));
                case "COMPENSATION" -> entries.stream().anyMatch(entry -> "COMPENSATION".equals(entry.type())
                        && "PAID".equals(entry.status())
                        && payoutDetails.containsKey(entry.id())
                        && "ACCEPTED".equals(payoutDetails.get(entry.id()).responseStatus()));
                default -> true;
            };
            boolean canResolve=!incident.resolutionWorkflowRequired() && java.util.Set.of("VERIFIED","NOT_SUBSTANTIATED").contains(incident.status()) && replied
                    && remedyDone && requiredPaymentSettled && noPendingEntries && !"PENDING".equals(incident.staffActionStatus());
            boolean compensationExists=entries.stream().anyMatch(entry -> "COMPENSATION".equals(entry.type())
                    && java.util.Set.of("PROPOSED","PENDING_APPROVAL","APPROVED","PAID","SIMULATED").contains(entry.status()));
            boolean verifiedForRemedy=java.util.Set.of("VERIFIED","NOT_SUBSTANTIATED").contains(incident.status());
            boolean canMarkRemedyCompleted=!incident.resolutionWorkflowRequired() && verifiedForRemedy && incident.remedyType()!=null
                    && (!"COMPENSATION".equals(incident.remedyType()) || requiredPaymentSettled);
            boolean remedyCompletedForDisplay=incident.remedyCompleted() && canMarkRemedyCompleted;
            model.addAttribute("feedbackById",java.util.Map.of(item.id(),item));
            model.addAttribute("caseMessages",java.util.Map.of(incident.id(),feedbackCases.adminMessages(incident.id(),user.getUserId())));
            model.addAttribute("verificationHistory",feedbackCases.verificationHistory(incident.id(),user.getUserId()));
            var resolutionPlans=resolutions.adminPlans(incident.id(),user.getUserId());
            model.addAttribute("resolutionPlans",resolutionPlans);
            model.addAttribute("resolutionWorkflowRequired",incident.resolutionWorkflowRequired());
            model.addAttribute("workOrders",resolutions.workOrders(incident.id(),user.getUserId(),false,user.getUserId()));
            model.addAttribute("caseFinancialEntries",java.util.Map.of(incident.id(),entries));
            model.addAttribute("adminPayoutDetails",payoutDetails);
            model.addAttribute("staffActionHistory",java.util.Map.of(incident.id(),feedbackCases.actions(incident.id(),user.getUserId())));
            model.addAttribute("caseCanResolve",java.util.Map.of(incident.id(),canResolve));
            model.addAttribute("caseCanMarkRemedyCompleted",java.util.Map.of(incident.id(),canMarkRemedyCompleted));
            model.addAttribute("caseRemedyCompleted",java.util.Map.of(incident.id(),remedyCompletedForDisplay));
            model.addAttribute("canProposeCompensation",java.util.Map.of(incident.id(),
                    !incident.resolutionWorkflowRequired() && "VERIFIED".equals(incident.status()) && "COMPENSATION".equals(incident.remedyType()) && !compensationExists));
        }
        return "admin/feedback-detail";
    }

    @GetMapping("/finance")
    String finance(Model model, @AuthenticationPrincipal PortalUser user) {
        model.addAttribute("summary", financials.summary(user.getUserId()));
        var entries=financials.all(user.getUserId());
        model.addAttribute("pendingApprovalCount",entries.stream().filter(entry -> "PENDING_APPROVAL".equals(entry.status())).count());
        model.addAttribute("awaitingSettlementCount",entries.stream().filter(entry -> "APPROVED".equals(entry.status())).count());
        return "admin/finance";
    }

    @GetMapping("/finance/ledger")
    String financeLedger(Model model, @AuthenticationPrincipal PortalUser user) {
        model.addAttribute("ledger", financials.ledger(user.getUserId()));
        return "admin/finance-ledger";
    }

    @GetMapping("/finance/entries")
    String financeEntries(Model model, @AuthenticationPrincipal PortalUser user) {
        model.addAttribute("entries", financials.all(user.getUserId()));
        return "admin/finance-entries";
    }

    @GetMapping("/finance/entries/{id}")
    String financeEntryDetail(@PathVariable Long id, Model model, @AuthenticationPrincipal PortalUser user) {
        var entry=financials.entry(id,user.getUserId()).orElseThrow(() ->
                new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND));
        model.addAttribute("financialEntry",entry);
        if (entry.caseId() != null)
            model.addAttribute("incident",feedbackCases.adminCaseById(entry.caseId(),user.getUserId()));
        if ("COMPENSATION".equals(entry.type()))
            model.addAttribute("payoutDetails",financials.adminPayoutDetails(id,user.getUserId()).orElse(null));
        return "admin/finance-entry-detail";
    }

    @GetMapping("/finance/new")
    String newFinancialEntry(@RequestParam(required=false) Long bookingId, @RequestParam(required=false) Long caseId,
            Model model, @AuthenticationPrincipal PortalUser user) {
        model.addAttribute("bookingOptions",query.bookings(user));
        var caseOptions=query.feedback().stream().map(feedback -> feedbackCases.adminCase(feedback.id(),user.getUserId()))
                .filter(java.util.Objects::nonNull).toList();
        model.addAttribute("caseOptions",caseOptions);
        model.addAttribute("selectedBookingId",bookingId);
        model.addAttribute("selectedCaseId",caseId);
        model.addAttribute("selectedIncident",caseOptions.stream()
                .filter(incident -> caseId != null && caseId.equals(incident.id())).findFirst().orElse(null));
        return "admin/finance-new";
    }

    @PostMapping("/financial-entries")
    String proposeFinancialEntry(@RequestParam Long bookingId, @RequestParam(required=false) Long caseId,
            @Valid @ModelAttribute FinancialEntryForm form, BindingResult errors,
            @AuthenticationPrincipal PortalUser user, RedirectAttributes flash,
            @RequestParam(required=false) Long returnCaseId) {
        try {
            if (errors.hasErrors()) throw new IllegalArgumentException("Dữ liệu khoản tiền không hợp lệ.");
            financials.propose(bookingId,caseId,user.getUserId(),form);
            flash.addFlashAttribute("success","Đã lập khoản thu/chi và gửi chờ quản lý duyệt.");
        } catch (IllegalArgumentException | IllegalStateException failure) { flash.addFlashAttribute("error",failure.getMessage()); }
        Long detailCaseId=returnCaseId==null?caseId:returnCaseId;
        return detailCaseId==null?"redirect:/admin/finance/entries":"redirect:/admin/feedback/cases/"+detailCaseId;
    }

    @PostMapping("/financial-entries/{id}/approve")
    String approveFinancialEntry(@PathVariable Long id, @RequestParam(required=false) Long returnCaseId,
            @RequestParam(required=false) Long returnEntryId,
            @AuthenticationPrincipal PortalUser user, RedirectAttributes flash) {
        try { financials.approve(id,user.getUserId()); flash.addFlashAttribute("success","Đã duyệt khoản tiền."); }
        catch (IllegalArgumentException | IllegalStateException failure) { flash.addFlashAttribute("error",failure.getMessage()); }
        return financialRedirect(returnCaseId,returnEntryId);
    }

    @PostMapping("/feedback/cases/{id}/messages")
    String sendFeedbackCaseMessage(@PathVariable Long id,
            @RequestParam(required=false) String message,
            @RequestParam(required=false) String driveLink,
            @RequestParam(required=false) java.util.List<org.springframework.web.multipart.MultipartFile> images,
            @AuthenticationPrincipal PortalUser user, RedirectAttributes flash) {
        Long messageId=null;
        try {
            var files=images==null?java.util.List.<org.springframework.web.multipart.MultipartFile>of():images.stream()
                    .filter(file->file!=null&&!file.isEmpty()).toList();
            if(files.size()+(driveLink!=null&&!driveLink.isBlank()?1:0)>5) throw new IllegalArgumentException("Mỗi tin nhắn tối đa 5 tệp/liên kết bằng chứng.");
            messageId=feedbackCases.addAdminMessage(id,user.getUserId(),message,!files.isEmpty() || (driveLink!=null&&!driveLink.isBlank()));
            if(!files.isEmpty()) attachments.uploadAdminMessageEvidence(id,user.getUserId(),messageId,files);
            if(driveLink!=null&&!driveLink.isBlank()) attachments.addAdminDriveEvidence(id,user.getUserId(),messageId,driveLink);
            feedbackCases.confirmAdminMessage(id,messageId,user.getUserId());
            flash.addFlashAttribute("success","Đã gửi tin nhắn vào hồ sơ sự cố.");
        } catch (IllegalArgumentException | IllegalStateException failure) {
            if(messageId!=null) feedbackCases.discardUnsentMessage(id,messageId,user.getUserId(),true);
            flash.addFlashAttribute("error",failure.getMessage());
        }
        return "redirect:/admin/feedback/cases/"+id;
    }

    @PostMapping("/financial-entries/{id}/reject")
    String rejectFinancialEntry(@PathVariable Long id, @RequestParam String reason, @RequestParam(required=false) Long returnCaseId,
            @RequestParam(required=false) Long returnEntryId,
            @AuthenticationPrincipal PortalUser user, RedirectAttributes flash) {
        try { financials.reject(id,user.getUserId(),reason); flash.addFlashAttribute("success","Đã từ chối khoản tiền với lý do được lưu."); }
        catch (IllegalArgumentException | IllegalStateException failure) { flash.addFlashAttribute("error",failure.getMessage()); }
        return financialRedirect(returnCaseId,returnEntryId);
    }

    @PostMapping("/financial-entries/{id}/cancel")
    String cancelFinancialEntry(@PathVariable Long id, @RequestParam String reason,
            @RequestParam(required=false) Long returnCaseId,
            @RequestParam(required=false) Long returnEntryId,
            @AuthenticationPrincipal PortalUser user, RedirectAttributes flash) {
        try { financials.cancel(id,user.getUserId(),reason); flash.addFlashAttribute("success","Đã hủy khoản chờ xử lý."); }
        catch (IllegalArgumentException | IllegalStateException failure) { flash.addFlashAttribute("error",failure.getMessage()); }
        return financialRedirect(returnCaseId,returnEntryId);
    }

    @PostMapping("/financial-entries/{id}/settle")
    String settleFinancialEntry(@PathVariable Long id, @RequestParam String method,
            @RequestParam String reference, @RequestParam(defaultValue="false") boolean simulated,
            @AuthenticationPrincipal PortalUser user, RedirectAttributes flash,
            @RequestParam(required=false) Long returnCaseId, @RequestParam(required=false) Long returnEntryId) {
        try { financials.settle(id,user.getUserId(),method,reference,simulated); flash.addFlashAttribute("success","Đã ghi nhận khoản tiền đã chi/hoàn."); }
        catch (IllegalArgumentException | IllegalStateException failure) { flash.addFlashAttribute("error",failure.getMessage()); }
        return financialRedirect(returnCaseId,returnEntryId);
    }

    @PostMapping("/financial-entries/{id}/receipt")
    String uploadReceipt(@PathVariable Long id, @RequestParam org.springframework.web.multipart.MultipartFile file,
            @AuthenticationPrincipal PortalUser user, RedirectAttributes flash,
            @RequestParam(required=false) Long returnCaseId, @RequestParam(required=false) Long returnEntryId) {
        try { attachments.uploadReceipt(id,user.getUserId(),file); flash.addFlashAttribute("success","Đã lưu hóa đơn/biên nhận cho khoản tiền."); }
        catch (IllegalArgumentException | IllegalStateException failure) { flash.addFlashAttribute("error",failure.getMessage()); }
        return financialRedirect(returnCaseId,returnEntryId);
    }

    private String financialRedirect(Long caseId, Long entryId) {
        if (caseId != null) return "redirect:/admin/feedback/cases/" + caseId;
        if (entryId != null) return "redirect:/admin/finance/entries/" + entryId;
        return "redirect:/admin/finance/entries";
    }

    @PostMapping("/bookings/{id}/cancel")
    String cancelBooking(@PathVariable Long id, @AuthenticationPrincipal PortalUser user,
            @Valid @ModelAttribute AdminBookingCancellationForm form, BindingResult errors, RedirectAttributes flash) {
        if (errors.hasErrors()) flash.addFlashAttribute("error", "Lý do hủy không được để trống và tối đa 500 ký tự.");
        else try {
            bookingManagement.cancelConfirmedBooking(id, user.getUserId(), form.reason());
            flash.addFlashAttribute("success", "Admin đã hủy lịch CONFIRMED; lịch sử và snapshot được giữ nguyên.");
        } catch (IllegalArgumentException | IllegalStateException failure) {
            flash.addFlashAttribute("error", failure.getMessage());
        }
        return "redirect:/admin/bookings/" + id;
    }

    @PostMapping("/bookings/{id}/reassign")
    String reassignBooking(@PathVariable Long id, @AuthenticationPrincipal PortalUser user,
            @Valid @ModelAttribute BookingReassignmentForm form, BindingResult errors, RedirectAttributes flash) {
        if (errors.hasErrors()) flash.addFlashAttribute("error", "Hãy chọn nhân viên hợp lệ.");
        else try {
            bookingManagement.reassignConfirmedBooking(id, user.getUserId(), form.staffId());
            flash.addFlashAttribute("success", "Đã cập nhật nhân viên phụ trách; nhân viên ban đầu được giữ nguyên trong lịch sử.");
        } catch (IllegalArgumentException | IllegalStateException failure) {
            flash.addFlashAttribute("error", failure.getMessage());
        }
        return "redirect:/admin/bookings/" + id;
    }

    @PostMapping("/feedback/{id}/reply")
    String reply(@PathVariable Long id, @AuthenticationPrincipal PortalUser user,
            @Valid @ModelAttribute FeedbackReplyForm form,
            BindingResult errors, RedirectAttributes flash) {
        if (!errors.hasErrors()) {
            var incident=feedbackCases.adminCase(id,user.getUserId());
            if(incident!=null) feedbackCases.addAdminMessage(incident.id(),user.getUserId(),form.reply(),false);
            else feedbackService.reply(id, user.getUserId(), form.reply());
            flash.addFlashAttribute("success", "Đã lưu phản hồi cho khách hàng.");
        } else {
            flash.addFlashAttribute("error", feedbackValidationMessage(errors, "Không thể lưu phản hồi khách hàng."));
        }
        return "redirect:/admin/feedback/"+id;
    }

    @PostMapping("/feedback/{id}/resolve")
    String resolve(@PathVariable Long id, @AuthenticationPrincipal PortalUser user, RedirectAttributes flash) {
        try {
            if(feedbackCases.adminCase(id,user.getUserId())!=null)
                throw new IllegalStateException("Hãy giải quyết hồ sơ sự cố bằng nút đóng hồ sơ để kiểm tra đủ điều kiện.");
            feedbackService.resolve(id, user.getUserId());
            flash.addFlashAttribute("success", "Đã đánh dấu phản hồi là đã giải quyết.");
        } catch (IllegalArgumentException | IllegalStateException failure) { flash.addFlashAttribute("error",failure.getMessage()); }
        return "redirect:/admin/feedback/"+id;
    }

    @PostMapping("/feedback/cases/{id}/review")
    String reviewFeedbackCase(@PathVariable Long id, @AuthenticationPrincipal PortalUser user,
            @Valid @ModelAttribute IncidentReviewForm form, BindingResult errors, RedirectAttributes flash) {
        if (!errors.hasErrors()) try {
            feedbackCases.review(id, user.getUserId(), form);
            flash.addFlashAttribute("success", "Đã lưu kết quả xác minh và phương án xử lý sự cố.");
        } catch (IllegalArgumentException | IllegalStateException failure) {
            flash.addFlashAttribute("error", failure.getMessage());
        } else {
            flash.addFlashAttribute("error", feedbackValidationMessage(errors, "Không thể lưu kết quả xác minh."));
        }
        return "redirect:/admin/feedback/cases/"+id;
    }

    @PostMapping("/feedback/cases/{id}/resolution-plans")
    String createResolutionPlan(@PathVariable Long id, @Valid @ModelAttribute ResolutionPlanForm form,
            BindingResult errors, @AuthenticationPrincipal PortalUser user, RedirectAttributes flash) {
        try {
            if(errors.hasErrors()) throw new IllegalArgumentException("Hãy nhập đầy đủ từng phương án, bước xử lý và căn cứ khoản tiền.");
            resolutions.createPlan(id,user.getUserId(),form);
            flash.addFlashAttribute("success","Đã gửi các phương án để khách lựa chọn.");
        } catch(IllegalArgumentException | IllegalStateException failure) { flash.addFlashAttribute("error",failure.getMessage()); }
        return "redirect:/admin/feedback/cases/"+id;
    }

    @PostMapping("/feedback/resolution-plans/{id}/approve")
    String approveResolutionPlan(@PathVariable Long id, @AuthenticationPrincipal PortalUser user, RedirectAttributes flash) {
        try { resolutions.managerDecision(id,user.getUserId(),true,null); flash.addFlashAttribute("success","Đã duyệt phương án khách đã chọn."); }
        catch(IllegalArgumentException | IllegalStateException failure) { flash.addFlashAttribute("error",failure.getMessage()); }
        return "redirect:/admin/feedback";
    }

    @PostMapping("/feedback/resolution-plans/{id}/return")
    String returnResolutionPlan(@PathVariable Long id, @RequestParam String reason,
            @AuthenticationPrincipal PortalUser user, RedirectAttributes flash) {
        try { resolutions.managerDecision(id,user.getUserId(),false,reason); flash.addFlashAttribute("success","Đã trả hồ sơ cho Admin bổ sung."); }
        catch(IllegalArgumentException | IllegalStateException failure) { flash.addFlashAttribute("error",failure.getMessage()); }
        return "redirect:/admin/feedback";
    }

    @PostMapping("/feedback/work-orders/{id}/schedule")
    String scheduleFeedbackWork(@PathVariable Long id, @Valid @ModelAttribute WorkOrderForm form,
            BindingResult errors, @RequestParam Long caseId, @AuthenticationPrincipal PortalUser user, RedirectAttributes flash) {
        try { if(errors.hasErrors()) throw new IllegalArgumentException("Thông tin phiếu thực hiện chưa hợp lệ."); resolutions.scheduleWorkOrder(id,user.getUserId(),form); flash.addFlashAttribute("success","Đã lập lịch công việc."); }
        catch(IllegalArgumentException | IllegalStateException failure) { flash.addFlashAttribute("error",failure.getMessage()); }
        return "redirect:/admin/feedback/cases/"+caseId;
    }

    @PostMapping("/feedback/work-orders/{id}/complete")
    String completeFeedbackWork(@PathVariable Long id, @Valid @ModelAttribute WorkOrderCompletionForm form,
            BindingResult errors, @RequestParam Long caseId, @AuthenticationPrincipal PortalUser user, RedirectAttributes flash) {
        try { if(errors.hasErrors()) throw new IllegalArgumentException("Cần chi phí thực tế và bằng chứng thực hiện."); resolutions.completeWorkOrder(id,user.getUserId(),form); flash.addFlashAttribute("success","Đã ghi nhận kết quả công việc, chờ khách xác nhận."); }
        catch(IllegalArgumentException | IllegalStateException failure) { flash.addFlashAttribute("error",failure.getMessage()); }
        return "redirect:/admin/feedback/cases/"+caseId;
    }

    @PostMapping("/feedback/cases/{id}/resolve")
    String resolveFeedbackCase(@PathVariable Long id, @AuthenticationPrincipal PortalUser user, RedirectAttributes flash) {
        try {
            feedbackCases.resolve(id, user.getUserId());
            flash.addFlashAttribute("success", "Đã hoàn tất hồ sơ sự cố.");
        } catch (IllegalArgumentException | IllegalStateException failure) {
            flash.addFlashAttribute("error", failure.getMessage());
        }
        return "redirect:/admin/feedback/cases/"+id;
    }

    @PostMapping("/feedback/cases/{id}/staff-action/{decision}")
    String decideStaffAction(@PathVariable Long id, @PathVariable String decision,
            @AuthenticationPrincipal PortalUser user, RedirectAttributes flash) {
        try {
            if (!decision.equals("approve") && !decision.equals("reject")) throw new IllegalArgumentException("Quyết định không hợp lệ.");
            feedbackCases.decideStaffAction(id, user.getUserId(), decision.equals("approve"));
            flash.addFlashAttribute("success", "Đã lưu quyết định quản lý đối với xử lý nội bộ Staff.");
        } catch (IllegalArgumentException | IllegalStateException failure) {
            flash.addFlashAttribute("error", failure.getMessage());
        }
        return "redirect:/admin/feedback/cases/"+id;
    }

    @PostMapping("/services")
    String createService(@AuthenticationPrincipal PortalUser user, @Valid @ModelAttribute ServiceForm form,
            BindingResult errors, RedirectAttributes flash) {
        if (!invalid(errors, flash)) { serviceManagement.save(user.getUserId(), null, form); flash.addFlashAttribute("success", "Đã thêm dịch vụ."); }
        return "redirect:/admin/services";
    }

    @PostMapping("/services/{id}")
    String updateService(@PathVariable Long id, @AuthenticationPrincipal PortalUser user,
            @Valid @ModelAttribute ServiceForm form, BindingResult errors, RedirectAttributes flash) {
        if (!invalid(errors, flash)) { serviceManagement.save(user.getUserId(), id, form); flash.addFlashAttribute("success", "Đã cập nhật dịch vụ."); }
        return "redirect:/admin/services";
    }

    @PostMapping("/services/{id}/delete")
    String deleteService(@PathVariable Long id, @AuthenticationPrincipal PortalUser user, RedirectAttributes flash) {
        serviceManagement.deactivate(user.getUserId(), id); flash.addFlashAttribute("success", "Đã ngừng cung cấp dịch vụ; lịch sử đặt dịch vụ được giữ nguyên."); return "redirect:/admin/services";
    }

    @PostMapping("/services/{id}/activate")
    String activateService(@PathVariable Long id, @AuthenticationPrincipal PortalUser user, RedirectAttributes flash) {
        serviceManagement.activate(user.getUserId(), id); flash.addFlashAttribute("success", "Đã kích hoạt lại dịch vụ."); return "redirect:/admin/services";
    }

    @PostMapping("/staff")
    String createStaff(@AuthenticationPrincipal PortalUser user, @Valid @ModelAttribute StaffCreateForm form,
            BindingResult errors, RedirectAttributes flash) {
        if (!invalid(errors, flash)) { staffManagement.create(user.getUserId(), form); flash.addFlashAttribute("success", "Đã tạo nhân viên và tài khoản đăng nhập."); }
        return "redirect:/admin/staff";
    }

    @PostMapping("/staff/{id}")
    String updateStaff(@PathVariable Long id, @AuthenticationPrincipal PortalUser user,
            @Valid @ModelAttribute StaffUpdateForm form, BindingResult errors, RedirectAttributes flash) {
        if (!invalid(errors, flash)) { staffManagement.update(user.getUserId(), id, form); flash.addFlashAttribute("success", "Đã cập nhật nhân viên."); }
        return "redirect:/admin/staff";
    }

    @PostMapping("/staff/{id}/delete")
    String deleteStaff(@PathVariable Long id, @AuthenticationPrincipal PortalUser user, RedirectAttributes flash) {
        staffManagement.deactivate(user.getUserId(), id); flash.addFlashAttribute("success", "Đã ngừng hoạt động tài khoản; dữ liệu lịch sử được giữ nguyên."); return "redirect:/admin/staff";
    }

    @PostMapping("/staff/{id}/activate")
    String activateStaff(@PathVariable Long id, @AuthenticationPrincipal PortalUser user, RedirectAttributes flash) {
        staffManagement.activate(user.getUserId(), id); flash.addFlashAttribute("success", "Đã kích hoạt lại tài khoản nhân viên."); return "redirect:/admin/staff";
    }

    private boolean invalid(BindingResult errors, RedirectAttributes flash) {
        if (!errors.hasErrors()) { return false; }
        flash.addFlashAttribute("error", "Dữ liệu chưa hợp lệ. Kiểm tra các trường bắt buộc, độ dài, thời lượng và giá tiền."); return true;
    }

    private String feedbackValidationMessage(BindingResult errors, String fallback) {
        String details = errors.getFieldErrors().stream()
                .map(org.springframework.validation.FieldError::getDefaultMessage)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .collect(java.util.stream.Collectors.joining(" "));
        return details.isBlank() ? fallback : details;
    }
}
