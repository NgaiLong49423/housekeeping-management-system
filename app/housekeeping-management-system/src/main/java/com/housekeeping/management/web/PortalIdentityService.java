package com.housekeeping.management.web;

import com.housekeeping.management.service.AuthService;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;

/** Compatibility adapter for the existing MVC test fixture; authentication uses AuthService directly. */
@Deprecated(forRemoval = true)
public class PortalIdentityService implements UserDetailsService {
    private final AuthService authentication;

    public PortalIdentityService(AuthService authentication) {
        this.authentication = authentication;
    }

    @Override
    public UserDetails loadUserByUsername(String email) {
        return authentication.loadUserByUsername(email);
    }
}
