package com.housekeeping.management.service.impl;

import com.housekeeping.management.service.StaffIncidentNoticeService;
import com.housekeeping.management.service.FeedbackCaseService;

import com.housekeeping.management.dto.response.PortalViews.StaffActionNoticeCard;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StaffIncidentNoticeServiceImpl implements StaffIncidentNoticeService {
    private final FeedbackCaseService cases;
    public StaffIncidentNoticeServiceImpl(FeedbackCaseService cases) { this.cases=cases; }
    public List<StaffActionNoticeCard> ownApprovedActions(Long staffId) { return cases.approvedActionsForStaff(staffId); }
    public void acknowledge(Long actionId, Long staffId) {
        cases.acknowledgeStaffAction(actionId, staffId);
    }
    public void respond(Long actionId, Long staffId, String response, boolean appeal) {
        cases.respondToStaffAction(actionId, staffId, response, appeal);
    }
}
