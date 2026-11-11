package com.housekeeping.management.repository;

import com.housekeeping.management.entity.StaffIncidentActionEvidence;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StaffIncidentActionEvidenceRepository extends JpaRepository<StaffIncidentActionEvidence, Long> {
    List<StaffIncidentActionEvidence> findAllByActionIdOrderByUploadedAtAscEvidenceIdAsc(Long actionId);
    List<StaffIncidentActionEvidence> findAllByActionIdAndEvidenceStageOrderByUploadedAtAscEvidenceIdAsc(Long actionId, String evidenceStage);
    long countByActionIdAndEvidenceStage(Long actionId, String evidenceStage);
}
