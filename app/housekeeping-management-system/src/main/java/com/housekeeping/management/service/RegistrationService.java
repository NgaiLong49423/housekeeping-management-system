package com.housekeeping.management.service;

import com.housekeeping.management.domain.UserRole;
import com.housekeeping.management.domain.UserStatus;
import com.housekeeping.management.dto.request.PortalForms.CustomerRegistrationForm;
import com.housekeeping.management.entity.User;
import jakarta.persistence.EntityManager;
import org.springframework.security.crypto.password.PasswordEncoder;

public interface RegistrationService {

    public void registerCustomer(CustomerRegistrationForm form);}
