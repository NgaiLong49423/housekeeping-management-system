package com.housekeeping.management.service;

import com.housekeeping.management.dto.request.PortalForms.ServiceForm;
import com.housekeeping.management.entity.Services;
import com.housekeeping.management.repository.ServicesRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ServiceManagementService {
    private final ServicesRepository services;

    public ServiceManagementService(ServicesRepository services) { this.services = services; }

    @Transactional
    public void save(Long serviceId, ServiceForm form) {
        Services service = serviceId == null ? new Services()
                : findService(serviceId);
        service.setServiceName(form.name().trim());
        service.setDescription(form.description());
        service.setDurationMinutes(form.duration());
        service.setPrice(form.price());
        if (service.getStatus() == null) service.setStatus(Services.Status.ACTIVE);
        services.saveAndFlush(service);
    }

    @Transactional
    public void deactivate(Long serviceId) { findService(serviceId).setStatus(Services.Status.INACTIVE); }

    @Transactional
    public void activate(Long serviceId) { findService(serviceId).setStatus(Services.Status.ACTIVE); }

    private Services findService(Long serviceId) {
        return services.findById(serviceId)
                .orElseThrow(() -> new IllegalArgumentException("Dịch vụ không tồn tại."));
    }
}
