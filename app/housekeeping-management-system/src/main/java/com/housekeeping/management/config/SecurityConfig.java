package com.housekeeping.management.config;

import com.housekeeping.management.service.AccountAuthorizationService;
import com.housekeeping.management.service.AuthService;
import com.housekeeping.management.web.LockedAccountLoginFailureHandler;
import com.housekeeping.management.web.PortalUser;
import org.springframework.security.authentication.AccountExpiredException;
import org.springframework.security.authentication.CredentialsExpiredException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
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
        // Mặc định Spring kiểm tra "bị khóa" trước khi so mật khẩu, làm lộ trạng thái tài khoản cho người
        // đoán mật khẩu. Ở đây chỉ báo khóa (kèm lý do) sau khi mật khẩu đã đúng.
        provider.setPreAuthenticationChecks(user -> { });
        provider.setPostAuthenticationChecks(user -> {
            if (!user.isAccountNonLocked()) {
                String reason = user instanceof PortalUser portalUser ? portalUser.getLockReason() : null;
                throw new LockedException(reason == null ? "" : reason);
            }
            if (!user.isEnabled()) throw new DisabledException("Tài khoản không hoạt động.");
            if (!user.isAccountNonExpired()) throw new AccountExpiredException("Tài khoản đã hết hạn.");
            if (!user.isCredentialsNonExpired()) throw new CredentialsExpiredException("Mật khẩu đã hết hạn.");
        });
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
                    .requestMatchers("/manager/**").access((authentication, context) -> {
                        var principal = authentication.get().getPrincipal();
                        return new AuthorizationDecision(principal instanceof PortalUser user
                                && "MANAGER".equals(user.getRole()) && authorization.isActiveAdmin(user.getUserId()));
                    })
                    .requestMatchers("/admin/**").access((authentication, context) -> {
                        var principal = authentication.get().getPrincipal();
                        return new AuthorizationDecision(principal instanceof PortalUser user
                                && user.isAdmin() && authorization.isActiveAdmin(user.getUserId()));
                    })
                    .anyRequest().authenticated())
                .formLogin(login -> login.loginPage("/login").usernameParameter("email")
                    .defaultSuccessUrl("/portal").failureHandler(new LockedAccountLoginFailureHandler()).permitAll())
                .logout(logout -> logout.logoutSuccessUrl("/login?logout"))
                .build();
    }
}
