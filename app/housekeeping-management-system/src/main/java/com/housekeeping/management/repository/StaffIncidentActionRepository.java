package com.housekeeping.management.repository;

import com.housekeeping.management.entity.StaffIncidentAction;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StaffIncidentActionRepository extends JpaRepository<StaffIncidentAction, Long> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select action from StaffIncidentAction action where action.actionId=:id")
    java.util.Optional<StaffIncidentAction> findByIdForUpdate(@Param("id") Long id);
    boolean existsByCaseIdAndStatus(Long caseId, String status);
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    java.util.Optional<StaffIncidentAction> findFirstByCaseIdAndStatusOrderByActionIdDesc(Long caseId, String status);
    List<StaffIncidentAction> findAllByCaseIdOrderByCreatedAtAscActionIdAsc(Long caseId);
    List<StaffIncidentAction> findAllByStaffIdAndStatusAndActionTypeNotOrderByDecidedAtDescActionIdDesc(
            Long staffId, String status, String excludedType);
    List<StaffIncidentAction> findAllByStaffIdAndActionTypeNotOrderByCreatedAtDescActionIdDesc(Long staffId, String excludedType);
    java.util.Optional<StaffIncidentAction> findByActionIdAndStaffId(Long actionId, Long staffId);
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    java.util.Optional<StaffIncidentAction> findFirstByCaseIdAndStatusInOrderByActionIdDesc(Long caseId, java.util.Collection<String> statuses);
    List<StaffIncidentAction> findAllByStatusAndResponseDeadlineLessThanEqual(String status, java.time.LocalDateTime deadline);


    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update StaffIncidentAction action set action.status=:status, action.decidedBy=:manager, "
            + "action.decidedAt=:at where action.actionId=:id and action.status='PENDING' and action.proposedBy<>:manager")
    int decide(@Param("id") Long id, @Param("manager") Long manager, @Param("status") String status,
            @Param("at") java.time.LocalDateTime at);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update StaffIncidentAction action set action.acknowledgedAt=:at where action.actionId=:id "
            + "and action.staffId=:staff and action.status='APPROVED' and action.acknowledgedAt is null")
    int acknowledge(@Param("id") Long id, @Param("staff") Long staff,
            @Param("at") java.time.LocalDateTime at);
}
