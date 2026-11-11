package com.housekeeping.management.repository;

import com.housekeeping.management.entity.StaffIncidentAction;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StaffIncidentActionRepository extends JpaRepository<StaffIncidentAction, Long> {
    boolean existsByCaseIdAndStatus(Long caseId, String status);
    java.util.Optional<StaffIncidentAction> findFirstByCaseIdAndStatusOrderByActionIdDesc(Long caseId, String status);
    List<StaffIncidentAction> findAllByCaseIdOrderByCreatedAtAscActionIdAsc(Long caseId);
    List<StaffIncidentAction> findAllByStaffIdAndStatusAndActionTypeNotOrderByDecidedAtDescActionIdDesc(
            Long staffId, String status, String excludedType);

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
