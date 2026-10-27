package com.housekeeping.management.entity;

import com.housekeeping.management.domain.UserRole;
import com.housekeeping.management.domain.UserStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Nationalized;

@Entity
@Table(name = "Users", schema = "dbo")
@Getter
@Setter
@NoArgsConstructor
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Nationalized
    @Column(name = "full_name", length = 150, nullable = false)
    private String fullName;

    @Nationalized
    @Column(name = "email", length = 255, nullable = false,
            columnDefinition = "nvarchar(255) collate Latin1_General_100_CI_AS")
    private String email;

    @Nationalized
    @Column(name = "password_hash", length = 255, nullable = false)
    private String passwordHash;

    @Nationalized
    @Column(name = "phone", length = 20, nullable = false)
    private String phone;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", length = 20, nullable = false, columnDefinition = "varchar(20)")
    private UserRole role;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false, columnDefinition = "varchar(20)")
    private UserStatus status = UserStatus.ACTIVE;
}
