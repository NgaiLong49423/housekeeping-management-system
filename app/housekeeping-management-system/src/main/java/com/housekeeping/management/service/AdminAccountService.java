package com.housekeeping.management.service;

import com.housekeeping.management.dto.request.PortalForms.AdminAccountForm;
import com.housekeeping.management.dto.response.PortalViews.AdminAccountCard;
import java.util.List;
import org.springframework.security.crypto.password.PasswordEncoder;

public interface AdminAccountService {

    public List<AdminAccountCard> findAll(Long managerId);
    public void create(Long managerId, AdminAccountForm form);
    /** Khóa ({@code INACTIVE}, bắt buộc có lý do) hoặc mở khóa ({@code ACTIVE}) tài khoản Admin; chỉ Manager được làm. */
    public void updateStatus(Long managerId, Long adminId, String status, String reason);}
