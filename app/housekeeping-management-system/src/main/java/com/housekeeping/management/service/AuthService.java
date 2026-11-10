package com.housekeeping.management.service;

import com.housekeeping.management.domain.UserRole;
import com.housekeeping.management.domain.UserStatus;
import com.housekeeping.management.entity.User;
import com.housekeeping.management.web.PortalUser;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

public interface AuthService extends UserDetailsService {

    public UserDetails loadUserByUsername(String email);
    User findActiveCustomer(Long customerId);
    User findActiveCustomerForUpdate(Long customerId);
    User findActiveAdmin(Long userId);
}
