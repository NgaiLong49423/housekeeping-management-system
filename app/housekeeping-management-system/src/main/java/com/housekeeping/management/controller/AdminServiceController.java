package com.housekeeping.management.controller;

import com.housekeeping.management.dto.request.PortalForms.ServiceForm;
import com.housekeeping.management.service.PortalQueryService;
import com.housekeeping.management.service.ServiceManagementService;
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
public class AdminServiceController {
    private final PortalQueryService query;
    private final ServiceManagementService serviceManagement;

    public AdminServiceController(PortalQueryService query, ServiceManagementService serviceManagement) {
        this.query = query;
        this.serviceManagement = serviceManagement;
    }

    @GetMapping("/services")
    String services(Model model, @AuthenticationPrincipal PortalUser user) { model.addAttribute("services", query.services(user)); return "admin/services"; }


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


    private boolean invalid(BindingResult errors, RedirectAttributes flash) {
        if (!errors.hasErrors()) return false;
        flash.addFlashAttribute("error", "Dữ liệu chưa hợp lệ. Kiểm tra các trường bắt buộc, độ dài, thời lượng và giá tiền.");
        return true;
    }
}
