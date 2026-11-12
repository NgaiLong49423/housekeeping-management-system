package com.housekeeping.management.service;

import com.housekeeping.management.domain.StaffType;
import com.housekeeping.management.domain.UserRole;
import com.housekeeping.management.domain.UserStatus;
import com.housekeeping.management.entity.Services;
import com.housekeeping.management.entity.House;
import com.housekeeping.management.entity.Staff;
import com.housekeeping.management.entity.User;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public interface BookingReservationService {
    public enum TravelStatus { FEASIBLE, OVERLAP, TOO_EARLY, TOO_LATE, DISTANCE_LIMIT_EXCEEDED, ROUTE_UNAVAILABLE }

    public record TravelAssessment(TravelStatus status,String message,LocalDateTime earliestStart,LocalDateTime latestStart,
    int routeMinutes,int availableGapMinutes,int requiredGapMinutes,Long relatedBookingId) {
        public boolean feasible(){return status==TravelStatus.FEASIBLE;}
        public static TravelAssessment unavailable(String message){return new TravelAssessment(TravelStatus.ROUTE_UNAVAILABLE,message,null,null,0,0,0,null);}
    }
    public Long createBooking(Long customerId, Long staffId, Long houseId, LocalDateTime start, List<Long> requestedServiceIds, String areaNote);
    public boolean hasInsufficientTravelGap(Long staffId, LocalDateTime start, LocalDateTime end, BigDecimal latitude, BigDecimal longitude, Long exceptBookingId);
    java.util.Optional<BookingPaymentState> lockBookingForPayment(Long bookingId);
    java.util.Optional<BookingPaymentState> lockBookingForEvidence(Long bookingId);
    java.util.Optional<Long> bookingCustomerId(Long bookingId);
    java.math.BigDecimal amountForPayment(Long bookingId);
    int completeBookingAfterPayment(Long bookingId);
    java.util.Optional<BookingFeedbackState> lockBookingForFeedback(Long bookingId);
    java.util.List<Long> futureConfirmedBookingIds(Long staffId, java.time.LocalDate startDate, java.time.LocalDate endDate);
    record BookingPaymentState(Long customerId, Long staffId, String status) { }
    record BookingFeedbackState(Long customerId, String status, java.time.LocalDateTime serviceCompletedAt) { }
    public TravelAssessment assessTravel(Long staffId,LocalDateTime start,LocalDateTime end, BigDecimal latitude,BigDecimal longitude,Long exceptBookingId);static void validateRequestedStart(LocalDateTime start, LocalDateTime now) {
        if (start == null) throw new IllegalArgumentException("Hãy chọn giờ bắt đầu.");
        if (start.isBefore(now.plusMinutes(120))) throw new IllegalArgumentException("Lịch phải được đặt trước giờ bắt đầu ít nhất 120 phút.");
        if (start.isAfter(now.plusDays(30))) throw new IllegalArgumentException("Chỉ có thể đặt lịch trong 30 ngày tới.");
        if (start.getSecond()!=0 || start.getNano()!=0 || start.getMinute()%30!=0) throw new IllegalArgumentException("Giờ bắt đầu chỉ được chọn phút 00 hoặc 30.");
    }
}
