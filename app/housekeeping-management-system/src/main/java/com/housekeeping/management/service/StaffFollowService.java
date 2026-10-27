package com.housekeeping.management.service;

import com.housekeeping.management.domain.UserRole;
import com.housekeeping.management.domain.UserStatus;
import com.housekeeping.management.entity.Staff;
import com.housekeeping.management.entity.User;
import com.housekeeping.management.repository.StaffAccountRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StaffFollowService {
    private final StaffAccountRepository staffAccounts;
    private final EntityManager entities;
    private final JdbcTemplate jdbc;

    public StaffFollowService(StaffAccountRepository staffAccounts, EntityManager entities, JdbcTemplate jdbc) {
        this.staffAccounts = staffAccounts;
        this.entities = entities;
        this.jdbc = jdbc;
    }

    @Transactional
    public void setFollowing(Long staffId, Long customerId, boolean following) {
        Staff staff = staffAccounts.findById(staffId)
                .filter(item -> item.getUser().getStatus() == UserStatus.ACTIVE)
                .orElseThrow(() -> new IllegalArgumentException("Nhân viên không tồn tại hoặc không hoạt động."));
        User customer = entities.find(User.class, customerId, LockModeType.PESSIMISTIC_WRITE);
        if (customer == null || customer.getRole() != UserRole.CUSTOMER || customer.getStatus() != UserStatus.ACTIVE) {
            throw new AccessDeniedException("Tài khoản Customer không còn hợp lệ.");
        }
        if (following) {
            jdbc.update("""
                    IF NOT EXISTS (SELECT 1 FROM dbo.Staff_Follows WITH (UPDLOCK, HOLDLOCK)
                                   WHERE customer_id=? AND staff_id=?)
                        INSERT INTO dbo.Staff_Follows (customer_id, staff_id) VALUES (?, ?)
                    """, customerId, staff.getStaffId(), customerId, staff.getStaffId());
        } else {
            jdbc.update("DELETE FROM dbo.Staff_Follows WHERE customer_id=? AND staff_id=?", customerId, staffId);
        }
    }
}
