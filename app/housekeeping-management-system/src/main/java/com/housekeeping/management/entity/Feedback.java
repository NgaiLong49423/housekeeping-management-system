package com.housekeeping.management.entity;

import com.housekeeping.management.domain.FeedbackStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Nationalized;

@Entity
@Table(name = "Feedbacks", schema = "dbo")
@Getter @Setter @NoArgsConstructor
public class Feedback {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "feedback_id", nullable = false)
    private Long feedbackId;

    @Column(name = "booking_id", nullable = false, unique = true)
    private Long bookingId;

    @Column(name = "customer_id", nullable = false)
    private Long customerId;

    @Nationalized @Column(name = "content", nullable = false, columnDefinition = "nvarchar(max)")
    private String content;

    @Nationalized @Column(name = "admin_reply", columnDefinition = "nvarchar(max)")
    private String adminReply;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false, columnDefinition = "varchar(20)")
    private FeedbackStatus status = FeedbackStatus.OPEN;
}
