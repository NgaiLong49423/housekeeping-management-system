package com.housekeeping.management.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Nationalized;

@Entity
@Table(name = "Evidences", uniqueConstraints = @UniqueConstraint(name = "UQ_Evidences_Storage_Path", columnNames = "storage_path"))
@Getter
@Setter
@NoArgsConstructor
public class Evidences {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "evidence_id", nullable = false)
    private Long evidenceId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "booking_detail_id", nullable = false, foreignKey = @ForeignKey(name = "FK_Evidences_Booking_Detail"))
    private BookingDetail bookingDetail;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "staff_id", nullable = false, foreignKey = @ForeignKey(name = "FK_Evidences_Staff"))
    private Staff staff;

    @Nationalized
    @Column(name = "storage_path", length = 500, nullable = false)
    private String storagePath;

    @Column(name = "file_size_bytes", nullable = false)
    private Long fileSizeBytes;

    @Column(name = "uploaded_at", nullable = false, columnDefinition = "datetimeoffset(0)")
    private OffsetDateTime uploadedAt;
}
