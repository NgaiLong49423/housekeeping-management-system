package com.housekeeping.management.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "Favorite_Staff")
@Getter
@Setter
@NoArgsConstructor
public class FavoriteStaff {
    @EmbeddedId
    private FavoriteStaffId id;

    @MapsId("customerId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false, foreignKey = @ForeignKey(name = "FK_Favorite_Staff_Customer"))
    private Customer customer;

    @MapsId("staffId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "staff_id", nullable = false, foreignKey = @ForeignKey(name = "FK_Favorite_Staff_Staff"))
    private Staff staff;

    @Column(name = "created_at", nullable = false, columnDefinition = "datetimeoffset(0)")
    private OffsetDateTime createdAt;
}
