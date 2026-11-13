package com.housekeeping.management.repository;

import com.housekeeping.management.entity.BookingTeamChangeRequest;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

public interface BookingTeamChangeRequestRepository extends JpaRepository<BookingTeamChangeRequest, Long> {
    boolean existsByBookingIdAndRequestStatus(Long bookingId, String requestStatus);
    Optional<BookingTeamChangeRequest> findFirstByBookingIdAndRequestStatusOrderByCreatedAtDesc(Long bookingId,
            String requestStatus);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select request from BookingTeamChangeRequest request where request.requestId=:id")
    Optional<BookingTeamChangeRequest> lockById(@Param("id") Long id);
}
