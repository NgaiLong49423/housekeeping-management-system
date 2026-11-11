package com.housekeeping.management.repository;

import com.housekeeping.management.dto.response.PortalViews.IncidentCard;
import com.housekeeping.management.entity.FeedbackCase;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FeedbackCaseRepository extends JpaRepository<FeedbackCase, Long> {
    Optional<FeedbackCase> findByFeedbackId(Long feedbackId);

    @Query("select incident.feedbackId from FeedbackCase incident where incident.caseId=:caseId")
    Optional<Long> feedbackIdForCase(@Param("caseId") Long caseId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select incident from FeedbackCase incident where incident.caseId=:id")
    Optional<FeedbackCase> findByIdForUpdate(@Param("id") Long id);

    @Query("select feedback.feedbackId from Feedback feedback join Booking booking on booking.bookingId=feedback.bookingId "
            + "where feedback.bookingId=:bookingId and feedback.customerId=:customerId and booking.customerId=:customerId "
            + "and booking.status=com.housekeeping.management.domain.BookingStatus.COMPLETED "
            + "and feedback.status<>com.housekeeping.management.domain.FeedbackStatus.RESOLVED")
    Optional<Long> eligibleFeedbackId(@Param("bookingId") Long bookingId, @Param("customerId") Long customerId);

    @Query("select feedback.feedbackId from Feedback feedback where feedback.bookingId=:bookingId and feedback.customerId=:customerId")
    Optional<Long> feedbackIdForCustomerBooking(@Param("bookingId") Long bookingId, @Param("customerId") Long customerId);

    default Long create(Long feedbackId, List<String> types, String description, String itemDescription,
            java.math.BigDecimal estimatedValue, Integer itemAgeMonths) {
        FeedbackCase incident = new FeedbackCase();
        incident.setFeedbackId(feedbackId);
        incident.setIncidentTypes(String.join(",", types));
        incident.setCustomerDescription(description);
        incident.setItemDescription(itemDescription);
        incident.setCustomerEstimatedValue(estimatedValue);
        incident.setItemAgeMonths(itemAgeMonths);
        incident.setCaseStatus("RECEIVED");
        incident.setResolutionWorkflowRequired(true);
        incident.setCreatedAt(LocalDateTime.now(java.time.ZoneOffset.ofHours(7)).withNano(0));
        return saveAndFlush(incident).getCaseId();
    }

    default Long create(Long feedbackId, List<String> types, String description) {
        return create(feedbackId, types, description, null, null, null);
    }

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update FeedbackCase incident set incident.caseStatus='RESOLVED', incident.resolvedAt=CURRENT_TIMESTAMP "
            + "where incident.caseId=:id and incident.caseStatus in ('VERIFIED','NOT_SUBSTANTIATED') "
            + "and exists (select feedback.feedbackId from Feedback feedback where feedback.feedbackId=incident.feedbackId "
            + "and feedback.adminReply is not null and trim(feedback.adminReply)<>'') "
            + "and (incident.remedyType is null or incident.remedyCompleted=true) "
            + "and (incident.remedyType is null or incident.remedyType not in ('REFUND','PRICE_REDUCTION') or exists "
            + "(select entry.entryId from BookingFinancialEntry entry where entry.caseId=incident.caseId "
            + "and entry.entryType=incident.remedyType and entry.status in ('PAID','SIMULATED'))) "
            + "and (incident.remedyType is null or incident.remedyType<>'COMPENSATION' or exists "
            + "(select entry.entryId from BookingFinancialEntry entry where entry.caseId=incident.caseId "
            + "and entry.entryType='COMPENSATION' and entry.status='PAID')) "
            + "and (incident.remedyType is null or incident.remedyType<>'COMPENSATION' or exists "
            + "(select payout.payoutResponseId from FinancialPayoutResponse payout join BookingFinancialEntry entry "
            + "on entry.entryId=payout.entryId where entry.caseId=incident.caseId and payout.responseStatus='ACCEPTED')) "
            + "and incident.staffActionStatus<>'PENDING' "
            + "and not exists (select entry.entryId from BookingFinancialEntry entry where entry.caseId=incident.caseId "
            + "and entry.status in ('PROPOSED','PENDING_APPROVAL','APPROVED')) "
            + "and (not exists (select plan.planId from FeedbackResolutionPlan plan where plan.caseId=incident.caseId) "
            + "or exists (select plan.planId from FeedbackResolutionPlan plan where plan.caseId=incident.caseId and plan.status='COMPLETED')) "
            + "and (incident.resolutionWorkflowRequired=false or exists (select plan.planId from FeedbackResolutionPlan plan "
            + "where plan.caseId=incident.caseId and plan.status='COMPLETED'))")
    int resolveCaseUpdate(@Param("id") Long id);

    @Query("select (count(entry)>0) from BookingFinancialEntry entry where entry.caseId=:caseId "
            + "and entry.entryType='COMPENSATION' and entry.status='PAID' and exists "
            + "(select payout.payoutResponseId from FinancialPayoutResponse payout where payout.entryId=entry.entryId "
            + "and payout.responseStatus='ACCEPTED')")
    boolean hasSettledAcceptedCompensation(@Param("caseId") Long caseId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Feedback feedback set feedback.status=com.housekeeping.management.domain.FeedbackStatus.RESOLVED "
            + "where feedback.feedbackId=(select incident.feedbackId from FeedbackCase incident where incident.caseId=:caseId) "
            + "and feedback.status=com.housekeeping.management.domain.FeedbackStatus.RESPONDED")
    int resolveFeedback(@Param("caseId") Long caseId);

    default int resolveCase(Long id) {
        int updated = resolveCaseUpdate(id);
        if (updated == 1) resolveFeedback(id);
        return updated;
    }

    @Query("select incident.customerOutcome from FeedbackCase incident where incident.caseId=:id")
    String customerOutcome(@Param("id") Long id);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Feedback feedback set feedback.status=com.housekeeping.management.domain.FeedbackStatus.RESPONDED, "
            + "feedback.adminReply=coalesce(nullif(trim(feedback.adminReply),''),:reply) where feedback.feedbackId="
            + "(select incident.feedbackId from FeedbackCase incident where incident.caseId=:caseId) "
            + "and feedback.status in (com.housekeeping.management.domain.FeedbackStatus.OPEN,com.housekeeping.management.domain.FeedbackStatus.RESPONDED)")
    int markFeedbackResponded(@Param("caseId") Long caseId, @Param("reply") String reply);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update FeedbackCase incident set incident.caseStatus=:status, incident.remedyType=:remedy, "
            + "incident.customerOutcome=:outcome, incident.internalFinding=:finding, "
            + "incident.remedyCompleted=:completed, incident.reviewedBy=:admin, incident.reviewedAt=:at "
            + "where incident.caseId=:id and incident.caseStatus<>'RESOLVED'")
    int updateReview(@Param("id") Long id, @Param("status") String status, @Param("remedy") String remedy,
            @Param("outcome") String outcome, @Param("finding") String finding,
            @Param("completed") boolean completed, @Param("admin") Long admin, @Param("at") LocalDateTime at);

    @Query("select (count(incident)>0) from FeedbackCase incident join Feedback feedback on feedback.feedbackId=incident.feedbackId "
            + "join Booking booking on booking.bookingId=feedback.bookingId where incident.caseId=:caseId "
            + "and feedback.customerId=:customerId and booking.customerId=:customerId")
    boolean customerOwnsCase(@Param("caseId") Long caseId, @Param("customerId") Long customerId);

    @Query("select (count(incident)>0) from FeedbackCase incident join Feedback feedback on feedback.feedbackId=incident.feedbackId "
            + "where incident.caseId=:caseId and feedback.bookingId=:bookingId")
    boolean belongsToBooking(@Param("caseId") Long caseId, @Param("bookingId") Long bookingId);

    @Query("select (count(incident)>0) from FeedbackCase incident join Feedback feedback on feedback.feedbackId=incident.feedbackId "
            + "where incident.caseId=:caseId and feedback.bookingId=:bookingId and incident.caseStatus='VERIFIED' "
            + "and incident.staffAction='PROPOSE_RECOVERY' and incident.staffActionStatus='APPROVED'")
    boolean authorizesStaffRecovery(@Param("caseId") Long caseId, @Param("bookingId") Long bookingId);

    @Query("select (count(incident)>0) from FeedbackCase incident join Feedback feedback on feedback.feedbackId=incident.feedbackId "
            + "join Booking booking on booking.bookingId=feedback.bookingId where incident.caseId=:caseId "
            + "and feedback.bookingId=:bookingId and feedback.customerId=:customerId and booking.customerId=:customerId "
            + "and incident.caseStatus<>'RESOLVED'")
    boolean customerCanMessage(@Param("caseId") Long caseId, @Param("bookingId") Long bookingId,
            @Param("customerId") Long customerId);

    @Query("select booking.staffId from FeedbackCase incident join Feedback feedback on feedback.feedbackId=incident.feedbackId "
            + "join Booking booking on booking.bookingId=feedback.bookingId where incident.caseId=:caseId")
    Optional<Long> staffIdForCase(@Param("caseId") Long caseId);

    @Query("select (count(incident)>0) from FeedbackCase incident join Feedback feedback on feedback.feedbackId=incident.feedbackId "
            + "join Booking booking on booking.bookingId=feedback.bookingId join FeedbackCaseMessage message on message.caseId=incident.caseId "
            + "where incident.caseId=:caseId and message.messageId=:messageId and message.senderUserId=:actorId "
            + "and message.senderRole=:role and incident.caseStatus<>'RESOLVED' and "
            + "(:admin=true or (booking.bookingId=:bookingId and feedback.customerId=:actorId and booking.customerId=:actorId))")
    boolean authorizesMessageEvidence(@Param("caseId") Long caseId, @Param("messageId") Long messageId,
            @Param("actorId") Long actorId, @Param("role") String role, @Param("admin") boolean admin,
            @Param("bookingId") Long bookingId);

    @Query("select min(message.messageId) from FeedbackCaseMessage message where message.caseId=:caseId and message.senderRole='CUSTOMER'")
    Long initialCustomerMessageId(@Param("caseId") Long caseId);

    @Query("select incident.feedbackId from FeedbackCase incident order by incident.createdAt desc, incident.caseId desc")
    List<Long> findAllFeedbackIds();

    @Query("select incident.caseId, incident.feedbackId, booking.bookingId, booking.customerId, booking.staffId, "
            + "incident.incidentTypes, incident.customerDescription, incident.caseStatus, incident.customerOutcome, "
            + "incident.remedyType, incident.internalFinding, incident.remedyCompleted, "
            + "coalesce(action.actionType,incident.staffAction), coalesce(action.status,incident.staffActionStatus), "
            + "coalesce(action.proposedBy,incident.staffActionBy), coalesce(action.decidedBy,incident.staffActionApprovedBy), "
            + "action.actionId, incident.createdAt, incident.resolvedAt, incident.itemDescription, "
            + "incident.customerEstimatedValue, incident.itemAgeMonths, incident.resolutionWorkflowRequired "
            + "from FeedbackCase incident join Feedback feedback on feedback.feedbackId=incident.feedbackId "
            + "join Booking booking on booking.bookingId=feedback.bookingId left join StaffIncidentAction action "
            + "on action.actionId=(select max(latest.actionId) from StaffIncidentAction latest where latest.caseId=incident.caseId) "
            + "where incident.feedbackId=:feedbackId")
    List<Object[]> findIncidentRows(@Param("feedbackId") Long feedbackId);

    default Optional<IncidentCard> findForFeedback(Long feedbackId) {
        return findIncidentRows(feedbackId).stream().findFirst().map(row -> new IncidentCard(
                (Long) row[0], (Long) row[1], (Long) row[2], (Long) row[3], (Long) row[4],
                Arrays.asList(((String) row[5]).split(",")), (String) row[6], (String) row[7],
                (String) row[8], (String) row[9], (String) row[10], Boolean.TRUE.equals(row[11]),
                (String) row[12], (String) row[13], (Long) row[14], (Long) row[15], (Long) row[16],
                (LocalDateTime) row[17], (LocalDateTime) row[18], (String) row[19],
                (java.math.BigDecimal) row[20], (Integer) row[21], Boolean.TRUE.equals(row[22])));
    }

    default List<IncidentCard> findAllCards() {
        return findAllFeedbackIds().stream().map(this::findForFeedback).flatMap(Optional::stream).toList();
    }
}
