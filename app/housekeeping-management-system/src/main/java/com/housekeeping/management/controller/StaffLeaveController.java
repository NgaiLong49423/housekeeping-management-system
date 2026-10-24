package com.housekeeping.management.controller;

import com.housekeeping.management.dto.request.PortalForms.LeaveRequestForm;
import com.housekeeping.management.service.LeaveManagementService;
import com.housekeeping.management.web.PortalMessages;
import com.housekeeping.management.web.PortalUser;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/staff/leaves")
public class StaffLeaveController {
    private final LeaveManagementService leaves;

    public StaffLeaveController(LeaveManagementService leaves) {
        this.leaves = leaves;
    }

    @GetMapping
    String list(@AuthenticationPrincipal PortalUser user, Model model) {
        model.addAttribute("leaveRequests", leaves.findForStaff(user.getAccountId()));
        if (!model.containsAttribute("leaveRequestForm")) {
            model.addAttribute("leaveRequestForm", new LeaveRequestForm(null, null, ""));
        }
        return "staff/leaves";
    }

    @PostMapping
    String submit(@AuthenticationPrincipal PortalUser user,
            @Valid @ModelAttribute("leaveRequestForm") LeaveRequestForm form,
            BindingResult errors, RedirectAttributes flash) {
        if (errors.hasErrors()) {
            flash.addFlashAttribute("error", "Hãy nhập ngày nghỉ hợp lệ và lý do không quá 500 ký tự.");
        } else {
            try {
                leaves.submitLeaveRequest(user.getAccountId(), form);
                flash.addFlashAttribute("success", "Đơn nghỉ đã được gửi và đang chờ Admin xét duyệt.");
            } catch (IllegalArgumentException | IllegalStateException failure) {
                flash.addFlashAttribute("error", PortalMessages.business(failure));
            }
        }
        return "redirect:/staff/leaves";
    }
}
