package com.housekeeping.management.service.impl;

import com.housekeeping.management.service.AccountAuthorizationService;
import com.housekeeping.management.service.ServiceManagementService;

import com.housekeeping.management.dto.request.PortalForms.ServiceForm;
import com.housekeeping.management.dto.entity.ServicesDTO;
import com.housekeeping.management.entity.Services;
import com.housekeeping.management.repository.ServicesRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ServiceManagementServiceImpl implements ServiceManagementService {
    private final ServicesRepository services;
    private final AccountAuthorizationService authorization;

    public ServiceManagementServiceImpl(ServicesRepository services, AccountAuthorizationService authorization) {
        this.services = services;
        this.authorization = authorization;
    }

    @Transactional
    public void save(Long adminId, Long serviceId, ServiceForm form) {
        authorization.requireActiveAdmin(adminId);
        Services service = serviceId == null ? null : findService(serviceId);
        Services.Status status = service == null || service.getStatus() == null
                ? Services.Status.ACTIVE : service.getStatus();
        String description = form.description() == null || form.description().isBlank() ? null : form.description().trim();
        ServicesDTO dto = new ServicesDTO(service == null ? null : service.getServiceId(), form.name().trim(),
                description, form.imageUrl() == null || form.imageUrl().isBlank() ? null : form.imageUrl().trim(),
                form.duration(), form.price(), status);
        services.saveAndFlush(dto.convertDTOToEntity(service));
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

    @Transactional(readOnly = true)
    public java.util.List<Services> findActiveByIds(java.util.List<Long> serviceIds) {
        if (serviceIds == null || serviceIds.isEmpty()) return java.util.List.of();
        return services.findAllById(serviceIds).stream()
                .filter(service -> service.getStatus() == Services.Status.ACTIVE)
                .toList();
    }

    @Override
    public java.util.List<Services> findByIds(java.util.List<Long> serviceIds) {
        return services.findAllById(serviceIds);
    }

    private Services findService(Long serviceId) {
        return services.findById(serviceId)
                .orElseThrow(() -> new IllegalArgumentException("Dịch vụ không tồn tại."));
    }
}
