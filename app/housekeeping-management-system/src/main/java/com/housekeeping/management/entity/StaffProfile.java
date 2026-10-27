package com.housekeeping.management.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Nationalized;

@Entity
@Table(name = "Staff_Profiles", schema = "dbo")
@Getter @Setter @NoArgsConstructor
public class StaffProfile {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "profile_id", nullable = false)
    private Long profileId;

    @Column(name = "staff_id", nullable = false, unique = true)
    private Long staffId;

    @Nationalized @Column(name = "avatar_url", length = 500)
    private String avatarUrl;

    @Nationalized @Column(name = "bio", columnDefinition = "nvarchar(max)")
    private String bio;
}
