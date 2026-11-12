package com.housekeeping.management.controller;

import com.housekeeping.management.dto.request.PortalForms.CustomerRegistrationForm;
import com.housekeeping.management.dto.request.PortalForms.ChangePasswordForm;
import com.housekeeping.management.exception.DuplicateEmailException;
import com.housekeeping.management.service.AuthService;
import com.housekeeping.management.service.PasswordChangeService;
import com.housekeeping.management.service.RegistrationService;
import com.housekeeping.management.web.LockedAccountLoginFailureHandler;
import com.housekeeping.management.web.PortalUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;

@Controller
public class AuthController {
    private final RegistrationService registration;
    private final PasswordChangeService passwordChanges;
    private final AuthService identities;
    private final SecurityContextRepository securityContexts;

    public AuthController(RegistrationService registration, PasswordChangeService passwordChanges,
            AuthService identities, SecurityContextRepository securityContexts) {
        this.registration = registration;
        this.passwordChanges = passwordChanges;
        this.identities = identities;
        this.securityContexts = securityContexts;
    }

    @GetMapping("/login")
    String login(HttpServletRequest request, Model model) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            Object reason = session.getAttribute(LockedAccountLoginFailureHandler.LOCK_REASON_ATTRIBUTE);
            if (reason != null) {
                session.removeAttribute(LockedAccountLoginFailureHandler.LOCK_REASON_ATTRIBUTE);
                model.addAttribute("lockReason", reason.toString());
            }
        }
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

    @GetMapping("/account/change-password")
    String changePasswordPage(@AuthenticationPrincipal PortalUser user, Model model) {
        if (!passwordChanges.isPasswordChangeRequired(user.getUserId())) return "redirect:/portal";
        model.addAttribute("changePasswordForm", new ChangePasswordForm("", "", ""));
        return "account/change-password";
    }

    @PostMapping("/account/change-password")
    String changePassword(@AuthenticationPrincipal PortalUser user,
            @Valid @ModelAttribute("changePasswordForm") ChangePasswordForm form, BindingResult errors,
            Model model, HttpServletRequest request, HttpServletResponse response) {
        if (!passwordChanges.isPasswordChangeRequired(user.getUserId())) return "redirect:/portal";
        if (errors.hasErrors()) {
            model.addAttribute("error", errors.getAllErrors().getFirst().getDefaultMessage());
            model.addAttribute("changePasswordForm", new ChangePasswordForm("", "", ""));
            return "account/change-password";
        }
        if (form.newPassword().getBytes(StandardCharsets.UTF_8).length > 72) {
            model.addAttribute("error", "Mật khẩu mới không được vượt quá 72 byte UTF-8.");
            model.addAttribute("changePasswordForm", new ChangePasswordForm("", "", ""));
            return "account/change-password";
        }

        try {
            passwordChanges.changePassword(user.getUserId(), form);
        } catch (IllegalArgumentException | IllegalStateException failure) {
            model.addAttribute("error", failure.getMessage());
            model.addAttribute("changePasswordForm", new ChangePasswordForm("", "", ""));
            return "account/change-password";
        }

        UserDetails refreshed = identities.loadUserByUsername(user.getUsername());
        if (!(refreshed instanceof PortalUser refreshedUser)) {
            throw new IllegalStateException("Không thể làm mới phiên đăng nhập sau khi đổi mật khẩu.");
        }
        Authentication previous = SecurityContextHolder.getContext().getAuthentication();
        var updatedAuthentication = new UsernamePasswordAuthenticationToken(
                refreshedUser, null, refreshedUser.getAuthorities());
        updatedAuthentication.setDetails(previous.getDetails());
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(updatedAuthentication);
        SecurityContextHolder.setContext(context);
        securityContexts.saveContext(context, request, response);
        return "redirect:/portal";
    }
}
