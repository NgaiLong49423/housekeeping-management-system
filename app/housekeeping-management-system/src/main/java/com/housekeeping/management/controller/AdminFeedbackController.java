package com.housekeeping.management.controller;

import static com.housekeeping.management.dto.request.PortalForms.*;
import com.housekeeping.management.dto.response.PortalViews;
import com.housekeeping.management.service.FeedbackService;
import com.housekeeping.management.service.FeedbackCaseService;
import com.housekeeping.management.service.BookingFinancialService;
import com.housekeeping.management.service.CaseAttachmentStorageService;
import com.housekeeping.management.service.PortalQueryService;
import com.housekeeping.management.web.PortalUser;
import com.housekeeping.management.web.PortalMessages;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/admin")
public class AdminFeedbackController {
    private final PortalQueryService query;
    private final FeedbackService feedbackService;
    private final FeedbackCaseService feedbackCases;
    private final BookingFinancialService financials;
    private final CaseAttachmentStorageService attachments;

    public AdminFeedbackController(PortalQueryService query, FeedbackService feedbackService,
            FeedbackCaseService feedbackCases, BookingFinancialService financials,
            CaseAttachmentStorageService attachments) {
        this.query = query;
        this.feedbackService = feedbackService;
        this.feedbackCases = feedbackCases;
        this.financials = financials;
        this.attachments = attachments;
    }

    @GetMapping("/feedback")
    String feedback(@RequestParam(defaultValue="all") String status, @RequestParam(required=false) Long bookingId,
            Model model, @AuthenticationPrincipal PortalUser user) {
        var allFeedback=query.feedback();
        var allCases=allFeedback.stream().map(item -> feedbackCases.adminCase(item.id(), user.getUserId()))
                .filter(java.util.Objects::nonNull).toList();
        var casesByFeedback=allCases.stream().collect(java.util.stream.Collectors.toMap(
                com.housekeeping.management.dto.response.PortalViews.IncidentCard::feedbackId, item -> item));
        var queueStatusByFeedback=allFeedback.stream().collect(java.util.stream.Collectors.toMap(
                com.housekeeping.management.dto.response.PortalViews.FeedbackCard::id, item -> {
                    var incident=casesByFeedback.get(item.id());
                    if (incident==null) return "processing";
                    return switch (incident.status()) {
                        case "RECEIVED" -> "intake";
                        case "WAITING_CUSTOMER" -> "customer";
                        case "WAITING_MANAGER" -> "manager";
                        case "WAITING_PAYOUT", "WAITING_CUSTOMER_CONFIRMATION" -> "payout";
                        case "RESOLVED" -> "resolved";
                        default -> "processing";
                    };
                }));
        String selectedStatus=java.util.Set.of("all","intake","processing","customer","manager","payout","resolved").contains(status) ? status : "all";
        var feedbackItems=allFeedback.stream()
                .filter(item -> bookingId==null || bookingId.equals(item.bookingId()))
                .filter(item -> "all".equals(selectedStatus) || selectedStatus.equals(queueStatusByFeedback.get(item.id())))
                .toList();
        model.addAttribute("feedback", feedbackItems);
        model.addAttribute("queueStatusByFeedback", queueStatusByFeedback);
        model.addAttribute("selectedStatus", selectedStatus);
        model.addAttribute("selectedBookingId", bookingId);
        model.addAttribute("feedbackCount", allFeedback.size());
        model.addAttribute("intakeCount", queueStatusByFeedback.values().stream().filter("intake"::equals).count());
        model.addAttribute("processingCount", queueStatusByFeedback.values().stream().filter("processing"::equals).count());
        model.addAttribute("customerCount", queueStatusByFeedback.values().stream().filter("customer"::equals).count());
        model.addAttribute("managerCount", queueStatusByFeedback.values().stream().filter("manager"::equals).count());
        model.addAttribute("payoutCount", queueStatusByFeedback.values().stream().filter("payout"::equals).count());
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
        return feedbackCaseRedirect(id,"messages",flash);
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
        return feedbackCaseRedirect(id,"verification",flash);
    }


