package com.housekeeping.management.service;

import org.springframework.web.multipart.MultipartFile;

public interface EvidenceStorageService {
    Long upload(Long bookingId, Long staffId, MultipartFile upload);
}
