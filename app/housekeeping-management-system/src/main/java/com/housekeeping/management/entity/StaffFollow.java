package com.housekeeping.management.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "Staff_Follows", schema = "dbo")
@Getter @Setter @NoArgsConstructor
public class StaffFollow {
    @EmbeddedId
    private StaffFollowId id;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
