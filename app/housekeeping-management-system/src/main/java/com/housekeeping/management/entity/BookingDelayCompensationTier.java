package com.housekeeping.management.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "Booking_Delay_Compensation_Tiers", schema = "dbo")
@Getter @Setter @NoArgsConstructor
public class BookingDelayCompensationTier {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "tier_id", nullable = false)
    private Integer tierId;
    @Column(name = "min_delay_minutes", nullable = false)
    private Integer minDelayMinutes;
    @Column(name = "max_delay_minutes")
    private Integer maxDelayMinutes;
    @Column(name = "discount_percent", nullable = false)
    private Integer discountPercent;
}
