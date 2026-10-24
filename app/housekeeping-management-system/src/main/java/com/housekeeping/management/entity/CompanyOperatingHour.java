package com.housekeeping.management.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "Company_Operating_Hours", schema = "dbo",
        uniqueConstraints = @UniqueConstraint(name = "UQ_Company_Operating_Hours_Company_Day", columnNames = {"company_id", "day_of_week"}))
@Getter @Setter @NoArgsConstructor
public class CompanyOperatingHour {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "operating_hour_id", nullable = false)
    private Long operatingHourId;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "day_of_week", nullable = false)
    private Integer dayOfWeek;

    @Column(name = "opening_time")
    private LocalTime openingTime;

    @Column(name = "closing_time")
    private LocalTime closingTime;

    @Column(name = "is_closed", nullable = false)
    private boolean closed;
}
