package com.housekeeping.management.repository;


import org.springframework.data.repository.NoRepositoryBean;
import com.housekeeping.management.domain.entity.Evidences;
import org.springframework.data.jpa.repository.JpaRepository;

@NoRepositoryBean
public interface EvidencesRepository extends JpaRepository<Evidences, Long> {
    long countByBookingDetail_BookingDetailId(Long bookingDetailId);
}
