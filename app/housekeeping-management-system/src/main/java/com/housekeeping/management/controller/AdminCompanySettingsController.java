package com.housekeeping.management.controller;

import com.housekeeping.management.dto.request.PortalForms.CompanySettingsForm;
import com.housekeeping.management.service.CompanySettingsService;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/admin/settings")
public class AdminCompanySettingsController {
    private final CompanySettingsService settings;
    public AdminCompanySettingsController(CompanySettingsService settings) { this.settings = settings; }

    @GetMapping
    String view(Model model) {
        model.addAttribute("settingsForm", settings.get());
        model.addAttribute("dayNames", new String[] {"Thứ Hai", "Thứ Ba", "Thứ Tư", "Thứ Năm", "Thứ Sáu", "Thứ Bảy", "Chủ nhật"});
        return "admin/settings";
    }

    @PostMapping
    String save(@Valid @ModelAttribute("settingsForm") CompanySettingsForm form, BindingResult errors,
            Model model, RedirectAttributes flash) {
        if (errors.hasErrors()) {
            model.addAttribute("dayNames", new String[] {"Thứ Hai", "Thứ Ba", "Thứ Tư", "Thứ Năm", "Thứ Sáu", "Thứ Bảy", "Chủ nhật"});
            model.addAttribute("error", "Vui lòng kiểm tra các trường bắt buộc và giá trị cấu hình.");
            return "admin/settings";
        }
        try {
            settings.save(form);
            flash.addFlashAttribute("success", "Đã lưu thông tin công ty và giờ hoạt động.");
        } catch (IllegalArgumentException | IllegalStateException exception) {
            flash.addFlashAttribute("error", exception.getMessage());
            return "redirect:/admin/settings";
        }
        return "redirect:/admin/settings";
    }
}
