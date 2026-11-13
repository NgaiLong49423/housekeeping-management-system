package com.housekeeping.management.service.impl;

import com.housekeeping.management.service.AuthService;
import com.housekeeping.management.service.GlobalEmailUniquenessService;
import com.housekeeping.management.service.StaffManagementService;

import com.housekeeping.management.enums.UserRole;
import com.housekeeping.management.enums.UserStatus;
import com.housekeeping.management.entity.User;
import com.housekeeping.management.repository.UserRepository;
import com.housekeeping.management.web.PortalUser;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class AuthServiceImpl implements AuthService {
    private final UserRepository users;
    private final StaffManagementService staffManagement;

    public AuthServiceImpl(UserRepository users, StaffManagementService staffManagement) {
        this.users = users;
        this.staffManagement = staffManagement;
    }

    @Override
    public UserDetails loadUserByUsername(String email) {
        String canonicalEmail = GlobalEmailUniquenessService.canonicalize(email);
        User user = users.findByEmail(canonicalEmail)
                .orElseThrow(() -> new UsernameNotFoundException("Thông tin đăng nhập không hợp lệ."));

        Long accountId = accountIdFor(user);
        // Tài khoản bị khóa vẫn được nạp để kiểm tra mật khẩu; SecurityConfig chỉ từ chối (kèm lý do)
        // sau khi mật khẩu đúng, nên người đoán sai mật khẩu không biết tài khoản có bị khóa hay không.
        boolean locked = user.getStatus() != UserStatus.ACTIVE;
        return new PortalUser(accountId, user.getUserId(), user.getEmail(), user.getPasswordHash(),
                user.getFullName(), user.getRole().name(), locked, user.getStatusReason(),
                user.isMustChangePassword());
    }

    @Override
    public User findActiveCustomer(Long customerId) {
        return users.findById(customerId)
                .filter(user -> user.getRole() == UserRole.CUSTOMER && user.getStatus() == UserStatus.ACTIVE)
                .orElseThrow(() -> new IllegalArgumentException("Tài khoản khách hàng không hợp lệ."));
    }

    @Override
    @org.springframework.transaction.annotation.Transactional
    public User findActiveCustomerForUpdate(Long customerId) {
        return users.findActiveCustomerByIdForUpdate(customerId)
                .orElseThrow(() -> new org.springframework.security.access.AccessDeniedException("Tài khoản Customer không còn hợp lệ."));
    }

    @Override
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public User findActiveAdmin(Long userId) {
        return users.findById(userId)
                .filter(user -> (user.getRole() == UserRole.ADMIN || user.getRole() == UserRole.MANAGER)
                        && user.getStatus() == UserStatus.ACTIVE)
                .orElseThrow(() -> new org.springframework.security.access.AccessDeniedException("Tài khoản Admin không còn hợp lệ."));
    }

    private Long accountIdFor(User user) {
        if (user.getRole() == UserRole.STAFF) {
            return staffManagement.findStaffIdByUserId(user.getUserId())
                    .orElseThrow(() -> new UsernameNotFoundException("Thông tin đăng nhập không hợp lệ."));
        }
        return user.getUserId();
    }
}
