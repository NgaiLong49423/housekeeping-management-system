package com.housekeeping.management.controller;

import com.housekeeping.management.dto.request.PortalForms.StaffCreateForm;
import com.housekeeping.management.dto.request.PortalForms.StaffUpdateForm;
import com.housekeeping.management.service.PortalQueryService;
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
public class AdminStaffController {
    private final PortalQueryService query;
    private final StaffManagementService staffManagement;

    public AdminStaffController(PortalQueryService query, StaffManagementService staffManagement) {
        this.query = query;
        this.staffManagement = staffManagement;
    }

    @GetMapping("/staff")
    String staff(@AuthenticationPrincipal PortalUser user, Model model) { model.addAttribute("staff", query.staff(user)); return "admin/staff"; }


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
        if (!errors.hasErrors()) return false;
        flash.addFlashAttribute("error", "Dữ liệu chưa hợp lệ. Kiểm tra các trường bắt buộc, độ dài, thời lượng và giá tiền.");
        return true;
    }
}
