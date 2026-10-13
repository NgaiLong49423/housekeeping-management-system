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
@Table(name = "Admin", uniqueConstraints = @UniqueConstraint(name = "UQ_Admin_Email", columnNames = "email"))
@Getter
@Setter
@NoArgsConstructor
public class Admin {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "admin_id", nullable = false)
    private Long adminId;

    @Nationalized
    @Column(name = "display_name", length = 150, nullable = false)
    private String displayName;

    @Nationalized
    @Column(name = "email", length = 254, nullable = false, columnDefinition = "nvarchar(254) collate Latin1_General_100_CI_AS")
    private String email;

    @Nationalized
    @Column(name = "password_hash", length = 255, nullable = false)
    private String passwordHash;
}
