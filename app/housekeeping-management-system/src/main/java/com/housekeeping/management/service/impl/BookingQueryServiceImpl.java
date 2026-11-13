package com.housekeeping.management.service.impl;

import com.housekeeping.management.enums.BookingStatus;
import com.housekeeping.management.entity.Booking;
import com.housekeeping.management.repository.BookingPersistenceRepository;
import com.housekeeping.management.repository.BookingPersistenceRepository.CustomerBookingCountRow;
import com.housekeeping.management.service.BookingQueryService;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class BookingQueryServiceImpl implements BookingQueryService {
    private static final ZoneOffset BUSINESS_OFFSET = ZoneOffset.ofHours(7);
    private static final List<BookingStatus> ACTIVE_STATUSES = List.of(
            BookingStatus.CONFIRMED, BookingStatus.AWAITING_PAYMENT);
    private final BookingPersistenceRepository bookings;

    public BookingQueryServiceImpl(BookingPersistenceRepository bookings) {
        this.bookings = bookings;
    }

    @Override
    public boolean hasActiveBookingForHouse(Long houseId) {
        return bookings.hasActiveBookingForHouse(houseId);
    }

    @Override
    public boolean hasUnfinishedBookingForCustomer(Long customerId) {
        return bookings.hasUnfinishedBookingForCustomer(customerId);
    }

    @Override
    public Map<Long, CustomerBookingCounts> bookingCountsByCustomer() {
        Map<Long, CustomerBookingCounts> counts = new HashMap<>();
        for (CustomerBookingCountRow row : bookings.countBookingsByCustomer(ACTIVE_STATUSES)) {
            long unfinished = row.getUnfinished() == null ? 0 : row.getUnfinished();
            counts.put(row.getCustomerId(), new CustomerBookingCounts(row.getTotal(), unfinished));
        }
        return counts;
    }

    @Override
    public boolean hasFutureConfirmedBookingOverlapping(Long staffId, LocalDate date, LocalTime start, LocalTime end) {
        LocalDateTime from = date.atStartOfDay();
        LocalDateTime until = date.plusDays(1).atStartOfDay();
        return !bookings.findFutureConfirmedWithinInterval(staffId, from, until,
                LocalDateTime.now(BUSINESS_OFFSET).withNano(0), date.atTime(start), date.atTime(end)).isEmpty();
    }

    @Override
    public List<Long> activeBookingIdsInInterval(LocalDate from, LocalDate until) {
        LocalDateTime start = from.atStartOfDay();
        LocalDateTime end = until == null ? null : until.atStartOfDay();
        return bookings.findActiveInInterval(start, end, ACTIVE_STATUSES).stream()
                .map(Booking::getBookingId).toList();
    }

    @Override
    public List<ActiveBookingWindow> activeBookingWindowsInInterval(LocalDate from, LocalDate until) {
        LocalDateTime start = from.atStartOfDay();
        LocalDateTime end = until == null ? null : until.atStartOfDay();
        return bookings.findActiveInInterval(start, end, ACTIVE_STATUSES).stream()
                .map(booking -> new ActiveBookingWindow(booking.getBookingId(), booking.getStartTime(), booking.getEndTime()))
                .toList();
    }

    @Override
    public List<BookingLocation> futureActiveBookingsWithLocation() {
        return bookings.findFutureActiveWithLocation(LocalDateTime.now(BUSINESS_OFFSET).withNano(0), ACTIVE_STATUSES)
                .stream().map(booking -> new BookingLocation(booking.getBookingId(), booking.getLatitudeSnapshot(),
                        booking.getLongitudeSnapshot())).toList();
    }
}
