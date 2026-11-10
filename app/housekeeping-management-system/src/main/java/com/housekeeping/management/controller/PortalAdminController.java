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
    public PortalAdminController(PortalQueryService query, ServiceManagementService serviceManagement,
            StaffManagementService staffManagement, FeedbackService feedbackService, FeedbackCaseService feedbackCases, BookingFinancialService financials, CaseAttachmentStorageService attachments,
            AdminBookingManagementService bookingManagement) {
        this.query = query; this.serviceManagement = serviceManagement; this.staffManagement = staffManagement;
        this.feedbackService = feedbackService; this.feedbackCases = feedbackCases; this.financials = financials; this.attachments=attachments; this.bookingManagement = bookingManagement;
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
    String feedback(Model model, @AuthenticationPrincipal PortalUser user) {
        var feedbackItems=query.feedback();
        model.addAttribute("feedback", feedbackItems);
        var cases=feedbackItems.stream().map(item -> feedbackCases.adminCase(item.id(), user.getUserId()))
                .filter(java.util.Objects::nonNull).toList();
        model.addAttribute("incidentCases", cases);
        model.addAttribute("caseEvidence", cases.stream().collect(java.util.stream.Collectors.toMap(
                com.housekeeping.management.dto.response.PortalViews.IncidentCard::id,
                item -> attachments.adminEvidenceIds(item.id(),user.getUserId()))));
        model.addAttribute("caseMessages", cases.stream().collect(java.util.stream.Collectors.toMap(
                com.housekeeping.management.dto.response.PortalViews.IncidentCard::id,
                item -> feedbackCases.adminMessages(item.id(),user.getUserId()))));
        model.addAttribute("caseFinancialEntries", cases.stream().collect(java.util.stream.Collectors.toMap(
                com.housekeeping.management.dto.response.PortalViews.IncidentCard::id,
                item -> financials.forBooking(item.bookingId(),user.getUserId()).stream()
                        .filter(entry -> item.id().equals(entry.caseId())).toList())));
        model.addAttribute("caseByFeedback", cases.stream().collect(java.util.stream.Collectors.toMap(
                com.housekeeping.management.dto.response.PortalViews.IncidentCard::feedbackId,
                com.housekeeping.management.dto.response.PortalViews.IncidentCard::id)));
        var feedbackById=feedbackItems.stream().collect(java.util.stream.Collectors.toMap(
                com.housekeeping.management.dto.response.PortalViews.FeedbackCard::id, item->item));
        model.addAttribute("feedbackById",feedbackById);
        model.addAttribute("caseCanResolve", cases.stream().collect(java.util.stream.Collectors.toMap(
                com.housekeeping.management.dto.response.PortalViews.IncidentCard::id, incident -> {
                    var entries=financials.forBooking(incident.bookingId(),user.getUserId()).stream()
                            .filter(entry -> incident.id().equals(entry.caseId())).toList();
                    var feedbackItem=feedbackById.get(incident.feedbackId());
                    boolean replied=feedbackItem!=null && feedbackItem.adminReply()!=null && !feedbackItem.adminReply().isBlank();
                    boolean remedyDone=incident.remedyType()==null || incident.remedyCompleted();
                    boolean noPendingEntries=entries.stream().noneMatch(entry -> java.util.Set.of("PROPOSED","PENDING_APPROVAL","APPROVED").contains(entry.status()));
                    boolean requiredPaymentSettled=switch (incident.remedyType()==null?"":incident.remedyType()) {
                        case "REFUND" -> entries.stream().anyMatch(entry -> "REFUND".equals(entry.type()) && java.util.Set.of("PAID","SIMULATED").contains(entry.status()));
                        case "PRICE_REDUCTION" -> entries.stream().anyMatch(entry -> "PRICE_REDUCTION".equals(entry.type()) && java.util.Set.of("PAID","SIMULATED").contains(entry.status()));
                        case "COMPENSATION" -> entries.stream().anyMatch(entry -> "COMPENSATION".equals(entry.type()) && java.util.Set.of("PAID","SIMULATED").contains(entry.status()));
                        default -> true;
                    };
                    return java.util.Set.of("VERIFIED","NOT_SUBSTANTIATED").contains(incident.status()) && replied
                            && remedyDone && requiredPaymentSettled && noPendingEntries && !"PENDING".equals(incident.staffActionStatus());
                })));
        model.addAttribute("staffActionHistory", cases.stream().collect(java.util.stream.Collectors.toMap(
                com.housekeeping.management.dto.response.PortalViews.IncidentCard::id,
                item -> feedbackCases.actions(item.id(),user.getUserId()))));
        return "admin/feedback";
    }

    @GetMapping("/finance")
    String finance(Model model, @AuthenticationPrincipal PortalUser user) {
        model.addAttribute("entries", financials.all(user.getUserId()));
        model.addAttribute("summary", financials.summary(user.getUserId()));
        return "admin/finance";
    }

    @PostMapping("/financial-entries")
    String proposeFinancialEntry(@RequestParam Long bookingId, @RequestParam(required=false) Long caseId,
            @Valid @ModelAttribute FinancialEntryForm form, BindingResult errors,
            @AuthenticationPrincipal PortalUser user, RedirectAttributes flash) {
        try {
            if (errors.hasErrors()) throw new IllegalArgumentException("Dữ liệu khoản tiền không hợp lệ.");
            financials.propose(bookingId,caseId,user.getUserId(),form);
            flash.addFlashAttribute("success","Đã lập khoản thu/chi và gửi chờ quản lý duyệt.");
        } catch (IllegalArgumentException | IllegalStateException failure) { flash.addFlashAttribute("error",failure.getMessage()); }
        return "redirect:/admin/finance";
    }

    @PostMapping("/financial-entries/{id}/approve")
    String approveFinancialEntry(@PathVariable Long id, @RequestParam(required=false) Long returnCaseId,
            @AuthenticationPrincipal PortalUser user, RedirectAttributes flash) {
        try { financials.approve(id,user.getUserId()); flash.addFlashAttribute("success","Đã duyệt khoản tiền."); }
        catch (IllegalArgumentException | IllegalStateException failure) { flash.addFlashAttribute("error",failure.getMessage()); }
        return returnCaseId==null?"redirect:/admin/finance":"redirect:/admin/feedback#case-"+returnCaseId;
    }

    @PostMapping("/feedback/cases/{id}/messages")
    String sendFeedbackCaseMessage(@PathVariable Long id,
            @RequestParam(required=false) String message,
            @RequestParam(required=false) java.util.List<org.springframework.web.multipart.MultipartFile> images,
            @AuthenticationPrincipal PortalUser user, RedirectAttributes flash) {
        Long messageId=null;
        try {
            var files=images==null?java.util.List.<org.springframework.web.multipart.MultipartFile>of():images.stream()
                    .filter(file->file!=null&&!file.isEmpty()).toList();
            if(files.size()>5) throw new IllegalArgumentException("Mỗi tin nhắn tối đa 5 ảnh bằng chứng.");
            messageId=feedbackCases.addAdminMessage(id,user.getUserId(),message,!files.isEmpty());
            if(!files.isEmpty()) attachments.uploadAdminMessageEvidence(id,user.getUserId(),messageId,files);
            feedbackCases.confirmAdminMessage(id,messageId,user.getUserId());
            flash.addFlashAttribute("success","Đã gửi tin nhắn vào hồ sơ sự cố.");
        } catch (IllegalArgumentException | IllegalStateException failure) {
            if(messageId!=null) feedbackCases.discardUnsentMessage(id,messageId,user.getUserId(),true);
            flash.addFlashAttribute("error",failure.getMessage());
        }
        return "redirect:/admin/feedback#case-"+id;
    }

    @PostMapping("/financial-entries/{id}/reject")
    String rejectFinancialEntry(@PathVariable Long id, @RequestParam String reason, @RequestParam(required=false) Long returnCaseId,
            @AuthenticationPrincipal PortalUser user, RedirectAttributes flash) {
        try { financials.reject(id,user.getUserId(),reason); flash.addFlashAttribute("success","Đã từ chối khoản tiền với lý do được lưu."); }
        catch (IllegalArgumentException | IllegalStateException failure) { flash.addFlashAttribute("error",failure.getMessage()); }
        return returnCaseId==null?"redirect:/admin/finance":"redirect:/admin/feedback#case-"+returnCaseId;
    }

    @PostMapping("/financial-entries/{id}/cancel")
    String cancelFinancialEntry(@PathVariable Long id, @RequestParam String reason,
            @AuthenticationPrincipal PortalUser user, RedirectAttributes flash) {
        try { financials.cancel(id,user.getUserId(),reason); flash.addFlashAttribute("success","Đã hủy khoản chờ xử lý."); }
        catch (IllegalArgumentException | IllegalStateException failure) { flash.addFlashAttribute("error",failure.getMessage()); }
        return "redirect:/admin/finance";
    }

    @PostMapping("/financial-entries/{id}/settle")
    String settleFinancialEntry(@PathVariable Long id, @RequestParam String method,
            @RequestParam String reference, @RequestParam(defaultValue="false") boolean simulated,
            @AuthenticationPrincipal PortalUser user, RedirectAttributes flash) {
        try { financials.settle(id,user.getUserId(),method,reference,simulated); flash.addFlashAttribute("success","Đã ghi nhận khoản tiền đã chi/hoàn."); }
        catch (IllegalArgumentException | IllegalStateException failure) { flash.addFlashAttribute("error",failure.getMessage()); }
        return "redirect:/admin/finance";
    }

    @PostMapping("/financial-entries/{id}/receipt")
    String uploadReceipt(@PathVariable Long id, @RequestParam org.springframework.web.multipart.MultipartFile file,
            @AuthenticationPrincipal PortalUser user, RedirectAttributes flash) {
        try { attachments.uploadReceipt(id,user.getUserId(),file); flash.addFlashAttribute("success","Đã lưu hóa đơn/biên nhận cho khoản tiền."); }
        catch (IllegalArgumentException | IllegalStateException failure) { flash.addFlashAttribute("error",failure.getMessage()); }
        return "redirect:/admin/finance";
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
        return "redirect:/admin/feedback";
    }

    @PostMapping("/feedback/{id}/resolve")
    String resolve(@PathVariable Long id, @AuthenticationPrincipal PortalUser user, RedirectAttributes flash) {
        try {
            if(feedbackCases.adminCase(id,user.getUserId())!=null)
                throw new IllegalStateException("Hãy giải quyết hồ sơ sự cố bằng nút đóng hồ sơ để kiểm tra đủ điều kiện.");
            feedbackService.resolve(id, user.getUserId());
            flash.addFlashAttribute("success", "Đã đánh dấu phản hồi là đã giải quyết.");
        } catch (IllegalArgumentException | IllegalStateException failure) { flash.addFlashAttribute("error",failure.getMessage()); }
        return "redirect:/admin/feedback";
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
        return "redirect:/admin/feedback";
    }

    @PostMapping("/feedback/cases/{id}/resolve")
    String resolveFeedbackCase(@PathVariable Long id, @AuthenticationPrincipal PortalUser user, RedirectAttributes flash) {
        try {
            feedbackCases.resolve(id, user.getUserId());
            flash.addFlashAttribute("success", "Đã hoàn tất hồ sơ sự cố.");
        } catch (IllegalArgumentException | IllegalStateException failure) {
            flash.addFlashAttribute("error", failure.getMessage());
        }
        return "redirect:/admin/feedback";
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
        return "redirect:/admin/feedback";
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
