package com.housekeeping.management.controller;

import com.housekeeping.management.domain.LeaveStatus;
import com.housekeeping.management.service.LeaveManagementService;
import com.housekeeping.management.web.PortalUser;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/admin/leaves")
public class AdminLeaveController {
    private final LeaveManagementService leaves;

    public AdminLeaveController(LeaveManagementService leaves) {
        this.leaves = leaves;
    }

    @GetMapping
    String list(@AuthenticationPrincipal PortalUser user, Model model) {
        model.addAttribute("leaveRequests", leaves.findAllForAdmin(user.getUserId()));
        return "admin/leaves";
    }

    @PostMapping("/{id}/approve")
    String approve(@PathVariable Long id, @AuthenticationPrincipal PortalUser user, RedirectAttributes flash) {
        return review(id, user, LeaveStatus.APPROVED, flash);
    }

    @PostMapping("/{id}/reject")
    String reject(@PathVariable Long id, @AuthenticationPrincipal PortalUser user, RedirectAttributes flash) {
        return review(id, user, LeaveStatus.REJECTED, flash);
    }

    private String review(Long id, PortalUser user, LeaveStatus decision, RedirectAttributes flash) {
        try {
            leaves.reviewLeaveRequest(id, user.getUserId(), decision);
            flash.addFlashAttribute("success", decision == LeaveStatus.APPROVED
                    ? "Đã duyệt đơn nghỉ. Hãy xử lý riêng các Booking đã xác nhận bị ảnh hưởng."
                    : "Đã từ chối đơn nghỉ.");
        } catch (IllegalArgumentException | IllegalStateException failure) {
            flash.addFlashAttribute("error", failure.getMessage());
        }
        return "redirect:/admin/leaves";
    }
}
