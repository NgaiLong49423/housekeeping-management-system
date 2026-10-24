package com.housekeeping.management.controller;

import com.housekeeping.management.dto.request.PortalForms.CustomerRegistrationForm;
import com.housekeeping.management.service.DuplicateEmailException;
import com.housekeeping.management.service.RegistrationService;
import jakarta.validation.Valid;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;

@Controller
public class AuthController {
    private final RegistrationService registration;

    public AuthController(RegistrationService registration) {
        this.registration = registration;
    }

    @GetMapping("/login")
    String login() {
        return "login";
    }

    @GetMapping("/register")
    String registration(Model model) {
        if (!model.containsAttribute("registrationForm")) {
            model.addAttribute("registrationForm", new CustomerRegistrationForm("", "", "", "", ""));
        }
        return "register";
    }

    @PostMapping("/register")
    String register(@Valid @ModelAttribute("registrationForm") CustomerRegistrationForm form,
            BindingResult errors) {
        if (!Objects.equals(form.password(), form.confirmPassword())) {
            errors.rejectValue("confirmPassword", "mismatch", "Mật khẩu nhập lại chưa khớp.");
        }
        if (form.password() != null && form.password().getBytes(StandardCharsets.UTF_8).length > 72) {
            errors.rejectValue("password", "bcryptLength", "Mật khẩu không được vượt quá 72 byte UTF-8.");
        }
        if (errors.hasErrors()) return "register";
        try {
            registration.registerCustomer(form);
            return "redirect:/login?registered";
        } catch (DuplicateEmailException duplicateEmail) {
            errors.rejectValue("email", "duplicate", "Email này đã được đăng ký. Hãy đăng nhập hoặc dùng email khác.");
            return "register";
        }
    }
}
