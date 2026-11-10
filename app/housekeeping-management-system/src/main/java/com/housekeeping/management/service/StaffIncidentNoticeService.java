package com.housekeeping.management.service;

import com.housekeeping.management.dto.response.PortalViews.StaffActionNoticeCard;
import java.util.List;

public interface StaffIncidentNoticeService {

    public List<StaffActionNoticeCard> ownApprovedActions(Long staffId);
    public void acknowledge(Long actionId, Long staffId);}
