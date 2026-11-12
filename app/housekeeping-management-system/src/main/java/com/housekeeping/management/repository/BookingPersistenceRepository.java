package com.housekeeping.management.repository;

import com.housekeeping.management.enums.BookingStatus;
import com.housekeeping.management.entity.Booking;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

/** Booking-owned persistence queries. Other domains are reached through their service contracts. */
public interface BookingPersistenceRepository extends JpaRepository<Booking, Long> {
    List<Booking> findAllByCustomerIdOrderByStartTimeDesc(Long customerId);
    List<Booking> findAllByStaffIdOrderByStartTimeDesc(Long staffId);
    List<Booking> findAllByBookingIdIn(List<Long> bookingIds);
    List<Booking> findAllByStatusInAndEndTimeAfterOrderByStartTime(List<BookingStatus> statuses, LocalDateTime endTime);
    List<BookingStatus> ACTIVE_STATUSES = List.of(BookingStatus.CONFIRMED, BookingStatus.AWAITING_PAYMENT);
    List<BookingStatus> APPOINTMENT_STATUSES = List.of(BookingStatus.CONFIRMED,
            BookingStatus.AWAITING_PAYMENT, BookingStatus.COMPLETED);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select booking from Booking booking where booking.bookingId=:id")
    Optional<Booking> lockEntity(@Param("id") Long id);

    default Optional<BookingState> lockBooking(Long id) {
        return lockEntity(id).map(BookingState::from);
    }

    default Optional<BookingState> findBooking(Long id) {
        return findById(id).map(BookingState::from);
    }

