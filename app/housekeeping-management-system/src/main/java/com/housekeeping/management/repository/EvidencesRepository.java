package com.housekeeping.management.repository;

import com.housekeeping.management.domain.entity.Evidences;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EvidencesRepository extends JpaRepository<Evidences, Long> {
    long countByBookingDetail_BookingDetailId(Long bookingDetailId);
}
