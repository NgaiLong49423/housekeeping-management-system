package com.housekeeping.management.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Nationalized;

@Entity
@Table(name = "Customer", uniqueConstraints = @UniqueConstraint(name = "UQ_Customer_Email", columnNames = "email"))
@Getter
@Setter
@NoArgsConstructor
public class Customer {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "customer_id", nullable = false)
    private Long customerId;

    @Nationalized
    @Column(name = "display_name", length = 150, nullable = false)
    private String displayName;

    @Nationalized
    @Column(name = "email", length = 254, nullable = false, columnDefinition = "nvarchar(254) collate Latin1_General_100_CI_AS")
    private String email;

    @Nationalized
    @Column(name = "password_hash", length = 255, nullable = false)
    private String passwordHash;

    @Nationalized
    @Column(name = "phone_number", length = 20)
    private String phoneNumber;
}