    @PostMapping("/feedback/cases/{id}/staff-action")
    String proposeStaffAction(@PathVariable Long id, @Valid @ModelAttribute StaffIncidentActionForm form,
            BindingResult errors, @AuthenticationPrincipal PortalUser user, RedirectAttributes flash) {
        if (!errors.hasErrors()) try {
            feedbackCases.proposeStaffAction(id,user.getUserId(),form);
            flash.addFlashAttribute("success","Đã gửi đề xuất xử lý Staff cho Manager.");
        } catch (IllegalArgumentException | IllegalStateException failure) {
            flash.addFlashAttribute("error",PortalMessages.business(failure));
        } else flash.addFlashAttribute("error",feedbackValidationMessage(errors,"Kiểm tra lại nội dung xử lý Staff."));
        return feedbackCaseRedirect(id,"staff",flash);
    }


    @PostMapping("/feedback/cases/{id}/accept")
    String acceptFeedbackCase(@PathVariable Long id, @AuthenticationPrincipal PortalUser user, RedirectAttributes flash) {
        try { feedbackCases.acceptIntake(id,user.getUserId()); flash.addFlashAttribute("success","Đã tiếp nhận phản ánh và chuyển sang đang xử lý."); }
        catch (IllegalArgumentException | IllegalStateException failure) { flash.addFlashAttribute("error",PortalMessages.business(failure)); }
        return feedbackCaseRedirect(id,"intake",flash);
    }


    @PostMapping("/feedback/cases/{id}/search-item")
    String searchMissingItem(@PathVariable Long id, @AuthenticationPrincipal PortalUser user, RedirectAttributes flash) {
        try { feedbackCases.startSearchingItem(id,user.getUserId()); flash.addFlashAttribute("success","Đã chuyển hồ sơ sang bước tìm lại tài sản."); }
        catch (IllegalArgumentException | IllegalStateException failure) { flash.addFlashAttribute("error",PortalMessages.business(failure)); }
        return feedbackCaseRedirect(id,"intake",flash);
    }


    @PostMapping("/feedback/cases/{id}/outcome")
    String sendIncidentOutcome(@PathVariable Long id, @RequestParam String outcome,
            @AuthenticationPrincipal PortalUser user, RedirectAttributes flash) {
        try { feedbackCases.sendOutcome(id,user.getUserId(),outcome); flash.addFlashAttribute("success","Đã gửi kết quả cho khách; hồ sơ chờ khách xác nhận."); }
        catch (IllegalArgumentException | IllegalStateException failure) { flash.addFlashAttribute("error",PortalMessages.business(failure)); }
        return feedbackCaseRedirect(id,"outcome",flash);
    }


    @PostMapping("/feedback/cases/{id}/compensation")
    String proposeCaseCompensation(@PathVariable Long id, @RequestParam java.math.BigDecimal amount,
            @RequestParam String reason, @AuthenticationPrincipal PortalUser user, RedirectAttributes flash) {
        Long entryId=null;
        try {
            var incident=feedbackCases.adminCaseById(id,user.getUserId());
            if(incident==null) throw new IllegalArgumentException("Hồ sơ không tồn tại.");
            entryId=financials.propose(incident.bookingId(),id,user.getUserId(),new FinancialEntryForm("COMPENSATION",amount,reason,null,null,false));
            flash.addFlashAttribute("financialNoticeEntryId",entryId);
            flash.addFlashAttribute("success","Đã gửi đề xuất bồi thường cho khách xác nhận.");
        } catch (IllegalArgumentException | IllegalStateException failure) { flash.addFlashAttribute("error",PortalMessages.business(failure)); }
        if(entryId!=null) return "redirect:/admin/feedback/cases/"+id+"#compensation-entry-"+entryId;
        return feedbackCaseRedirect(id,"compensation",flash);
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
        return feedbackCaseRedirect(id,"staff",flash);
    }


