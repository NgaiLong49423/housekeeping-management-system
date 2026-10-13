package com.housekeeping.management.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class FavoriteStaffId implements Serializable {
    @Column(name = "customer_id", nullable = false)
    private Long customerId;

    @Column(name = "staff_id", nullable = false)
    private Long staffId;
}
