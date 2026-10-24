package com.housekeeping.management.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Embeddable
@Getter @Setter @NoArgsConstructor @EqualsAndHashCode
public class StaffFollowId implements Serializable {
    private static final long serialVersionUID = 1L;

    @Column(name = "customer_id", nullable = false)
    private Long customerId;

    @Column(name = "staff_id", nullable = false)
    private Long staffId;

    public StaffFollowId(Long customerId, Long staffId) {
        this.customerId = customerId;
        this.staffId = staffId;
    }
}
