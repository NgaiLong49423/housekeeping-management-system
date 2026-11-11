package com.housekeeping.management.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Nationalized;

@Entity @Table(name="Feedback_Resolution_Options", schema="dbo") @Getter @Setter @NoArgsConstructor
public class FeedbackResolutionOption {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) @Column(name="option_id") private Long optionId;
    @Column(name="plan_id", nullable=false) private Long planId;
    @Column(name="option_no", nullable=false) private Integer optionNo;
    @Nationalized @Column(nullable=false, length=150) private String title;
    @Nationalized @Column(nullable=false, length=2000) private String description;
}
