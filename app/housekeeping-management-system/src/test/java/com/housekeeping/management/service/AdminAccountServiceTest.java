package com.housekeeping.management.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import com.housekeeping.management.dto.request.PortalForms.AdminAccountForm;
import com.housekeeping.management.enums.UserRole;
import com.housekeeping.management.enums.UserStatus;
import com.housekeeping.management.repository.AdminAccountRepository;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;

class AdminAccountServiceTest {
    private final AccountAuthorizationService authorization = mock(AccountAuthorizationService.class);
    private final AdminAccountRepository accounts = mock(AdminAccountRepository.class);
    private final GlobalEmailUniquenessService emails = mock(GlobalEmailUniquenessService.class);
    private final PasswordEncoder passwords = mock(PasswordEncoder.class);
    private final AdminAccountService service = new com.housekeeping.management.service.impl.AdminAccountServiceImpl(authorization, accounts, emails, passwords);

    @Test
    void onlyManagerCanReadOrCreateAdminAccounts() {
        doThrow(new AccessDeniedException("manager required")).when(authorization).requireManager(12L);

        assertThatThrownBy(() -> service.findAll(12L)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.create(12L,
                new AdminAccountForm("New Admin", "new-admin@example.test", "0900000000", "password123")))
                .isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(accounts, emails, passwords);
    }

    @Test
    void managerMayCreateAdminWithCanonicalEmailAndEncodedPassword() {
        org.mockito.Mockito.when(passwords.encode("password123")).thenReturn("bcrypt-hash");
        org.mockito.Mockito.when(emails.executeWithUniqueEmail(eq("New-Admin@Example.Test"),
                org.mockito.ArgumentMatchers.<Function<String, Long>>any())).thenAnswer(invocation -> {
                    Function<String, Long> persist = invocation.getArgument(1);
                    return persist.apply("new-admin@example.test");
                });

        service.create(1L, new AdminAccountForm(" New Admin ", "New-Admin@Example.Test", " 0900000000 ", "password123"));

        org.mockito.Mockito.verify(accounts).save(org.mockito.ArgumentMatchers.argThat(user ->
                "New Admin".equals(user.getFullName())
                        && "new-admin@example.test".equals(user.getEmail())
                        && "bcrypt-hash".equals(user.getPasswordHash())
                        && UserRole.ADMIN == user.getRole()
                        && UserStatus.ACTIVE == user.getStatus()));
    }
}
