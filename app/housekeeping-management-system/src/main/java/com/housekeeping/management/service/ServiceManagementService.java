package com.housekeeping.management.service;

import com.housekeeping.management.dto.request.PortalForms.ServiceForm;
import com.housekeeping.management.entity.Services;
import com.housekeeping.management.repository.ServicesRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ServiceManagementService {
    private final ServicesRepository services;
    private final AccountAuthorizationService authorization;

    public ServiceManagementService(ServicesRepository services, AccountAuthorizationService authorization) {
        this.services = services;
        this.authorization = authorization;
    }

    @Transactional
    public void save(Long adminId, Long serviceId, ServiceForm form) {
        authorization.requireActiveAdmin(adminId);
        Services service = serviceId == null ? new Services()
                : findService(serviceId);
        service.setServiceName(form.name().trim());
        service.setDescription(form.description());
        service.setImageUrl(form.imageUrl() == null || form.imageUrl().isBlank() ? null : form.imageUrl().trim());
        service.setDurationMinutes(form.duration());
        service.setPrice(form.price());
        if (service.getStatus() == null) service.setStatus(Services.Status.ACTIVE);
        services.saveAndFlush(service);
    }

    @Transactional
    public void deactivate(Long adminId, Long serviceId) {
        authorization.requireActiveAdmin(adminId);
        findService(serviceId).setStatus(Services.Status.INACTIVE);
    }

    @Transactional
    public void activate(Long adminId, Long serviceId) {
        authorization.requireActiveAdmin(adminId);
        findService(serviceId).setStatus(Services.Status.ACTIVE);
    }

    private Services findService(Long serviceId) {
        return services.findById(serviceId)
                .orElseThrow(() -> new IllegalArgumentException("Dịch vụ không tồn tại."));
    }
}
