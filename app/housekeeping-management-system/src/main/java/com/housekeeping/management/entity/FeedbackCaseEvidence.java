package com.housekeeping.management.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Nationalized;

@Entity
@Table(name = "Feedback_Case_Evidence", schema = "dbo")
@Getter @Setter @NoArgsConstructor
public class FeedbackCaseEvidence {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "evidence_id", nullable = false)
    private Long evidenceId;
    @Column(name = "case_id", nullable = false)
    private Long caseId;
    @Nationalized @Column(name = "image_path", length = 500)
    private String imagePath;
    @Column(name = "uploaded_by", nullable = false)
    private Long uploadedBy;
    @Column(name = "uploaded_at", nullable = false)
    private LocalDateTime uploadedAt;
    @Column(name = "message_id")
    private Long messageId;
    @Column(name = "media_type", nullable=false, length=20)
    private String mediaType = "IMAGE";
    @Column(name = "content_type", length=100)
    private String contentType;
    @Nationalized @Column(name = "original_name", length=255)
    private String originalName;
    @Nationalized @Column(name = "external_url", length=1000)
    private String externalUrl;
}
