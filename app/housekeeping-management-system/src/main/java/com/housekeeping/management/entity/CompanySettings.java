package com.housekeeping.management.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Nationalized;

@Entity
@Table(name = "Company_Settings", schema = "dbo")
@Getter @Setter @NoArgsConstructor
public class CompanySettings {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Nationalized @Column(name = "company_address", length = 500, nullable = false)
    private String companyAddress;

    @Column(name = "latitude", precision = 10, scale = 7, nullable = false)
    private BigDecimal latitude;

    @Column(name = "longitude", precision = 10, scale = 7, nullable = false)
    private BigDecimal longitude;

    @Column(name = "service_radius_km", precision = 6, scale = 2, nullable = false)
    private BigDecimal serviceRadiusKm;
}