    default Optional<BookingWorkflowState> lockBookingWorkflow(Long id) {
        return lockEntity(id).map(BookingWorkflowState::from);
    }

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Booking booking set booking.status=:newStatus where booking.bookingId=:id "
            + "and booking.status=:expectedStatus and (:newStatus<>com.housekeeping.management.enums.BookingStatus.AWAITING_PAYMENT "
            + "or booking.workStartedAt is not null)")
    int changeStatusEntity(@Param("id") Long id, @Param("expectedStatus") BookingStatus expected,
            @Param("newStatus") BookingStatus next);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Booking booking set booking.status=com.housekeeping.management.enums.BookingStatus.AWAITING_PAYMENT, "
            + "booking.serviceCompletedAt=:at where booking.bookingId=:id and booking.staffId=:staff "
            + "and booking.status=com.housekeeping.management.enums.BookingStatus.CONFIRMED "
            + "and booking.workStartedAt is not null and booking.serviceCompletedAt is null")
    int completeWork(@Param("id") Long id, @Param("staff") Long staff, @Param("at") LocalDateTime at);

    default int changeStatus(Long id, String expected, String next) {
        return changeStatusEntity(id, BookingStatus.valueOf(expected), BookingStatus.valueOf(next));
    }

    boolean existsByStaffIdAndStatusIn(Long staffId, List<BookingStatus> statuses);
    boolean existsByHouseIdAndStatusIn(Long houseId, List<BookingStatus> statuses);

    default boolean hasUnfinishedBookingForStaff(Long staffId) {
        return existsByStaffIdAndStatusIn(staffId, ACTIVE_STATUSES);
    }

    default boolean hasActiveBookingForHouse(Long houseId) {
        return existsByHouseIdAndStatusIn(houseId, ACTIVE_STATUSES);
    }

    @Query("select (count(booking)>0) from Booking booking where booking.bookingId=:id "
            + "and booking.status=com.housekeeping.management.enums.BookingStatus.AWAITING_PAYMENT")
    boolean isAwaitingPayment(@Param("id") Long id);

    @Query("select coalesce(booking.totalAmount-booking.delayDiscountAmount,0) from Booking booking where booking.bookingId=:id")
    BigDecimal bookingAmount(@Param("id") Long id);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Booking booking set booking.status=com.housekeeping.management.enums.BookingStatus.COMPLETED "
            + "where booking.bookingId=:id and booking.status=com.housekeeping.management.enums.BookingStatus.AWAITING_PAYMENT")
    int completeAfterPayment(@Param("id") Long id);

    @Query("select booking.bookingId from Booking booking where booking.staffId=:staffId "
            + "and booking.status=com.housekeeping.management.enums.BookingStatus.CONFIRMED "
            + "and booking.endTime>:now and booking.startTime>=:from and booking.startTime<:until order by booking.startTime")
    List<Long> futureConfirmedBookingIds(@Param("staffId") Long staffId, @Param("from") LocalDateTime from,
            @Param("until") LocalDateTime until, @Param("now") LocalDateTime now);

    @Query("select booking from Booking booking where booking.status in :statuses and booking.startTime>=:from "
            + "and (:until is null or booking.startTime<:until) order by booking.startTime")
    List<Booking> findActiveInInterval(@Param("from") LocalDateTime from, @Param("until") LocalDateTime until,
            @Param("statuses") List<BookingStatus> statuses);

    @Query("select booking from Booking booking where booking.status in :statuses and booking.startTime>:now")
    List<Booking> findFutureActiveWithLocation(@Param("now") LocalDateTime now,
            @Param("statuses") List<BookingStatus> statuses);

    default List<Long> futureConfirmedBookingIds(Long staffId, LocalDate from, LocalDate until) {
        return futureConfirmedBookingIds(staffId, from.atStartOfDay(), until.plusDays(1).atStartOfDay(),
                LocalDateTime.now(java.time.ZoneOffset.ofHours(7)).withNano(0));
    }

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Booking booking set booking.status=com.housekeeping.management.enums.BookingStatus.CANCELLED, "
            + "booking.cancelledAt=:at, booking.cancelledBy=:actor, booking.cancellationReason=:reason "
            + "where booking.bookingId=:id and booking.status=com.housekeeping.management.enums.BookingStatus.CONFIRMED")
    int cancel(@Param("id") Long id, @Param("actor") Long actor, @Param("reason") String reason,
            @Param("at") LocalDateTime at);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Booking booking set booking.status=com.housekeeping.management.enums.BookingStatus.CANCELLED, "
            + "booking.cancelledAt=:at, booking.cancelledBy=:actor, booking.cancellationReason=:reason "
            + "where booking.bookingId=:id and booking.customerId=:actor "
            + "and booking.status=com.housekeeping.management.enums.BookingStatus.CONFIRMED and booking.workStartedAt is null")
    int cancelByCustomer(@Param("id") Long id, @Param("actor") Long actor, @Param("reason") String reason,
            @Param("at") LocalDateTime at);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Booking booking set booking.staffId=:replacement where booking.bookingId=:id "
            + "and booking.staffId=:expected and booking.status=com.housekeeping.management.enums.BookingStatus.CONFIRMED")
    int assignStaff(@Param("id") Long id, @Param("expected") Long expected, @Param("replacement") Long replacement);

    @Query("select booking from Booking booking where booking.staffId=:staffId and booking.status in :statuses "
            + "and booking.startTime<:end and booking.endTime>:start order by booking.startTime")
    List<Booking> findAppointments(@Param("staffId") Long staffId, @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end, @Param("statuses") List<BookingStatus> statuses);

    @Query("select booking from Booking booking where booking.staffId=:staffId and booking.status in :statuses "
            + "and booking.bookingId<>:excluded and booking.endTime<=:start order by booking.endTime desc")
    List<Booking> findPreviousAppointments(@Param("staffId") Long staffId, @Param("start") LocalDateTime start,
            @Param("excluded") Long excluded, @Param("statuses") List<BookingStatus> statuses,
            org.springframework.data.domain.Pageable page);

    @Query("select booking from Booking booking where booking.staffId=:staffId and booking.status in :statuses "
            + "and booking.bookingId<>:excluded and booking.startTime>=:end order by booking.startTime")
    List<Booking> findNextAppointments(@Param("staffId") Long staffId, @Param("end") LocalDateTime end,
            @Param("excluded") Long excluded, @Param("statuses") List<BookingStatus> statuses,
            org.springframework.data.domain.Pageable page);

    default List<AppointmentPoint> overlappingAppointments(Long staffId, LocalDateTime start,
            LocalDateTime end, Long excluded) {
        return findAppointments(staffId, start, end, APPOINTMENT_STATUSES).stream()
                .filter(booking -> excluded == null || !excluded.equals(booking.getBookingId()))
                .map(AppointmentPoint::from).toList();
    }

    default List<AppointmentPoint> adjacentAppointments(Long staffId, LocalDateTime start,
            LocalDateTime end, Long excluded) {
        Long excludedId = excluded == null ? -1L : excluded;
        var first = findPreviousAppointments(staffId, start, excludedId, APPOINTMENT_STATUSES,
                org.springframework.data.domain.PageRequest.of(0, 1));
        var next = findNextAppointments(staffId, end, excludedId, APPOINTMENT_STATUSES,
                org.springframework.data.domain.PageRequest.of(0, 1));
        List<AppointmentPoint> result = new ArrayList<>(2);
        if (!first.isEmpty()) result.add(AppointmentPoint.from(first.getFirst()));
        if (!next.isEmpty()) result.add(AppointmentPoint.from(next.getFirst()));
        return List.copyOf(result);
    }

    @Query("select (count(booking)>0) from Booking booking where booking.staffId=:staffId "
            + "and booking.status in :statuses and booking.startTime<:end and booking.endTime>:start")
    boolean hasConflictingBooking(@Param("staffId") Long staffId, @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end, @Param("statuses") List<BookingStatus> statuses);

    default boolean hasConflictingBooking(Long staffId, LocalDateTime start, LocalDateTime end, int gapMinutes) {
        return hasConflictingBooking(staffId, start.minusMinutes(gapMinutes), end.plusMinutes(gapMinutes), APPOINTMENT_STATUSES);
    }

    @Query("select (count(booking)>0) from Booking booking where booking.staffId=:staffId "
            + "and booking.bookingId<>:excluded and booking.status in :statuses "
            + "and booking.startTime<:end and booking.endTime>:start")
    boolean hasConflictingBookingExcept(@Param("staffId") Long staffId, @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end, @Param("excluded") Long excluded,
            @Param("statuses") List<BookingStatus> statuses);

    default boolean hasConflictingBookingExcept(Long staffId, LocalDateTime start, LocalDateTime end,
            Long excluded, int gapMinutes) {
        return hasConflictingBookingExcept(staffId, start.minusMinutes(gapMinutes), end.plusMinutes(gapMinutes),
                excluded, APPOINTMENT_STATUSES);
    }

    @Query("select booking from Booking booking where booking.staffId=:staffId and booking.startTime>=:from "
            + "and booking.startTime<:until and booking.status=com.housekeeping.management.enums.BookingStatus.CONFIRMED "
            + "and booking.endTime>:now and booking.endTime>:availabilityStart and booking.startTime<:availabilityEnd")
    List<Booking> findFutureConfirmedWithinInterval(@Param("staffId") Long staffId, @Param("from") LocalDateTime from,
            @Param("until") LocalDateTime until, @Param("now") LocalDateTime now,
            @Param("availabilityStart") LocalDateTime availabilityStart,
            @Param("availabilityEnd") LocalDateTime availabilityEnd);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Booking booking set booking.customerArrivedAt=:at "
            + "where booking.bookingId=:id "
            + "and booking.customerId=:customer and booking.status=com.housekeeping.management.enums.BookingStatus.CONFIRMED "
            + "and booking.customerArrivedAt is null "
            + "and booking.serviceCompletedAt is null")
    int confirmCustomerArrival(@Param("id") Long id, @Param("customer") Long customer,
            @Param("at") LocalDateTime at);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Booking booking set booking.arrivalVerifiedAt=:actualAt, booking.arrivalVerificationRecordedAt=:recordedAt, booking.arrivalVerifiedBy=:admin, "
            + "booking.arrivalVerificationSource='ADMIN', booking.arrivalVerificationNote=:note, "
            + "booking.delayMinutes=:delay, booking.delayDiscountPercent=:percent, booking.delayDiscountAmount=:amount "
            + "where booking.bookingId=:id and booking.status=com.housekeeping.management.enums.BookingStatus.CONFIRMED "
            + "and booking.customerDelayReportedAt is not null and booking.arrivalVerifiedAt is null "
            + "and booking.serviceCompletedAt is null")
    int verifyDisputedArrival(@Param("id") Long id, @Param("admin") Long admin,
            @Param("actualAt") LocalDateTime actualAt, @Param("recordedAt") LocalDateTime recordedAt, @Param("note") String note,
            @Param("delay") int delay, @Param("percent") BigDecimal percent, @Param("amount") BigDecimal amount);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Booking booking set booking.staffArrivedAt=:at, booking.delayMinutes=:delay, "
            + "booking.delayDiscountPercent=:percent, booking.delayDiscountAmount=:amount "
            + "where booking.bookingId=:id and booking.staffId=:staff and booking.status=com.housekeeping.management.enums.BookingStatus.CONFIRMED "
            + "and booking.staffArrivedAt is null")
    int confirmStaffArrival(@Param("id") Long id, @Param("staff") Long staff, @Param("at") LocalDateTime at,
            @Param("delay") int delay, @Param("percent") BigDecimal percent, @Param("amount") BigDecimal amount);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Booking booking set booking.customerDelayReportedAt=:at where booking.bookingId=:id "
            + "and booking.customerId=:customer and booking.status=com.housekeeping.management.enums.BookingStatus.CONFIRMED "
            + "and booking.startTime<=:at and booking.serviceCompletedAt is null "
            + "and booking.customerDelayReportedAt is null")
    int reportCustomerDelay(@Param("id") Long id, @Param("customer") Long customer,
            @Param("at") LocalDateTime at);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Booking booking set booking.incidentReportedAt=coalesce(booking.incidentReportedAt,:at), "
            + "booking.incidentNote=:note, booking.incidentExpectedArrivalAt=:eta, booking.incidentCustomerResponse=null, "
            + "booking.incidentCustomerRespondedAt=null where booking.bookingId=:id "
            + "and booking.staffId=:staff and booking.status=com.housekeeping.management.enums.BookingStatus.CONFIRMED "
            + "and booking.workStartedAt is null and (booking.incidentReportedAt is null or booking.incidentCustomerResponse='EXPIRED' or "
            + "(booking.incidentCustomerResponse='ACCEPTED' and booking.incidentExpectedArrivalAt<:at))")
    int reportStaffIncident(@Param("id") Long id, @Param("staff") Long staff, @Param("note") String note,
            @Param("eta") LocalDateTime eta, @Param("at") LocalDateTime at);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Booking booking set booking.incidentCustomerResponse='EXPIRED', booking.incidentCustomerRespondedAt=:at "
            + "where booking.bookingId=:id and booking.incidentCustomerResponse is null and booking.incidentExpectedArrivalAt<:at")
    int expireBookingEta(@Param("id") Long id, @Param("at") LocalDateTime at);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Booking booking set booking.incidentCustomerResponse='DECLINED', "
            + "booking.incidentCustomerRespondedAt=:at, booking.status=com.housekeeping.management.enums.BookingStatus.CANCELLED, "
            + "booking.cancelledAt=:at, booking.cancelledBy=:customer, booking.cancellationReason=:reason "
            + "where booking.bookingId=:id and booking.customerId=:customer and booking.status=com.housekeeping.management.enums.BookingStatus.CONFIRMED "
            + "and booking.incidentReportedAt is not null and booking.incidentExpectedArrivalAt is not null "
            + "and booking.incidentCustomerResponse is null and booking.workStartedAt is null")
    int declineIncident(@Param("id") Long id, @Param("customer") Long customer, @Param("at") LocalDateTime at,
            @Param("reason") String reason);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Booking booking set booking.incidentCustomerResponse='ACCEPTED', "
            + "booking.incidentCustomerRespondedAt=:at "
            + "where booking.bookingId=:id and booking.customerId=:customer and booking.status=com.housekeeping.management.enums.BookingStatus.CONFIRMED "
            + "and booking.incidentReportedAt is not null and booking.incidentExpectedArrivalAt is not null "
            + "and booking.incidentCustomerResponse is null and booking.workStartedAt is null "
            + "and booking.incidentExpectedArrivalAt>=:at")
    int acceptIncident(@Param("id") Long id, @Param("customer") Long customer, @Param("at") LocalDateTime at);

    default int respondToIncident(Long id, Long customer, String response, LocalDateTime at) {
        return "DECLINED".equals(response)
                ? declineIncident(id, customer, at, "Khách hủy miễn phí do Staff đến trễ.")
                : acceptIncident(id, customer, at);
    }

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Booking booking set booking.workStartedAt=:at where booking.bookingId=:id "
            + "and booking.staffId=:staff and booking.status=com.housekeeping.management.enums.BookingStatus.CONFIRMED "
            + "and booking.workStartedAt is null and booking.staffArrivedAt is not null "
            + "and (:lateBeyondGrace=false or booking.incidentReportedAt is not null) "
            + "and (booking.incidentReportedAt is null or (booking.incidentCustomerResponse='ACCEPTED' "
            + "and booking.staffArrivedAt<=booking.incidentExpectedArrivalAt))")
    int startWork(@Param("id") Long id, @Param("staff") Long staff, @Param("at") LocalDateTime at,
            @Param("lateBeyondGrace") boolean lateBeyondGrace);

    default Long insertBooking(Long customer, Long staff, Long house, String address, BigDecimal latitude,
            BigDecimal longitude, String addressNote, LocalDateTime start, LocalDateTime end, int duration,
            BigDecimal amount, String areaNote, LocalDateTime createdAt) {
        Booking booking = new Booking();
        booking.setCustomerId(customer);
        booking.setStaffId(staff);
        booking.setOriginalStaffId(staff);
        booking.setHouseId(house);
        booking.setAddressSnapshot(address);
        booking.setLatitudeSnapshot(latitude);
        booking.setLongitudeSnapshot(longitude);
        booking.setAddressNoteSnapshot(addressNote);
        booking.setStartTime(start);
        booking.setEndTime(end);
        booking.setTotalDurationMinutes(duration);
        booking.setTotalAmount(amount);
        booking.setAreaNote(areaNote);
        booking.setStatus(BookingStatus.CONFIRMED);
        booking.setCreatedAt(createdAt);
        return saveAndFlush(booking).getBookingId();
    }

    record AppointmentPoint(Long bookingId, LocalDateTime start, LocalDateTime end,
            BigDecimal latitude, BigDecimal longitude) {
        static AppointmentPoint from(Booking booking) {
            return new AppointmentPoint(booking.getBookingId(), booking.getStartTime(), booking.getEndTime(),
                    booking.getLatitudeSnapshot(), booking.getLongitudeSnapshot());
        }
    }
    record ServiceSnapshot(Long id, String name, BigDecimal price, int durationMinutes) { }
    record BookingState(Long id, Long customerId, Long staffId, Long houseId, LocalDateTime startTime,
            LocalDateTime endTime, String status, BigDecimal latitude, BigDecimal longitude, LocalDateTime serviceCompletedAt) {
        public BookingState(Long id, Long customerId, Long staffId, Long houseId, LocalDateTime startTime,
                LocalDateTime endTime, String status, BigDecimal latitude, BigDecimal longitude) {
            this(id,customerId,staffId,houseId,startTime,endTime,status,latitude,longitude,null);
        }
        static BookingState from(Booking booking) {
            return new BookingState(booking.getBookingId(), booking.getCustomerId(), booking.getStaffId(),
                    booking.getHouseId(), booking.getStartTime(), booking.getEndTime(), booking.getStatus().name(),
                    booking.getLatitudeSnapshot(), booking.getLongitudeSnapshot(), booking.getServiceCompletedAt());
        }
    }
    record BookingWorkflowState(Long id, Long customerId, Long staffId, LocalDateTime startTime,
            LocalDateTime endTime, String status, BigDecimal totalAmount, LocalDateTime staffArrivedAt,
            LocalDateTime workStartedAt, LocalDateTime incidentReportedAt, String incidentNote,
            LocalDateTime incidentExpectedArrivalAt, String incidentCustomerResponse,
            LocalDateTime incidentCustomerRespondedAt, Integer delayMinutes,
            BigDecimal delayDiscountPercent, BigDecimal delayDiscountAmount,
            LocalDateTime customerDelayReportedAt, LocalDateTime arrivalVerifiedAt,
            Long arrivalVerifiedBy, String arrivalVerificationSource, String arrivalVerificationNote,
            LocalDateTime customerArrivedAt, LocalDateTime serviceCompletedAt) {
        public BookingWorkflowState(Long id, Long customerId, Long staffId, LocalDateTime startTime,
                LocalDateTime endTime, String status, BigDecimal totalAmount, LocalDateTime staffArrivedAt,
                LocalDateTime workStartedAt, LocalDateTime incidentReportedAt, String incidentNote,
                LocalDateTime incidentExpectedArrivalAt, String incidentCustomerResponse,
                LocalDateTime incidentCustomerRespondedAt, Integer delayMinutes,
                BigDecimal delayDiscountPercent, BigDecimal delayDiscountAmount,
                LocalDateTime customerDelayReportedAt, LocalDateTime arrivalVerifiedAt,
                Long arrivalVerifiedBy, String arrivalVerificationSource, String arrivalVerificationNote,
                LocalDateTime customerArrivedAt) {
            this(id, customerId, staffId, startTime, endTime, status, totalAmount, staffArrivedAt,
                    workStartedAt, incidentReportedAt, incidentNote, incidentExpectedArrivalAt,
                    incidentCustomerResponse, incidentCustomerRespondedAt, delayMinutes, delayDiscountPercent,
                    delayDiscountAmount, customerDelayReportedAt, arrivalVerifiedAt, arrivalVerifiedBy,
                    arrivalVerificationSource, arrivalVerificationNote, customerArrivedAt, null);
        }
        public BookingWorkflowState(Long id, Long customerId, Long staffId, LocalDateTime startTime,
                LocalDateTime endTime, String status, BigDecimal totalAmount, LocalDateTime staffArrivedAt,
                LocalDateTime workStartedAt, LocalDateTime incidentReportedAt, String incidentNote,
                LocalDateTime incidentExpectedArrivalAt, String incidentCustomerResponse,
                LocalDateTime incidentCustomerRespondedAt, Integer delayMinutes,
                BigDecimal delayDiscountPercent, BigDecimal delayDiscountAmount) {
            this(id, customerId, staffId, startTime, endTime, status, totalAmount, staffArrivedAt,
                    workStartedAt, incidentReportedAt, incidentNote, incidentExpectedArrivalAt,
                    incidentCustomerResponse, incidentCustomerRespondedAt, delayMinutes, delayDiscountPercent,
                    delayDiscountAmount, null, null, null, null, null, null, null);
        }
        static BookingWorkflowState from(Booking booking) {
            return new BookingWorkflowState(booking.getBookingId(), booking.getCustomerId(), booking.getStaffId(),
                    booking.getStartTime(), booking.getEndTime(), booking.getStatus().name(), booking.getTotalAmount(),
                    booking.getStaffArrivedAt(), booking.getWorkStartedAt(), booking.getIncidentReportedAt(),
                    booking.getIncidentNote(), booking.getIncidentExpectedArrivalAt(),
                    booking.getIncidentCustomerResponse(), booking.getIncidentCustomerRespondedAt(),
                    booking.getDelayMinutes(), booking.getDelayDiscountPercent(), booking.getDelayDiscountAmount(),
                    booking.getCustomerDelayReportedAt(), booking.getArrivalVerifiedAt(), booking.getArrivalVerifiedBy(),
                    booking.getArrivalVerificationSource(), booking.getArrivalVerificationNote(), booking.getCustomerArrivedAt(), booking.getServiceCompletedAt());
        }
    }
}
