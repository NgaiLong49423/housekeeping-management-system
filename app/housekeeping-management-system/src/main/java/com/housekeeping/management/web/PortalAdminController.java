package com.housekeeping.management.web;

import static com.housekeeping.management.web.PortalForms.*;
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
    private final PortalActionsService actions;
    public PortalAdminController(PortalQueryService query, PortalActionsService actions) { this.query = query; this.actions = actions; }

    @GetMapping("/services")
    String services(Model model) { model.addAttribute("services", query.services()); return "admin/services"; }

    @GetMapping("/staff")
    String staff(@AuthenticationPrincipal PortalUser user, Model model) { model.addAttribute("staff", query.staff(user)); return "admin/staff"; }

    @GetMapping("/schedules")
    String schedules(@AuthenticationPrincipal PortalUser user, Model model) {
        model.addAttribute("schedules", query.schedules(user)); model.addAttribute("staff", query.staff(user)); return "admin/schedules";
    }

    @PostMapping("/services")
    String createService(@Valid @ModelAttribute ServiceForm form, BindingResult errors, RedirectAttributes flash) {
        if (!invalid(errors, flash)) { actions.saveService(null, form); flash.addFlashAttribute("success", "Đã thêm dịch vụ."); }
        return "redirect:/admin/services";
    }

    @PostMapping("/services/{id}")
    String updateService(@PathVariable Long id, @Valid @ModelAttribute ServiceForm form, BindingResult errors, RedirectAttributes flash) {
        if (!invalid(errors, flash)) { actions.saveService(id, form); flash.addFlashAttribute("success", "Đã cập nhật dịch vụ."); }
        return "redirect:/admin/services";
    }

    @PostMapping("/services/{id}/delete")
    String deleteService(@PathVariable Long id, RedirectAttributes flash) {
        actions.deleteService(id); flash.addFlashAttribute("success", "Đã xóa dịch vụ không có dữ liệu phụ thuộc."); return "redirect:/admin/services";
    }

    @PostMapping("/staff")
    String createStaff(@Valid @ModelAttribute StaffCreateForm form, BindingResult errors, RedirectAttributes flash) {
        if (!invalid(errors, flash)) { actions.createStaff(form); flash.addFlashAttribute("success", "Đã tạo nhân viên và tài khoản đăng nhập."); }
        return "redirect:/admin/staff";
    }

    @PostMapping("/staff/{id}")
    String updateStaff(@PathVariable Long id, @Valid @ModelAttribute StaffUpdateForm form, BindingResult errors, RedirectAttributes flash) {
        if (!invalid(errors, flash)) { actions.updateStaff(id, form); flash.addFlashAttribute("success", "Đã cập nhật nhân viên."); }
        return "redirect:/admin/staff";
    }

    @PostMapping("/staff/{id}/delete")
    String deleteStaff(@PathVariable Long id, RedirectAttributes flash) {
        actions.deleteStaff(id); flash.addFlashAttribute("success", "Đã xóa nhân viên không có dữ liệu phụ thuộc."); return "redirect:/admin/staff";
    }

    @PostMapping("/schedules")
    String createSchedule(@AuthenticationPrincipal PortalUser user, @Valid @ModelAttribute ScheduleForm form, BindingResult errors, RedirectAttributes flash) {
        if (!invalid(errors, flash)) { actions.saveSchedule(null, user.getAccountId(), form); flash.addFlashAttribute("success", "Đã thêm khoảng sẵn sàng."); }
        return "redirect:/admin/schedules";
    }

    @PostMapping("/schedules/{id}")
    String updateSchedule(@PathVariable Long id, @AuthenticationPrincipal PortalUser user,
            @Valid @ModelAttribute ScheduleForm form, BindingResult errors, RedirectAttributes flash) {
        if (!invalid(errors, flash)) { actions.saveSchedule(id, user.getAccountId(), form); flash.addFlashAttribute("success", "Đã cập nhật khoảng sẵn sàng."); }
        return "redirect:/admin/schedules";
    }

    private boolean invalid(BindingResult errors, RedirectAttributes flash) {
        if (!errors.hasErrors()) { return false; }
        flash.addFlashAttribute("error", "Dữ liệu chưa hợp lệ. Kiểm tra các trường bắt buộc, độ dài, thời lượng và giá tiền."); return true;
    }
}
