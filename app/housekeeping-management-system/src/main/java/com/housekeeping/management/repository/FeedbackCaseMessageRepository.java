package com.housekeeping.management.repository;

import com.housekeeping.management.entity.FeedbackCaseMessage;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FeedbackCaseMessageRepository extends JpaRepository<FeedbackCaseMessage, Long> {
    List<FeedbackCaseMessage> findAllByCaseIdOrderBySentAtAscMessageIdAsc(Long caseId);
    Optional<FeedbackCaseMessage> findFirstByCaseIdAndSenderRoleOrderByMessageIdAsc(Long caseId, String senderRole);
    Optional<FeedbackCaseMessage> findFirstByCaseIdAndSenderRoleAndSenderUserIdOrderByMessageIdAsc(
            Long caseId, String senderRole, Long senderUserId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select message from FeedbackCaseMessage message where message.messageId=:id")
    Optional<FeedbackCaseMessage> findByIdForUpdate(@Param("id") Long id);

    @Query("select message.messageText from FeedbackCaseMessage message where message.caseId=:caseId "
            + "and message.messageId=:messageId and message.senderUserId=:adminId and message.senderRole='ADMIN'")
    Optional<String> findAdminMessageText(@Param("caseId") Long caseId, @Param("messageId") Long messageId,
            @Param("adminId") Long adminId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from FeedbackCaseMessage message where message.messageId=:messageId and message.caseId=:caseId "
            + "and message.senderUserId=:actorId and message.senderRole=:role and message.legacyImported=false "
            + "and not exists (select evidence.evidenceId from FeedbackCaseEvidence evidence where evidence.messageId=message.messageId)")
    int discardUnsent(@Param("caseId") Long caseId, @Param("messageId") Long messageId,
            @Param("actorId") Long actorId, @Param("role") String role);

    @Query("select (count(message)>0) from FeedbackCaseMessage message where message.caseId=:caseId "
            + "and message.messageId=:messageId and message.senderUserId=:actorId and message.senderRole=:role")
    boolean existsOwnedMessage(@Param("caseId") Long caseId, @Param("messageId") Long messageId,
            @Param("actorId") Long actorId, @Param("role") String role);
}
