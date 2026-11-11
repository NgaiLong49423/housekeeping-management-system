package com.housekeeping.management.service;

import com.housekeeping.management.domain.LeaveStatus;
import com.housekeeping.management.domain.UserRole;
import com.housekeeping.management.domain.UserStatus;
import com.housekeeping.management.dto.request.PortalForms.LeaveRequestForm;
import com.housekeeping.management.dto.response.PortalViews.LeaveCard;
import com.housekeeping.management.entity.Staff;
import com.housekeeping.management.entity.StaffLeave;
import com.housekeeping.management.entity.User;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.springframework.security.access.AccessDeniedException;

public interface LeaveManagementService {

    public List<LeaveCard> findForStaff(Long staffId);
    public List<LeaveCard> findAllForAdmin(Long adminUserId);
    public void submitLeaveRequest(Long staffId, LeaveRequestForm form);
    public void reviewLeaveRequest(Long leaveId, Long adminUserId, LeaveStatus decision);}
