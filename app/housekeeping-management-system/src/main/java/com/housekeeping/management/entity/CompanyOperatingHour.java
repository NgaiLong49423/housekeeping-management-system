package com.housekeeping.management.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalTime;
import java.time.LocalDate;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "Company_Operating_Hours", schema = "dbo")
@Getter @Setter @NoArgsConstructor
public class CompanyOperatingHour {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "operating_hour_id", nullable = false)
    private Long operatingHourId;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom;

    @Column(name = "day_of_week", nullable = false)
    private Integer dayOfWeek;

    @Column(name = "period_no", nullable = false)
    private Short periodNo;

    @Column(name = "opening_time")
    private LocalTime openingTime;

    @Column(name = "closing_time")
    private LocalTime closingTime;

    @Column(name = "is_closed", nullable = false)
    private boolean closed;
}
