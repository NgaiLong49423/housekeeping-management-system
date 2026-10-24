package com.housekeeping.management.controller;

import com.housekeeping.management.service.PortalQueryService;
import com.housekeeping.management.web.PortalUser;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class PortalController {
    private final PortalQueryService query;

    public PortalController(PortalQueryService query) {
        this.query = query;
    }

    @GetMapping("/")
    String home(Model model) { model.addAttribute("services", query.services()); return "home"; }

    @GetMapping("/services")
    String services(Model model) { model.addAttribute("services", query.services()); return "services"; }

    @GetMapping("/portal")
    String portal(@AuthenticationPrincipal PortalUser user) {
        return "redirect:" + switch (user.getRole()) { case "ADMIN" -> "/admin/bookings"; case "STAFF" -> "/staff/bookings"; default -> "/customer/bookings"; };
    }

}
