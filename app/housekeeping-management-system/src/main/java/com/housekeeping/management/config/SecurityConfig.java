package com.housekeeping.management.config;

import com.housekeeping.management.service.AccountAuthorizationService;
import com.housekeeping.management.service.AuthService;
import com.housekeeping.management.web.PortalUser;
import jakarta.servlet.DispatcherType;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.authorization.AuthorizationDecision;

@Configuration
@EnableWebSecurity
public class SecurityConfig {
    @Bean
    DaoAuthenticationProvider portalAuthenticationProvider(AuthService identities, PasswordEncoder encoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(identities);
        provider.setPasswordEncoder(encoder);
        return provider;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        BCryptPasswordEncoder bcrypt = new BCryptPasswordEncoder();
        DelegatingPasswordEncoder encoder = new DelegatingPasswordEncoder("bcrypt", java.util.Map.of("bcrypt", bcrypt));
        encoder.setDefaultPasswordEncoderForMatches(bcrypt);
        return encoder;
    }

    @Bean
    SecurityFilterChain portalSecurity(HttpSecurity http, AccountAuthorizationService authorization) throws Exception {
        return http.authorizeHttpRequests(access -> access
                    .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                    .requestMatchers("/", "/services", "/login", "/register", "/css/**", "/js/**", "/images/**", "/vendor/**", "/error").permitAll()
                    // VNPay redirects the browser back and calls the IPN with GET requests, so CSRF does not apply.
                    .requestMatchers("/payments/vnpay/return", "/payments/vnpay/ipn").permitAll()
                    .requestMatchers("/customer/**").hasRole("CUSTOMER")
                    .requestMatchers("/staff/**").hasRole("STAFF")
                    .requestMatchers("/admin/**").access((authentication, context) -> {
                        var principal = authentication.get().getPrincipal();
                        return new AuthorizationDecision(principal instanceof PortalUser user
                                && user.isAdmin() && authorization.isActiveAdmin(user.getUserId()));
                    })
                    .anyRequest().authenticated())
                .formLogin(login -> login.loginPage("/login").usernameParameter("email")
                    .defaultSuccessUrl("/portal").failureUrl("/login?error").permitAll())
                .logout(logout -> logout.logoutSuccessUrl("/login?logout"))
                .build();
    }
}
