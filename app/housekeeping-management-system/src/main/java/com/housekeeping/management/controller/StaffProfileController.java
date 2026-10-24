package com.housekeeping.management.controller;

import com.housekeeping.management.dto.request.PortalForms.StaffProfileForm;
import com.housekeeping.management.service.StaffProfileService;
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
@RequestMapping("/staff/profile")
public class StaffProfileController {
    private final StaffProfileService profiles;
    public StaffProfileController(StaffProfileService profiles) { this.profiles = profiles; }

    @GetMapping
    String view(@AuthenticationPrincipal PortalUser user, Model model) {
        model.addAttribute("profileForm", profiles.findOwnProfile(user.getUserId()));
        return "staff/profile";
    }

    @PostMapping
    String update(@AuthenticationPrincipal PortalUser user,
            @Valid @ModelAttribute("profileForm") StaffProfileForm form, BindingResult errors,
            Model model, RedirectAttributes flash) {
        if (errors.hasErrors()) {
            model.addAttribute("error", "Giới thiệu tối đa 5.000 ký tự và URL ảnh tối đa 500 ký tự.");
            return "staff/profile";
        }
        try {
            profiles.updateOwnProfile(user.getUserId(), form);
            flash.addFlashAttribute("success", "Hồ sơ của bạn đã được cập nhật.");
        } catch (IllegalArgumentException failure) {
            flash.addFlashAttribute("error", failure.getMessage());
        }
        return "redirect:/staff/profile";
    }
}
