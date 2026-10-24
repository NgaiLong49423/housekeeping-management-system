package com.housekeeping.management.service;

import com.housekeeping.management.domain.UserRole;
import com.housekeeping.management.domain.UserStatus;
import com.housekeeping.management.dto.request.PortalForms.CustomerRegistrationForm;
import com.housekeeping.management.entity.User;
import jakarta.persistence.EntityManager;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RegistrationService {
    private final EntityManager entities;
    private final GlobalEmailUniquenessService emails;
    private final PasswordEncoder passwords;

    public RegistrationService(EntityManager entities, GlobalEmailUniquenessService emails, PasswordEncoder passwords) {
        this.entities = entities;
        this.emails = emails;
        this.passwords = passwords;
    }

    @Transactional
    public void registerCustomer(CustomerRegistrationForm form) {
        String passwordHash = passwords.encode(form.password());
        emails.executeWithUniqueEmail(form.email(), canonicalEmail -> {
            User user = new User();
            user.setFullName(form.name().trim());
            user.setEmail(canonicalEmail);
            user.setPasswordHash(passwordHash);
            user.setPhone(form.phone().trim());
            user.setRole(UserRole.CUSTOMER);
            user.setStatus(UserStatus.ACTIVE);
            entities.persist(user);
            return null;
        });
    }
}
