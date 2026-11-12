package com.housekeeping.management.repository;

import com.housekeeping.management.enums.FeedbackStatus;
import com.housekeeping.management.entity.Feedback;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FeedbackRepository extends JpaRepository<Feedback, Long> {
    List<Feedback> findAllByBookingIdIn(List<Long> bookingIds);
    boolean existsByBookingId(Long bookingId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Feedback feedback set feedback.rating=:rating, feedback.topics=:topics, feedback.content=:content "
            + "where feedback.bookingId=:bookingId and feedback.customerId=:customerId "
            + "and feedback.status=com.housekeeping.management.enums.FeedbackStatus.OPEN and feedback.adminReply is null")
    int updateCustomerFeedback(@Param("bookingId") Long bookingId, @Param("customerId") Long customerId,
            @Param("rating") Integer rating, @Param("topics") String topics, @Param("content") String content);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Feedback feedback set feedback.adminReply=:reply, "
            + "feedback.status=com.housekeeping.management.enums.FeedbackStatus.RESPONDED "
            + "where feedback.feedbackId=:feedbackId and feedback.status in "
            + "(com.housekeeping.management.enums.FeedbackStatus.OPEN, com.housekeeping.management.enums.FeedbackStatus.RESPONDED)")
    int reply(@Param("feedbackId") Long feedbackId, @Param("reply") String reply);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Feedback feedback set feedback.status=com.housekeeping.management.enums.FeedbackStatus.RESOLVED "
            + "where feedback.feedbackId=:feedbackId and feedback.status=com.housekeeping.management.enums.FeedbackStatus.RESPONDED "
            + "and not exists (select incident.caseId from FeedbackCase incident where incident.feedbackId=feedback.feedbackId "
            + "and incident.caseStatus<>'RESOLVED')")
    int resolve(@Param("feedbackId") Long feedbackId);

    @Query("select feedback.feedbackId, feedback.bookingId, customer.fullName, feedback.rating, feedback.topics, "
            + "feedback.content, feedback.adminReply, feedback.status, booking.status, booking.startTime, "
            + "booking.totalAmount, staffUser.fullName from Feedback feedback "
            + "join User customer on customer.userId=feedback.customerId "
            + "join Booking booking on booking.bookingId=feedback.bookingId "
            + "join Staff staff on staff.staffId=booking.staffId join staff.user staffUser "
            + "order by case feedback.status when com.housekeeping.management.enums.FeedbackStatus.OPEN then 0 "
            + "when com.housekeeping.management.enums.FeedbackStatus.RESPONDED then 1 else 2 end, feedback.feedbackId desc")
    List<Object[]> findAllForPortal();
}
