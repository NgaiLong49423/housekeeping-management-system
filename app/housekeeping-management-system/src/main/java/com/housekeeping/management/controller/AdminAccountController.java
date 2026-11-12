package com.housekeeping.management.controller;

import com.housekeeping.management.dto.request.PortalForms.AdminAccountForm;
import com.housekeeping.management.service.AdminAccountService;
import com.housekeeping.management.service.DuplicateEmailException;
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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/admin/accounts")
public class AdminAccountController {
    private final AdminAccountService accounts;

    public AdminAccountController(AdminAccountService accounts) {
        this.accounts = accounts;
    }

    @GetMapping
    String accounts(@AuthenticationPrincipal PortalUser user, Model model) {
        model.addAttribute("admins", accounts.findAll(user.getUserId()));
        model.addAttribute("adminAccountForm", new AdminAccountForm("", "", "", ""));
        return "admin/accounts";
    }

    @PostMapping
    String create(@Valid @ModelAttribute("adminAccountForm") AdminAccountForm form, BindingResult errors,
            @AuthenticationPrincipal PortalUser user, RedirectAttributes flash) {
        try {
            if (errors.hasErrors()) throw new IllegalArgumentException("Vui lòng kiểm tra lại thông tin Admin.");
            accounts.create(user.getUserId(), form);
            flash.addFlashAttribute("success", "Đã tạo tài khoản Admin.");
        } catch (DuplicateEmailException duplicate) {
            flash.addFlashAttribute("error", "Email này đã được sử dụng.");
        } catch (IllegalArgumentException failure) {
            flash.addFlashAttribute("error", failure.getMessage());
        }
        return "redirect:/admin/accounts";
    }

    @PostMapping("/{id}/status")
    String updateStatus(@PathVariable Long id, @RequestParam String status, @RequestParam(required = false) String reason,
            @AuthenticationPrincipal PortalUser user, RedirectAttributes flash) {
        try {
            accounts.updateStatus(user.getUserId(), id, status, reason);
            flash.addFlashAttribute("success", "Đã cập nhật trạng thái tài khoản Admin.");
        } catch (IllegalArgumentException failure) {
            flash.addFlashAttribute("error", failure.getMessage());
        }
        return "redirect:/admin/accounts";
    }
}
