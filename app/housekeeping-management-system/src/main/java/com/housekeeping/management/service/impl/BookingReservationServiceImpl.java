package com.housekeeping.management.service.impl;

import com.housekeeping.management.integration.vietmap.VietmapClient;
import com.housekeeping.management.service.BookingReservationService;
import com.housekeeping.management.service.HouseService;
import com.housekeeping.management.service.AuthService;
import com.housekeeping.management.service.ServiceManagementService;
import com.housekeeping.management.service.StaffManagementService;
import com.housekeeping.management.service.CompanySettingsService;

import com.housekeeping.management.domain.SpecificAddressRules;
import com.housekeeping.management.domain.StaffType;
import com.housekeeping.management.entity.Services;
import com.housekeeping.management.entity.House;
import com.housekeeping.management.entity.Staff;
import com.housekeeping.management.entity.User;
import com.housekeeping.management.repository.BookingPersistenceRepository;
import com.housekeeping.management.repository.BookingDetailRepository;
import com.housekeeping.management.entity.BookingDetail;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BookingReservationServiceImpl implements BookingReservationService {
    private static final ZoneOffset BUSINESS_OFFSET = ZoneOffset.ofHours(7);
    private static final int MAX_AREA_NOTE_LENGTH = 2000;
    private final BookingPersistenceRepository bookings;
    private final HouseService houseService;
    private final AuthService auth;
    private final StaffManagementService staffManagement;
    private final ServiceManagementService serviceManagement;
    private final VietmapClient vietmap;
    private final CompanySettingsService companySettings;
    private final BookingDetailRepository bookingDetails;

    public BookingReservationServiceImpl(BookingPersistenceRepository bookings, HouseService houseService,
            AuthService auth, StaffManagementService staffManagement, ServiceManagementService serviceManagement,
            VietmapClient vietmap, CompanySettingsService companySettings, BookingDetailRepository bookingDetails) {
        this.bookings = bookings;
        this.houseService = houseService;
        this.auth = auth;
        this.staffManagement = staffManagement;
        this.serviceManagement = serviceManagement;
        this.vietmap = vietmap;
        this.companySettings = companySettings;
        this.bookingDetails = bookingDetails;
    }

    @Transactional
    public Long createBooking(Long customerId, Long staffId, Long houseId, LocalDateTime start,
            List<Long> requestedServiceIds, String areaNote) {
        User customer = auth.findActiveCustomer(customerId);
        validateRequestedStart(start, now());
        String normalizedAreaNote = cleanAreaNote(areaNote);

        // Lock order is always House then Staff. The Staff lock serializes overlap checks, including empty ranges.
        House house = houseService.findCustomerHouseForUpdate(customerId, houseId);
        Staff staff = staffManagement.findActiveForBookingUpdate(staffId);
        companySettings.lockSettingsForScheduling();
        List<BookingPersistenceRepository.ServiceSnapshot> selectedServices = selectedServices(requestedServiceIds);
        int totalDurationMinutes = selectedServices.stream().mapToInt(BookingPersistenceRepository.ServiceSnapshot::durationMinutes)
                .reduce(0, Math::addExact);
        BigDecimal totalAmount = selectedServices.stream().map(BookingPersistenceRepository.ServiceSnapshot::price)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        LocalDateTime end = start.plusMinutes(totalDurationMinutes);
        if (end.toLocalDate().isAfter(start.toLocalDate())) throw new IllegalArgumentException("Booking phải kết thúc trong cùng ngày và khung giờ hoạt động.");
        if (!companySettings.acceptsInterval(start.toLocalDate(), start.toLocalTime(), end.toLocalTime())) {
            throw new IllegalArgumentException("Toàn bộ lịch phải kết thúc trong cùng một khung giờ hoạt động của công ty.");
        }
        if (staffManagement.isOnApprovedLeave(staffId, start.toLocalDate())) throw new IllegalArgumentException("Nhân viên đã được duyệt nghỉ trong ngày bắt đầu này.");
        if (staff.getStaffType() == StaffType.PART_TIME && !staffManagement.acceptsAvailabilityInterval(staffId, start.toLocalDate(), start.toLocalTime(), end.toLocalTime())) {
            throw new IllegalArgumentException("Toàn bộ lịch phải nằm trong một khoảng sẵn sàng đang hiệu lực của nhân viên.");
        }
        if (house.getLatitude() == null || house.getLongitude() == null) throw new IllegalArgumentException("Địa chỉ này chưa có tọa độ VietMap. Hãy cập nhật địa chỉ bằng kết quả gợi ý.");
        if (!SpecificAddressRules.isSpecific(house.getAddressDetail())) {
            throw new IllegalArgumentException("Địa chỉ này chưa có số nhà và tên đường cụ thể. Hãy cập nhật địa chỉ trong mục Nhà của tôi trước khi đặt lịch.");
        }
        if (!houseService.isWithinServiceRadius(house.getLatitude(), house.getLongitude())) throw new IllegalArgumentException("Địa chỉ nằm ngoài bán kính phục vụ hiện tại của công ty.");
        TravelAssessment travel = assessTravel(staffId,start,end,house.getLatitude(),house.getLongitude(),null);
        if (!travel.feasible()) {
            if (travel.status()==TravelStatus.ROUTE_UNAVAILABLE) throw new IllegalStateException(travel.message());
            throw new IllegalArgumentException(travel.message());
        }
        validateRequestedStart(start, now());

        Long bookingId = bookings.insertBooking(customer.getUserId(), staff.getStaffId(), house.getHouseId(),
                house.getFullAddress(), house.getLatitude(), house.getLongitude(), house.getAddressNote(),
                start, end, totalDurationMinutes, totalAmount, normalizedAreaNote, now());
        bookingDetails.saveAll(selectedServices.stream().map(snapshot -> {
            BookingDetail detail = new BookingDetail();
            detail.setBookingId(bookingId);
            detail.setServiceId(snapshot.id());
            detail.setServiceNameSnapshot(snapshot.name());
            detail.setUnitPrice(snapshot.price());
            detail.setDurationMinutes(snapshot.durationMinutes());
            return detail;
        }).toList());
        return bookingId;
    }

    public boolean hasInsufficientTravelGap(Long staffId, LocalDateTime start, LocalDateTime end,
            BigDecimal latitude, BigDecimal longitude, Long exceptBookingId) {
        return !assessTravel(staffId,start,end,latitude,longitude,exceptBookingId).feasible();
    }

    @Override
    @Transactional
    public java.util.Optional<BookingPaymentState> lockBookingForPayment(Long bookingId) {
        return bookings.lockBooking(bookingId).map(booking -> new BookingPaymentState(
                booking.customerId(), booking.staffId(), booking.status()));
    }

    @Override
    @Transactional
    public java.util.Optional<BookingPaymentState> lockBookingForEvidence(Long bookingId) {
        return bookings.lockBooking(bookingId).map(booking -> new BookingPaymentState(
                booking.customerId(), booking.staffId(), booking.status()));
    }

    @Override
    @Transactional(readOnly = true)
    public java.util.Optional<Long> bookingCustomerId(Long bookingId) {
        return bookings.findBooking(bookingId).map(BookingPersistenceRepository.BookingState::customerId);
    }

    @Override
    public BigDecimal amountForPayment(Long bookingId) {
        return bookings.bookingAmount(bookingId);
    }

    @Override
    public int completeBookingAfterPayment(Long bookingId) {
        return bookings.completeAfterPayment(bookingId);
    }

    @Override
    @Transactional
    public java.util.Optional<BookingFeedbackState> lockBookingForFeedback(Long bookingId) {
        return bookings.lockBooking(bookingId).map(booking -> new BookingFeedbackState(
                booking.customerId(), booking.status(), booking.serviceCompletedAt()));
    }

    @Override
    public List<Long> futureConfirmedBookingIds(Long staffId, java.time.LocalDate startDate, java.time.LocalDate endDate) {
        return bookings.futureConfirmedBookingIds(staffId, startDate, endDate);
    }

    public TravelAssessment assessTravel(Long staffId,LocalDateTime start,LocalDateTime end,
            BigDecimal latitude,BigDecimal longitude,Long exceptBookingId) {
        List<BookingPersistenceRepository.AppointmentPoint> overlaps=bookings.overlappingAppointments(staffId,start,end,exceptBookingId);
        if(!overlaps.isEmpty()) {
            BookingPersistenceRepository.AppointmentPoint overlap=overlaps.getFirst();
            return new TravelAssessment(TravelStatus.OVERLAP,"Giờ đã chọn bị trùng với một booking khác ("+
                    format(overlap.start())+"–"+format(overlap.end())+"). Hãy chọn giờ khác.",null,null,0,0,0,overlap.bookingId());
        }
        List<BookingPersistenceRepository.AppointmentPoint> appointments=bookings.adjacentAppointments(staffId,start,end,exceptBookingId);
        BookingPersistenceRepository.AppointmentPoint previousAppointment=null;
        BookingPersistenceRepository.AppointmentPoint nextAppointment=null;
        LocalDateTime earliestStart=null,latestStart=null;
        int previousRoute=0,previousGap=0,previousRequired=0,nextRoute=0,nextGap=0,nextRequired=0;
        for (BookingPersistenceRepository.AppointmentPoint adjacent : appointments) {
            boolean previous = !adjacent.end().isAfter(start);
            if (previous && adjacent.end().toLocalDate().equals(start.toLocalDate())) previousAppointment=adjacent;
            if (!adjacent.start().toLocalDate().equals(start.toLocalDate())) continue;
            if(previous) {
                if(adjacent.latitude()==null||adjacent.longitude()==null)
                    return TravelAssessment.unavailable("Không thể xác minh tuyến từ booking trước vì lịch đó chưa có tọa độ.");
                boolean sameLocation=sameLocation(adjacent.latitude(),adjacent.longitude(),latitude,longitude);
                if(!sameLocation) {
                    VietmapClient.MotorcycleRoute route;
                    BigDecimal maximumDistanceMeters;
                    try {
                        maximumDistanceMeters=houseService.serviceCenter().radiusKm().movePointRight(3);
                        route=vietmap.motorcycleRoute(adjacent.latitude(),adjacent.longitude(),latitude,longitude,adjacent.end());
                    }
                    catch(IllegalStateException routeFailure) { return TravelAssessment.unavailable("Chưa xác minh được tuyến từ booking trước đến địa chỉ này. " + routeFailure.getMessage()); }
                    if(route.distanceMeters().compareTo(maximumDistanceMeters)>0)
                        return distanceLimitExceeded("từ booking trước đến booking này",route.distanceMeters(),maximumDistanceMeters,adjacent.bookingId());
                    previousRoute=route.durationMinutes();
                }
                previousRequired=Math.addExact(previousRoute,15);
                previousGap=Math.toIntExact(java.time.Duration.between(adjacent.end(),start).toMinutes());
                earliestStart=adjacent.end().plusMinutes(previousRequired);
            } else {
                nextAppointment=adjacent;
                if(adjacent.latitude()==null||adjacent.longitude()==null)
                    return TravelAssessment.unavailable("Không thể xác minh tuyến đến booking kế tiếp vì lịch đó chưa có tọa độ.");
                boolean sameLocation=sameLocation(latitude,longitude,adjacent.latitude(),adjacent.longitude());
                if(!sameLocation) {
                    VietmapClient.MotorcycleRoute route;
                    BigDecimal maximumDistanceMeters;
                    try {
                        maximumDistanceMeters=houseService.serviceCenter().radiusKm().movePointRight(3);
                        route=vietmap.motorcycleRoute(latitude,longitude,adjacent.latitude(),adjacent.longitude(),end);
                    }
                    catch(IllegalStateException routeFailure) { return TravelAssessment.unavailable("Chưa xác minh được tuyến từ địa chỉ này đến booking kế tiếp. " + routeFailure.getMessage()); }
                    if(route.distanceMeters().compareTo(maximumDistanceMeters)>0)
                        return distanceLimitExceeded("từ booking này đến booking kế tiếp",route.distanceMeters(),maximumDistanceMeters,adjacent.bookingId());
                    nextRoute=route.durationMinutes();
                }
                nextRequired=Math.addExact(nextRoute,15);
                nextGap=Math.toIntExact(java.time.Duration.between(end,adjacent.start()).toMinutes());
                latestStart=adjacent.start().minusMinutes((long)nextRequired+java.time.Duration.between(start,end).toMinutes());
            }
        }
        if(previousAppointment==null) {
            HouseService.ServiceCenter center=houseService.serviceCenter();
            try { vietmap.motorcycleMinutesBeforeArrival(center.latitude(),center.longitude(),latitude,longitude,start); }
            catch(IllegalStateException routeFailure) { return TravelAssessment.unavailable("Chưa xác minh được tuyến từ tâm công ty đến địa chỉ booking. " + routeFailure.getMessage()); }
        }
        if(earliestStart!=null&&start.isBefore(earliestStart)) {
            int rounded=roundUpToQuarterHour(earliestStart.toLocalTime().toSecondOfDay()/60);
            LocalTime firstSelectable=LocalTime.of((rounded/60)%24,rounded%60);
            boolean sameLocation=sameLocation(previousAppointment.latitude(),previousAppointment.longitude(),latitude,longitude);
            String msg=sameLocation
                    ? "Booking trước kết thúc lúc "+format(previousAppointment.end().toLocalTime())+
                            ". Nhân viên cần nghỉ 15 phút; giờ sớm nhất có thể đặt là "+format(firstSelectable)+"."
                    : "Booking trước kết thúc lúc "+format(previousAppointment.end().toLocalTime())+
                            ". Tuyến đến địa chỉ này khoảng "+previousRoute+" phút, cộng 15 phút dự phòng cần "+previousRequired+
                            " phút; hiện chỉ cách "+previousGap+" phút. Đủ thời gian từ "+format(earliestStart.toLocalTime())+
                            "; mốc đặt sớm nhất theo lưới 15 phút là "+format(firstSelectable)+".";
            return new TravelAssessment(TravelStatus.TOO_EARLY,msg,earliestStart,latestStart,previousRoute,previousGap,previousRequired,previousAppointment.bookingId());
        }
        if(latestStart!=null&&start.isAfter(latestStart)) {
            int rounded=roundDownToQuarterHour(latestStart.toLocalTime().toSecondOfDay()/60);
            LocalTime lastSelectable=LocalTime.of((Math.max(0,rounded)/60)%24,Math.max(0,rounded)%60);
            String msg="Booking kế tiếp bắt đầu lúc "+format(nextAppointment.start().toLocalTime())+
                    ". Tuyến từ địa chỉ này khoảng "+nextRoute+" phút, cộng 15 phút dự phòng cần "+nextRequired+
                    " phút; hiện còn "+nextGap+" phút. Với dịch vụ "+java.time.Duration.between(start,end).toMinutes()+
                    " phút, cần bắt đầu không muộn hơn "+
                    format(latestStart.toLocalTime())+" (mốc 15 phút cuối có thể chọn: "+format(lastSelectable)+").";
            return new TravelAssessment(TravelStatus.TOO_LATE,msg,earliestStart,latestStart,nextRoute,nextGap,nextRequired,nextAppointment.bookingId());
        }
        return new TravelAssessment(TravelStatus.FEASIBLE,"",earliestStart,latestStart,
                previousAppointment!=null?previousRoute:nextRoute,previousAppointment!=null?previousGap:nextGap,
                previousAppointment!=null?previousRequired:nextRequired,
                previousAppointment!=null?previousAppointment.bookingId():nextAppointment==null?null:nextAppointment.bookingId());
    }

    private static int roundUpToQuarterHour(int minute){return ((minute+14)/15)*15;}
    private static int roundDownToQuarterHour(int minute){return (minute/15)*15;}
    private static boolean sameLocation(BigDecimal firstLatitude,BigDecimal firstLongitude,
            BigDecimal secondLatitude,BigDecimal secondLongitude) {
        return firstLatitude!=null&&firstLongitude!=null&&secondLatitude!=null&&secondLongitude!=null
                &&firstLatitude.compareTo(secondLatitude)==0&&firstLongitude.compareTo(secondLongitude)==0;
    }
    private static TravelAssessment distanceLimitExceeded(String direction,BigDecimal distanceMeters,
            BigDecimal maximumDistanceMeters,Long bookingId) {
        BigDecimal actualKm=distanceMeters.movePointLeft(3).setScale(2,java.math.RoundingMode.CEILING);
        BigDecimal maximumKm=maximumDistanceMeters.movePointLeft(3).setScale(2,java.math.RoundingMode.HALF_UP);
        String message="Quãng đường tuyến VietMap "+direction+" là "+actualKm.toPlainString()+" km, vượt giới hạn "+
                maximumKm.toPlainString()+" km theo bán kính phục vụ Admin. Hãy chọn nhân viên hoặc ngày khác.";
        return new TravelAssessment(TravelStatus.DISTANCE_LIMIT_EXCEEDED,message,null,null,0,0,0,bookingId);
    }
    private static String format(LocalTime time){return time.format(DateTimeFormatter.ofPattern("HH:mm"));}
    private static String format(LocalDateTime time){return format(time.toLocalTime());}
    static void validateRequestedStart(LocalDateTime start, LocalDateTime now) {
        if (start == null) throw new IllegalArgumentException("Hãy chọn giờ bắt đầu.");
        if (start.isBefore(now.plusMinutes(120))) {
            throw new IllegalArgumentException("Lịch phải được đặt trước giờ bắt đầu ít nhất 120 phút.");
        }
        if (start.isAfter(now.plusDays(30))) {
            throw new IllegalArgumentException("Chỉ có thể đặt lịch trong 30 ngày tới.");
        }
        if (start.getSecond() != 0 || start.getNano() != 0 || start.getMinute() % 15 != 0) {
            throw new IllegalArgumentException("Giờ bắt đầu chỉ được chọn phút 00, 15, 30 hoặc 45.");
        }
    }

    private List<BookingPersistenceRepository.ServiceSnapshot> selectedServices(List<Long> requestedIds) {
        if (requestedIds == null || requestedIds.isEmpty() || requestedIds.stream().anyMatch(java.util.Objects::isNull)
                || requestedIds.stream().distinct().count() != requestedIds.size()) {
            throw new IllegalArgumentException("Hãy chọn ít nhất một dịch vụ hợp lệ.");
        }
        Map<Long, Services> byId = serviceManagement.findByIds(requestedIds).stream()
                .collect(Collectors.toMap(Services::getServiceId, Function.identity()));
        List<BookingPersistenceRepository.ServiceSnapshot> selected = new ArrayList<>(requestedIds.size());
        for (Long id : requestedIds) {
            Services service = byId.get(id);
            if (service == null || service.getStatus() != Services.Status.ACTIVE) {
                throw new IllegalArgumentException("Một dịch vụ đã ngừng hoạt động hoặc không tồn tại.");
            }
            selected.add(new BookingPersistenceRepository.ServiceSnapshot(service.getServiceId(),
                    service.getServiceName(), service.getPrice(), service.getDurationMinutes()));
        }
        return List.copyOf(selected);
    }

    private String cleanAreaNote(String areaNote) {
        if (areaNote == null || areaNote.isBlank()) return null;
        String normalized = areaNote.trim();
        if (normalized.length() > MAX_AREA_NOTE_LENGTH) {
            throw new IllegalArgumentException("Ghi chú khu vực không được vượt quá 2.000 ký tự.");
        }
        return normalized;
    }

    private LocalDateTime now() {
        return LocalDateTime.now(BUSINESS_OFFSET).withNano(0);
    }
}
