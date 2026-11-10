package com.housekeeping.management.service;

import java.nio.file.Path;
import java.util.List;
import org.springframework.web.multipart.MultipartFile;

public interface CaseAttachmentStorageService {
    void uploadCustomerEvidence(Long caseId, Long bookingId, Long customerId, MultipartFile upload);

    void uploadCustomerMessageEvidence(Long caseId, Long bookingId, Long customerId, Long messageId, MultipartFile upload);

    void uploadCustomerMessageEvidence(Long caseId, Long bookingId, Long customerId, Long messageId, List<MultipartFile> uploads);

    void uploadAdminMessageEvidence(Long caseId, Long adminId, Long messageId, MultipartFile upload);

    void uploadAdminMessageEvidence(Long caseId, Long adminId, Long messageId, List<MultipartFile> uploads);

    void uploadReceipt(Long entryId, Long adminId, MultipartFile upload);

    Path customerEvidence(Long evidenceId, Long customerId, boolean admin);

    Path receipt(Long entryId, Long adminId);

    List<Long> customerEvidenceIds(Long caseId, Long customerId);

    List<Long> adminEvidenceIds(Long caseId, Long adminId);
}
