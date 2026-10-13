package com.housekeeping.management.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Nationalized;

@Entity
@Table(name = "Feedback", uniqueConstraints = @UniqueConstraint(name = "UQ_Feedback_Booking", columnNames = "booking_id"))
@Getter
@Setter
@NoArgsConstructor
public class Feedback {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "feedback_id", nullable = false)
    private Long feedbackId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "booking_id", nullable = false, foreignKey = @ForeignKey(name = "FK_Feedback_Booking"))
    private Booking booking;

    @Column(name = "rating", nullable = false, columnDefinition = "tinyint")
    private Byte rating;

    @Nationalized
    @Column(name = "comment", length = 2000, nullable = false)
    private String comment;

    @Column(name = "created_at", nullable = false, columnDefinition = "datetimeoffset(0)")
    private OffsetDateTime createdAt;
}
