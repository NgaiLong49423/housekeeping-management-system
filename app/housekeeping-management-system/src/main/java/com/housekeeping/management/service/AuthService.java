package com.housekeeping.management.service;

import com.housekeeping.management.domain.UserRole;
import com.housekeeping.management.domain.UserStatus;
import com.housekeeping.management.entity.User;
import com.housekeeping.management.repository.StaffAccountRepository;
import com.housekeeping.management.repository.UserRepository;
import com.housekeeping.management.web.PortalUser;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class AuthService implements UserDetailsService {
    private final UserRepository users;
    private final StaffAccountRepository staffAccounts;

    public AuthService(UserRepository users, StaffAccountRepository staffAccounts) {
        this.users = users;
        this.staffAccounts = staffAccounts;
    }

    @Override
    public UserDetails loadUserByUsername(String email) {
        String canonicalEmail = GlobalEmailUniquenessService.canonicalize(email);
        User user = users.findByEmail(canonicalEmail)
                .filter(candidate -> candidate.getStatus() == UserStatus.ACTIVE)
                .orElseThrow(() -> new UsernameNotFoundException("Thông tin đăng nhập không hợp lệ."));

        Long accountId = accountIdFor(user);
        return new PortalUser(accountId, user.getUserId(), user.getEmail(), user.getPasswordHash(),
                user.getFullName(), user.getRole().name());
    }

    private Long accountIdFor(User user) {
        if (user.getRole() == UserRole.STAFF) {
            return staffAccounts.findByUser_UserId(user.getUserId())
                    .map(staff -> staff.getStaffId())
                    .orElseThrow(() -> new UsernameNotFoundException("Thông tin đăng nhập không hợp lệ."));
        }
        return user.getUserId();
    }
}
