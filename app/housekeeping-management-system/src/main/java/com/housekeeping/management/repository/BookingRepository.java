package com.housekeeping.management.repository;


import org.springframework.data.repository.NoRepositoryBean;
import com.housekeeping.management.domain.entity.Booking;
import java.time.OffsetDateTime;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

@NoRepositoryBean
public interface BookingRepository extends JpaRepository<Booking, Long> {
    @Query("""
            select case when count(distinct booking.bookingId) > 0 then true else false end
            from Booking booking
            join BookingDetail detail on detail.booking = booking
            join WorkSchedule schedule on detail.workSchedule = schedule
            where schedule.staff.staffId = :staffId
              and booking.bookingStatus = com.housekeeping.management.domain.BookingStatus.CONFIRMED
              and booking.startAt < :endAt
              and booking.endAt > :startAt
            """)
    boolean existsConfirmedOverlap(
            @Param("staffId") Long staffId,
            @Param("startAt") OffsetDateTime startAt,
            @Param("endAt") OffsetDateTime endAt);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select booking from Booking booking where booking.bookingId = :bookingId")
    Optional<Booking> findByIdForUpdate(@Param("bookingId") Long bookingId);
}
