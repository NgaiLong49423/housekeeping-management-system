package com.housekeeping.management.service.impl;

import com.housekeeping.management.repository.BookingPersistenceRepository;
import com.housekeeping.management.service.BookingStaffEligibilityService;
import org.springframework.stereotype.Service;

@Service
public class BookingStaffEligibilityServiceImpl implements BookingStaffEligibilityService {
    private final BookingPersistenceRepository bookings;

    public BookingStaffEligibilityServiceImpl(BookingPersistenceRepository bookings) {
        this.bookings = bookings;
    }

    @Override
    public boolean hasUnfinishedBookingForStaff(Long staffId) {
        return bookings.hasUnfinishedBookingForStaff(staffId);
    }
}
