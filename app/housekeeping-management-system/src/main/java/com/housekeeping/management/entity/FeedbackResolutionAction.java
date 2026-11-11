package com.housekeeping.management.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Nationalized;

@Entity @Table(name="Feedback_Resolution_Actions", schema="dbo") @Getter @Setter @NoArgsConstructor
public class FeedbackResolutionAction {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) @Column(name="action_id") private Long actionId;
    @Column(name="option_id", nullable=false) private Long optionId;
    @Column(name="action_type", nullable=false, length=20) private String actionType;
    @Nationalized @Column(nullable=false, length=1000) private String description;
    @Column(name="due_at") private LocalDateTime dueAt;
    @Column(precision=12, scale=2) private BigDecimal amount;
    @Nationalized @Column(name="calculation_basis", length=1000) private String calculationBasis;
}
