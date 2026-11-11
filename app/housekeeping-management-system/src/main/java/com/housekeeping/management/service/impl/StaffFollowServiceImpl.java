package com.housekeeping.management.service.impl;

import com.housekeeping.management.service.StaffFollowService;
import com.housekeeping.management.service.StaffManagementService;
import com.housekeeping.management.service.AuthService;
import com.housekeeping.management.entity.StaffFollow;
import com.housekeeping.management.entity.StaffFollowId;
import com.housekeeping.management.repository.StaffFollowRepository;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StaffFollowServiceImpl implements StaffFollowService {
    private final StaffManagementService staffManagement;
    private final AuthService auth;
    private final StaffFollowRepository follows;

    public StaffFollowServiceImpl(StaffManagementService staffManagement, AuthService auth, StaffFollowRepository follows) {
        this.staffManagement = staffManagement;
        this.auth = auth;
        this.follows = follows;
    }

    @Transactional
    public void setFollowing(Long staffId, Long customerId, boolean following) {
        staffManagement.findActiveByStaffId(staffId);
        auth.findActiveCustomerForUpdate(customerId);
        StaffFollowId id = new StaffFollowId(customerId, staffId);
        if (following) {
            if (!follows.existsById(id)) {
                StaffFollow follow = new StaffFollow();
                follow.setId(id);
                follow.setCreatedAt(LocalDateTime.now(ZoneOffset.ofHours(7)).withNano(0));
                follows.save(follow);
            }
            return;
        }
        follows.deleteById(id);
    }
}
