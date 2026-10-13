package com.housekeeping.management.repository;

import com.housekeeping.management.domain.entity.BookingDetail;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BookingDetailRepository extends JpaRepository<BookingDetail, Long> {
    List<BookingDetail> findAllByBooking_BookingIdOrderBySequenceNo(Long bookingId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select detail from BookingDetail detail where detail.bookingDetailId = :detailId")
    Optional<BookingDetail> findByIdForUpdate(@Param("detailId") Long detailId);
}
