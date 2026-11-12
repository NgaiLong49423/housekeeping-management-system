package com.housekeeping.management.controller;

import com.housekeeping.management.service.CustomerManagementService;
import com.housekeeping.management.web.PortalUser;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/admin/customers")
public class AdminCustomerController {
    private final CustomerManagementService customers;

    public AdminCustomerController(CustomerManagementService customers) {
        this.customers = customers;
    }

    @GetMapping
    String customers(@AuthenticationPrincipal PortalUser user, @RequestParam(required = false) String q, Model model) {
        model.addAttribute("customers", customers.findCustomers(user.getUserId(), q));
        model.addAttribute("keyword", q == null ? "" : q.strip());
        return "admin/customers";
    }

    @PostMapping("/{id}/lock")
    String lock(@PathVariable Long id, @RequestParam(required = false) String reason,
            @RequestParam(required = false) String q, @AuthenticationPrincipal PortalUser user, RedirectAttributes flash) {
        try {
            customers.lock(user.getUserId(), id, reason);
            flash.addFlashAttribute("success", "Đã khóa tài khoản Customer.");
        } catch (IllegalArgumentException | IllegalStateException failure) {
            flash.addFlashAttribute("error", failure.getMessage());
        }
        return redirectToList(q, flash);
    }

    @PostMapping("/{id}/unlock")
    String unlock(@PathVariable Long id, @RequestParam(required = false) String q,
            @AuthenticationPrincipal PortalUser user, RedirectAttributes flash) {
        try {
            customers.unlock(user.getUserId(), id);
            flash.addFlashAttribute("success", "Đã mở khóa tài khoản Customer.");
        } catch (IllegalArgumentException | IllegalStateException failure) {
            flash.addFlashAttribute("error", failure.getMessage());
        }
        return redirectToList(q, flash);
    }

    private static String redirectToList(String keyword, RedirectAttributes flash) {
        if (keyword != null && !keyword.isBlank()) flash.addAttribute("q", keyword.strip());
        return "redirect:/admin/customers";
    }
}
