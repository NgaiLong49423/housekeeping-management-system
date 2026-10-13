package com.housekeeping.management.domain.entity;

import com.housekeeping.management.domain.BookingStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
@Table(name = "Booking")
@Getter
@Setter
@NoArgsConstructor
public class Booking {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "booking_id", nullable = false)
    private Long bookingId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false, foreignKey = @jakarta.persistence.ForeignKey(name = "FK_Booking_Customer"))
    private Customer customer;

    @Column(name = "start_at", nullable = false, columnDefinition = "datetimeoffset(0)")
    private OffsetDateTime startAt;

    @Column(name = "end_at", nullable = false, columnDefinition = "datetimeoffset(0)")
    private OffsetDateTime endAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "booking_status", length = 20, nullable = false, columnDefinition = "varchar(20)")
    private BookingStatus bookingStatus;

    @Column(name = "created_at", nullable = false, columnDefinition = "datetimeoffset(0)")
    private OffsetDateTime createdAt;
}
