package com.housekeeping.management.repository;

import com.housekeeping.management.entity.BookingDelayProposal;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BookingDelayProposalRepository extends JpaRepository<BookingDelayProposal, Long> {
    @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    Optional<BookingDelayProposal> findFirstByBookingIdOrderByProposalIdDesc(Long bookingId);
    List<BookingDelayProposal> findAllByBookingIdOrderByProposalIdAsc(Long bookingId);
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update BookingDelayProposal proposal set proposal.status='EXPIRED', proposal.respondedAt=:at where proposal.proposalId=:id and proposal.status='ACCEPTED'")
    int expireAccepted(@Param("id") Long id, @Param("at") java.time.LocalDateTime at);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update BookingDelayProposal proposal set proposal.status=:status, proposal.respondedAt=:at where proposal.proposalId=:id and proposal.status='PENDING'")
    int respond(@Param("id") Long id, @Param("status") String status, @Param("at") java.time.LocalDateTime at);
}