    @PostMapping("/feedback/cases/{id}/staff-action/decision")
    String decideStaffLiability(@PathVariable Long id, @RequestParam String decision,
            @RequestParam(required=false) java.math.BigDecimal liabilityAmount, @RequestParam String reason,
            @RequestParam(defaultValue="false") boolean appealDecision,
            @AuthenticationPrincipal PortalUser user, RedirectAttributes flash) {
        try {
            feedbackCases.decideStaffAction(id,user.getUserId(),decision,liabilityAmount,reason,appealDecision);
            flash.addFlashAttribute("success",appealDecision?"Đã lưu kết luận cuối sau khi xem phản đối của Staff.":"Đã lưu kết luận; Staff có 48 giờ để phản đối một lần.");
        } catch(IllegalArgumentException | IllegalStateException failure) { flash.addFlashAttribute("error",PortalMessages.business(failure)); }
        return feedbackCaseRedirect(id,"staff",flash);
    }


    @PostMapping("/feedback/cases/{id}/staff-action/{actionId}/recovery")
    String recordStaffRecovery(@PathVariable Long id, @PathVariable Long actionId,
            @RequestParam java.math.BigDecimal amount, @RequestParam String paymentMethod,
            @RequestParam String transactionReference, @RequestParam org.springframework.web.multipart.MultipartFile receipt,
            @AuthenticationPrincipal PortalUser user, RedirectAttributes flash) {
        try {
            attachments.saveStaffRecoveryInstallment(actionId,user.getUserId(),
                    new com.housekeeping.management.dto.request.PortalForms.StaffRecoveryInstallmentForm(amount,paymentMethod,transactionReference),receipt);
            flash.addFlashAttribute("success","Đã ghi nhận khoản Staff thực trả.");
        } catch(IllegalArgumentException | IllegalStateException failure) { flash.addFlashAttribute("error",PortalMessages.business(failure)); }
        return feedbackCaseRedirect(id,"staff",flash);
    }


    private String feedbackValidationMessage(BindingResult errors, String fallback) {
        String details = errors.getFieldErrors().stream().map(org.springframework.validation.FieldError::getDefaultMessage)
                .filter(java.util.Objects::nonNull).distinct().collect(java.util.stream.Collectors.joining(" "));
        return details.isBlank() ? fallback : details;
    }

    private String feedbackCaseRedirect(Long caseId, String section, RedirectAttributes flash) {
        flash.addFlashAttribute("feedbackNoticeTarget",section);
        return "redirect:/admin/feedback/cases/"+caseId+"#feedback-"+section+"-"+caseId;
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
            model.addAttribute("feedbackById",java.util.Map.of(item.id(),item));
            model.addAttribute("caseMessages",java.util.Map.of(incident.id(),feedbackCases.adminMessages(incident.id(),user.getUserId())));
            model.addAttribute("verificationHistory",feedbackCases.verificationHistory(incident.id(),user.getUserId()));
            model.addAttribute("hasSufficientEvidence",feedbackCases.hasSufficientEvidence(incident.id()));
            model.addAttribute("caseFinancialEntries",java.util.Map.of(incident.id(),entries));
            model.addAttribute("hasActiveCompensationProposal",entries.stream().anyMatch(entry ->
                    "COMPENSATION".equals(entry.type())
                            && java.util.Set.of("PROPOSED","PENDING_APPROVAL","APPROVED","PAID").contains(entry.status())));
            model.addAttribute("adminPayoutDetails",payoutDetails);
            model.addAttribute("staffActionHistory",java.util.Map.of(incident.id(),feedbackCases.actions(incident.id(),user.getUserId())));
            var actionHistory=feedbackCases.actions(incident.id(),user.getUserId());
            java.util.Map<Long,java.util.List<PortalViews.StaffRecoveryPaymentCard>> recoveries=new java.util.HashMap<>();
            for(var action:actionHistory) if(action.liabilityAmount()!=null && action.liabilityAmount().signum()>0)
                recoveries.put(action.id(),feedbackCases.staffRecoveryPayments(action.id(),user.getUserId()));
            model.addAttribute("staffRecoveryPayments",recoveries);
        }
        return "admin/feedback-detail";
    }
}
