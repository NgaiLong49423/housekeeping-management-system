package com.housekeeping.management.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
@Table(name = "Services")
@Getter
@Setter
@NoArgsConstructor
public class Services {
    public enum Status { ACTIVE, INACTIVE }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "service_id", nullable = false)
    private Long serviceId;

    @Nationalized
    @Column(name = "service_name", length = 150, nullable = false)
    private String serviceName;

    @Nationalized
    @Column(name = "description", columnDefinition = "nvarchar(max)")
    private String description;

    @Nationalized
    @Column(name = "image_url", length = 500)
    private String imageUrl;

    @Column(name = "duration_minutes", nullable = false)
    private Integer durationMinutes;

    @Column(name = "price", precision = 12, scale = 2, nullable = false)
    private BigDecimal price;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false, columnDefinition = "varchar(20)")
    private Status status = Status.ACTIVE;

    /** Legacy booking code alias retained while its vertical slice is being migrated. */
    @jakarta.persistence.Transient
    public BigDecimal getBasePrice() { return price; }

    public void setBasePrice(BigDecimal basePrice) { this.price = basePrice; }
}
