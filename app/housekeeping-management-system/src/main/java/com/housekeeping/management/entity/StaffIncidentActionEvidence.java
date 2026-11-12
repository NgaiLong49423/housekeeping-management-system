package com.housekeeping.management.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Nationalized;

@Entity
@Table(name = "Staff_Incident_Action_Evidence", schema = "dbo")
@Getter @Setter @NoArgsConstructor
public class StaffIncidentActionEvidence {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "evidence_id", nullable = false) private Long evidenceId;
    @Column(name = "action_id", nullable = false) private Long actionId;
    @Column(name = "uploaded_by", nullable = false) private Long uploadedBy;
    @Column(name = "evidence_stage", nullable = false, length = 20, columnDefinition = "varchar(20)") private String evidenceStage;
    @Column(name = "media_type", nullable = false, length = 20, columnDefinition = "varchar(20)") private String mediaType;
    @Column(name = "content_type", length = 100) private String contentType;
    @Nationalized @Column(name = "original_name", length = 255) private String originalName;
    @Nationalized @Column(name = "storage_path", nullable = false, length = 500) private String storagePath;
    @Column(name = "uploaded_at", nullable = false) private LocalDateTime uploadedAt;
}
