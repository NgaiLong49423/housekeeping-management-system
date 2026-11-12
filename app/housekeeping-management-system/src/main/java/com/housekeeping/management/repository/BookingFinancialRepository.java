package com.housekeeping.management.repository;

import com.housekeeping.management.entity.BookingFinancialEntry;
import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BookingFinancialRepository extends JpaRepository<BookingFinancialEntry, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select entry from BookingFinancialEntry entry where entry.entryId=:id")
    java.util.Optional<BookingFinancialEntry> findByIdForUpdate(@Param("id") Long id);

    @Query("select entry from BookingFinancialEntry entry join Booking booking on booking.bookingId=entry.bookingId "
            + "where booking.customerId=:customerId and entry.entryType='COMPENSATION' and entry.status in ('PROPOSED','PENDING_APPROVAL','APPROVED','PAID') "
            + "order by entry.createdAt desc, entry.entryId desc")
    List<BookingFinancialEntry> findApprovedCompensationsForCustomer(@Param("customerId") Long customerId);
    List<BookingFinancialEntry> findByBookingIdOrderByCreatedAtAscEntryIdAsc(Long bookingId);
    boolean existsByCaseIdAndEntryTypeAndStatusIn(Long caseId, String entryType, Collection<String> statuses);
    List<BookingFinancialEntry> findAllByOrderByCreatedAtDescEntryIdDesc();

    List<BookingFinancialEntry> findByBookingIdAndEntryTypeInOrderByCreatedAtAscEntryIdAsc(
            Long bookingId, Collection<String> entryTypes);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update BookingFinancialEntry entry set entry.status='APPROVED', entry.approvedBy=:actor, entry.approvedAt=CURRENT_TIMESTAMP "
            + "where entry.entryId=:id and entry.status='PENDING_APPROVAL'")
    int approve(@Param("id") Long id, @Param("actor") Long actor);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update BookingFinancialEntry entry set entry.status='REJECTED', entry.approvedBy=:actor, "
            + "entry.approvedAt=CURRENT_TIMESTAMP, entry.rejectionReason=:reason "
            + "where entry.entryId=:id and entry.status='PENDING_APPROVAL' and entry.createdBy<>:actor")
    int reject(@Param("id") Long id, @Param("actor") Long actor, @Param("reason") String reason);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update BookingFinancialEntry entry set entry.status='CANCELLED', entry.cancelledBy=:actor, "
            + "entry.cancellationReason=:reason where entry.entryId=:id "
            + "and entry.status in ('PROPOSED','PENDING_APPROVAL','APPROVED')")
    int cancel(@Param("id") Long id, @Param("actor") Long actor, @Param("reason") String reason);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update BookingFinancialEntry entry set entry.status=:status, entry.paymentMethod=:method, "
            + "entry.transactionReference=:reference, entry.simulated=:simulated, entry.settledBy=:adminId, "
            + "entry.paidAt=CURRENT_TIMESTAMP where entry.entryId=:id and entry.status='APPROVED' and entry.receiptPath is not null")
    int settle(@Param("id") Long id, @Param("adminId") Long adminId, @Param("method") String method,
            @Param("reference") String reference, @Param("simulated") boolean simulated,
            @Param("status") String status);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update BookingFinancialEntry entry set entry.receiptPath=:path where entry.entryId=:id "
            + "and entry.status='APPROVED' and entry.receiptPath is null")
    int attachReceipt(@Param("id") Long id, @Param("path") String path);

    @Query("select coalesce(sum(entry.amount), 0) from BookingFinancialEntry entry "
            + "where entry.status='PAID' and entry.simulated=false and entry.entryType in :types")
    BigDecimal sumPaidByTypes(@Param("types") Collection<String> types);

    @Query("select coalesce(sum(entry.amount), 0) from BookingFinancialEntry entry where entry.status='SIMULATED'")
    BigDecimal sumSimulated();
}
