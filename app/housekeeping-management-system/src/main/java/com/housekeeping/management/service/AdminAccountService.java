package com.housekeeping.management.service;

import com.housekeeping.management.dto.request.PortalForms.AdminAccountForm;
import com.housekeeping.management.dto.response.PortalViews.AdminAccountCard;
import java.util.List;
import org.springframework.security.crypto.password.PasswordEncoder;

public interface AdminAccountService {

    public List<AdminAccountCard> findAll(Long managerId);
    public void create(Long managerId, AdminAccountForm form);
    public void updateStatus(Long managerId, Long adminId, String status);}
