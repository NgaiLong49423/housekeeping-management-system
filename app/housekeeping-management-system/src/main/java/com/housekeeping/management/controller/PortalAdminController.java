package com.housekeeping.management.controller;

import static com.housekeeping.management.dto.request.PortalForms.*;
import com.housekeeping.management.service.FeedbackCaseService;
import com.housekeeping.management.service.BookingFinancialService;
import com.housekeeping.management.service.CaseAttachmentStorageService;
import com.housekeeping.management.service.PortalQueryService;
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
    private final FeedbackCaseService feedbackCases;
    private final BookingFinancialService financials;
    private final CaseAttachmentStorageService attachments;
    public PortalAdminController(PortalQueryService query, FeedbackCaseService feedbackCases,
            BookingFinancialService financials, CaseAttachmentStorageService attachments) {
        this.query = query;
        this.feedbackCases = feedbackCases;
        this.financials = financials;
        this.attachments = attachments;
    }

    @GetMapping("/schedules")
    String schedules(@AuthenticationPrincipal PortalUser user, Model model) {
        model.addAttribute("schedules", query.schedules(user)); return "admin/schedules";
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
            if ("COMPENSATION".equals(form.entryType()))
                throw new IllegalArgumentException("Chỉ lập bồi thường từ chi tiết hồ sơ sự cố.");
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

}
