package com.housekeeping.management.service;

import static com.housekeeping.management.dto.response.PortalViews.*;
import java.time.OffsetDateTime;
import java.time.LocalDateTime;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.time.ZoneId;
import java.util.List;
import com.housekeeping.management.enums.FeedbackTopic;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import com.housekeeping.management.web.PortalUser;

public interface PortalQueryService {
    public record BusyInterval(Long staffId, OffsetDateTime start, OffsetDateTime end) {
        public String getLabel() { return time(start) + " → " + time(end); }
    }
    public List<FeedbackCard> feedback();
    public List<ServiceCard> services();
    public List<ServiceCard> services(PortalUser user);
    public List<StaffCard> staff(PortalUser user);
    public List<ScheduleCard> schedules(PortalUser user);
    public boolean isPartTime(Long staffId);
    public List<BusyInterval> busyIntervals();
    public List<BookingCard> bookings(PortalUser user);
    public BookingCard booking(Long id, PortalUser user);
    public void authorize(BookingCard card, PortalUser user);
    public List<DetailCard> details(Long bookingId, PortalUser user);
    public List<EvidenceCard> bookingImages(Long bookingId, PortalUser user);
    public Long bookingIdForDetail(Long detailId, PortalUser user);
    public String evidencePath(Long evidenceId, PortalUser user);}
