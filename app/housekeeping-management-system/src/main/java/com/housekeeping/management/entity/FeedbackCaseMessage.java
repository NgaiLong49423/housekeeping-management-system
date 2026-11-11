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
@Table(name = "Feedback_Case_Messages", schema = "dbo")
@Getter @Setter @NoArgsConstructor
public class FeedbackCaseMessage {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "message_id", nullable = false)
    private Long messageId;
    @Column(name = "case_id", nullable = false)
    private Long caseId;
    @Column(name = "sender_user_id")
    private Long senderUserId;
    @Column(name = "sender_role", nullable = false, length = 20, columnDefinition = "varchar(20)")
    private String senderRole;
    @Nationalized @Column(name = "message_text", columnDefinition = "nvarchar(max)")
    private String messageText;
    @Column(name = "sent_at", nullable = false)
    private LocalDateTime sentAt;
    @Column(name = "legacy_imported", nullable = false)
    private boolean legacyImported;
    @Column(name = "legacy_timestamp_unknown", nullable = false)
    private boolean legacyTimestampUnknown;
}
