package com.housekeeping.management.service;

import com.housekeeping.management.dto.entity.BookingTeamChangeRequestDTO;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface BookingTeamChangeService {
    void requestReduction(Long bookingId, Long adminId, Long staffId, String reason);
    Optional<BookingTeamChangeRequestDTO> pendingRequest(Long bookingId, Long customerId);
    boolean hasPendingRequest(Long bookingId);
    void assertRescheduleAllowed(Long bookingId, Long customerId);
    RescheduleInfo rescheduleInfo(Long bookingId, Long customerId);
    void proceedWithFewerStaff(Long bookingId, Long customerId);
    void cancelForTeamChange(Long bookingId, Long customerId);
    void resolvePendingAsCancelled(Long bookingId, Long actorId);
    void reschedule(Long bookingId, Long customerId, List<Long> staffIds, Long leadStaffId, LocalDateTime startAt);

    record RescheduleServiceLine(Long id, String name, java.math.BigDecimal price,
            java.math.BigDecimal additionalStaffPrice, int duration) { }

    record RescheduleInfo(Long bookingId, Long houseId, List<RescheduleServiceLine> services, List<Long> staffIds,
            Long leadStaffId, LocalDateTime startAt, String areaNote, BigDecimal amount) {
        public RescheduleInfo {
            services = List.copyOf(services);
            staffIds = List.copyOf(staffIds);
        }
        public List<Long> serviceIds() { return services.stream().map(RescheduleServiceLine::id).toList(); }
        public int durationMinutes() { return services.stream().mapToInt(RescheduleServiceLine::duration).sum(); }
    }
}
