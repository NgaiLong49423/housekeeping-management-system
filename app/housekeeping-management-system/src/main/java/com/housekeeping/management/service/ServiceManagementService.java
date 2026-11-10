package com.housekeeping.management.service;

import com.housekeeping.management.dto.request.PortalForms.ServiceForm;
import com.housekeeping.management.entity.Services;
import java.util.List;

public interface ServiceManagementService {

    public void save(Long adminId, Long serviceId, ServiceForm form);
    public void deactivate(Long adminId, Long serviceId);
    public void activate(Long adminId, Long serviceId);
    List<Services> findActiveByIds(List<Long> serviceIds);
    List<Services> findByIds(List<Long> serviceIds);
}
