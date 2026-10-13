package com.housekeeping.management.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "Work_Schedule")
@Getter
@Setter
@NoArgsConstructor
public class WorkSchedule {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "work_schedule_id", nullable = false)
    private Long workScheduleId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "staff_id", nullable = false, foreignKey = @jakarta.persistence.ForeignKey(name = "FK_Work_Schedule_Staff"))
    private Staff staff;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "managed_by_admin_id", nullable = false, foreignKey = @jakarta.persistence.ForeignKey(name = "FK_Work_Schedule_Admin"))
    private Admin managedByAdmin;

    @Column(name = "available_start", nullable = false, columnDefinition = "datetimeoffset(0)")
    private OffsetDateTime availableStart;

    @Column(name = "available_end", nullable = false, columnDefinition = "datetimeoffset(0)")
    private OffsetDateTime availableEnd;
}
