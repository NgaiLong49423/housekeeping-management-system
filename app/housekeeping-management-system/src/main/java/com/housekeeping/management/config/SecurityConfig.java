package com.housekeeping.management.config;

import jakarta.servlet.DispatcherType;
import com.housekeeping.management.service.AuthService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import com.housekeeping.management.service.AuthService;

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
    SecurityFilterChain portalSecurity(HttpSecurity http) throws Exception {
        return http.authorizeHttpRequests(access -> access
                    .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                    .requestMatchers("/", "/services", "/login", "/register", "/css/**", "/js/**", "/images/**", "/vendor/**", "/error").permitAll()
                    .requestMatchers("/payments/payos/webhook", "/payments/payos/return", "/payments/payos/cancel").permitAll()
                    .requestMatchers("/customer/**").hasRole("CUSTOMER")
                    .requestMatchers("/staff/**").hasRole("STAFF")
                    .requestMatchers("/admin/**").hasRole("ADMIN")
                    .anyRequest().authenticated())
                .csrf(csrf -> csrf.ignoringRequestMatchers("/payments/payos/webhook"))
                .formLogin(login -> login.loginPage("/login").usernameParameter("email")
                    .defaultSuccessUrl("/portal", true).failureUrl("/login?error").permitAll())
                .logout(logout -> logout.logoutSuccessUrl("/login?logout"))
                .build();
    }
}
