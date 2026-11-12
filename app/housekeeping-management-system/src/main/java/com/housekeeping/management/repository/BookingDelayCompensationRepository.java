package com.housekeeping.management.repository;

import com.housekeeping.management.entity.BookingDelayCompensationTier;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BookingDelayCompensationRepository extends JpaRepository<BookingDelayCompensationTier, Integer> {
    List<BookingDelayCompensationTier> findAllByOrderByMinDelayMinutesAsc();
}
