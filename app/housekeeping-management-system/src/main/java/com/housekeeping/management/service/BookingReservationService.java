package com.housekeeping.management.service;

import com.housekeeping.management.domain.BookingStatus;
import com.housekeeping.management.domain.entity.Booking;
import com.housekeeping.management.domain.entity.BookingDetail;
import com.housekeeping.management.domain.entity.Customer;
import com.housekeeping.management.domain.entity.Services;
import com.housekeeping.management.domain.entity.Staff;
import com.housekeeping.management.domain.entity.WorkSchedule;
import com.housekeeping.management.repository.BookingDetailRepository;
import com.housekeeping.management.repository.BookingRepository;
import com.housekeeping.management.repository.CustomerRepository;
import com.housekeeping.management.repository.ServicesRepository;
import com.housekeeping.management.repository.StaffRepository;
import com.housekeeping.management.repository.WorkScheduleRepository;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BookingReservationService {
    private static final ZoneOffset BUSINESS_OFFSET = ZoneOffset.ofHours(7);

    private final StaffRepository staffRepository;
    private final CustomerRepository customerRepository;
    private final ServicesRepository servicesRepository;
    private final WorkScheduleRepository workScheduleRepository;
    private final BookingRepository bookingRepository;
    private final BookingDetailRepository bookingDetailRepository;

    public BookingReservationService(
            StaffRepository staffRepository,
            CustomerRepository customerRepository,
            ServicesRepository servicesRepository,
            WorkScheduleRepository workScheduleRepository,
            BookingRepository bookingRepository,
            BookingDetailRepository bookingDetailRepository) {
        this.staffRepository = staffRepository;
        this.customerRepository = customerRepository;
        this.servicesRepository = servicesRepository;
        this.workScheduleRepository = workScheduleRepository;
        this.bookingRepository = bookingRepository;
        this.bookingDetailRepository = bookingDetailRepository;
    }

    @Transactional
    public Booking createBooking(
            Long customerId,
            Long staffId,
            OffsetDateTime requestedStart,
            List<ServiceSelection> requestedServices) {
        if (requestedServices == null || requestedServices.isEmpty()) {
            throw new IllegalArgumentException("A booking requires at least one service detail");
        }

        // Serializes all reservations for one Staff row, including the empty overlap range.
        Staff staff = staffRepository.findByIdForUpdate(staffId)
                .orElseThrow(() -> new IllegalArgumentException("Staff does not exist"));
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new IllegalArgumentException("Customer does not exist"));

        List<ServiceSelection> selections = requestedServices.stream()
                .sorted(Comparator.comparing(ServiceSelection::sequenceNo))
                .toList();
        Set<Integer> sequences = new HashSet<>();
        for (ServiceSelection selection : selections) {
            if (selection.sequenceNo() == null || selection.sequenceNo() <= 0
                    || !sequences.add(selection.sequenceNo())) {
                throw new IllegalArgumentException("Detail sequence numbers must be unique and positive");
            }
        }

        OffsetDateTime startAt = requestedStart.withOffsetSameInstant(BUSINESS_OFFSET).withNano(0);
        int totalDurationMinutes = 0;
        List<ResolvedSelection> resolved = new java.util.ArrayList<>();
        for (ServiceSelection selection : selections) {
            WorkSchedule schedule = workScheduleRepository.findById(selection.workScheduleId())
                    .orElseThrow(() -> new IllegalArgumentException("Work schedule does not exist"));
            Services service = servicesRepository.findById(selection.serviceId())
                    .orElseThrow(() -> new IllegalArgumentException("Service does not exist"));
            if (!schedule.getStaff().getStaffId().equals(staffId)) {
                throw new IllegalArgumentException("Every detail must use the selected Staff's schedule");
            }
            resolved.add(new ResolvedSelection(selection, schedule, service));
            totalDurationMinutes = Math.addExact(totalDurationMinutes, service.getDurationMinutes());
        }

        OffsetDateTime endAt = startAt.plusMinutes(totalDurationMinutes);
        if (bookingRepository.existsConfirmedOverlap(staffId, startAt, endAt)) {
            throw new IllegalArgumentException("The selected Staff is already booked in this interval");
        }

        OffsetDateTime detailStart = startAt;
        for (ResolvedSelection item : resolved) {
            OffsetDateTime detailEnd = detailStart.plusMinutes(item.service().getDurationMinutes());
            if (detailStart.isBefore(item.schedule().getAvailableStart())
                    || detailEnd.isAfter(item.schedule().getAvailableEnd())) {
                throw new IllegalArgumentException("A detail falls outside its referenced work schedule");
            }
            detailStart = detailEnd;
        }

        Booking booking = new Booking();
        booking.setCustomer(customer);
        booking.setStartAt(startAt);
        booking.setEndAt(endAt);
        booking.setBookingStatus(BookingStatus.CONFIRMED);
        booking.setCreatedAt(OffsetDateTime.now(BUSINESS_OFFSET).withNano(0));
        booking = bookingRepository.saveAndFlush(booking);

        for (ResolvedSelection item : resolved) {
            BookingDetail detail = new BookingDetail();
            detail.setBooking(booking);
            detail.setWorkSchedule(item.schedule());
            detail.setService(item.service());
            detail.setSequenceNo(item.selection().sequenceNo());
            detail.setDurationMinutesSnapshot(item.service().getDurationMinutes());
            detail.setUnitPriceSnapshot(item.service().getBasePrice());
            bookingDetailRepository.save(detail);
        }
        return booking;
    }

    public record ServiceSelection(Long workScheduleId, Long serviceId, Integer sequenceNo) {
    }

    private record ResolvedSelection(ServiceSelection selection, WorkSchedule schedule, Services service) {
    }
}
