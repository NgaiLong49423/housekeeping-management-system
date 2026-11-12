package com.housekeeping.management.service.impl;

import com.housekeeping.management.service.GlobalEmailUniquenessService;
import com.housekeeping.management.service.RegistrationService;

import com.housekeeping.management.enums.UserRole;
import com.housekeeping.management.enums.UserStatus;
import com.housekeeping.management.dto.request.PortalForms.CustomerRegistrationForm;
import com.housekeeping.management.dto.entity.UserDTO;
import com.housekeeping.management.entity.User;
import jakarta.persistence.EntityManager;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RegistrationServiceImpl implements RegistrationService {
    private final EntityManager entities;
    private final GlobalEmailUniquenessService emails;
    private final PasswordEncoder passwords;

    public RegistrationServiceImpl(EntityManager entities, GlobalEmailUniquenessService emails, PasswordEncoder passwords) {
        this.entities = entities;
        this.emails = emails;
        this.passwords = passwords;
    }

    @Transactional
    public void registerCustomer(CustomerRegistrationForm form) {
        String passwordHash = passwords.encode(form.password());
        emails.executeWithUniqueEmail(form.email(), canonicalEmail -> {
            UserDTO userDto = new UserDTO(null, form.name().trim(), canonicalEmail, form.phone().trim(),
                    UserRole.CUSTOMER, UserStatus.ACTIVE);
            User user = userDto.convertDTOToEntity(passwordHash);
            entities.persist(user);
            return null;
        });
    }
}
