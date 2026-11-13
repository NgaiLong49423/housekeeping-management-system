package com.housekeeping.management.service.impl;

import com.housekeeping.management.service.BookingAvailabilityService;
import com.housekeeping.management.service.BookingReservationService;
import com.housekeeping.management.service.CompanySettingsService;
import com.housekeeping.management.service.HouseService;
import com.housekeeping.management.service.ServiceManagementService;

import com.housekeeping.management.dto.request.PortalForms.OperatingHoursForm;
import com.housekeeping.management.dto.response.BookingAvailabilityOptions;
import com.housekeeping.management.dto.response.BookingAvailabilityOptions.StaffOption;
import com.housekeeping.management.dto.response.BookingAvailabilityOptions.TimeOption;
import com.housekeeping.management.dto.response.BookingAvailabilityDates;
import com.housekeeping.management.dto.SpecificAddressRules;
import com.housekeeping.management.enums.TravelStatus;
import com.housekeeping.management.entity.Services;
import com.housekeeping.management.entity.House;
import com.housekeeping.management.repository.BookingAvailabilityRepository;
import com.housekeeping.management.repository.BookingAvailabilityRepository.Interval;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BookingAvailabilityServiceImpl implements BookingAvailabilityService {
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private final BookingAvailabilityRepository availability;
    private final CompanySettingsService companySettings;
    private final ServiceManagementService services;
    private final HouseService houses;
    private final BookingReservationService reservations;

    public BookingAvailabilityServiceImpl(BookingAvailabilityRepository availability,
            CompanySettingsService companySettings, ServiceManagementService services,HouseService houses,
            BookingReservationService reservations) {
        this.availability = availability;
        this.companySettings = companySettings;
        this.services = services;
        this.houses=houses;
        this.reservations=reservations;
    }

    public BookingAvailabilityOptions options(LocalDate date,List<Long> serviceIds,Long selectedStaffId,
            LocalTime selectedTime,Long customerId,Long houseId) {
        return optionsForStaffIds(date, serviceIds, selectedStaffId == null ? List.of() : List.of(selectedStaffId),
                selectedTime, customerId, houseId);
    }

    @Override
    public BookingAvailabilityOptions optionsForStaffIds(LocalDate date, List<Long> serviceIds, List<Long> selectedStaffIds,
            LocalTime selectedTime, Long customerId, Long houseId) {
        return optionsForStaffIds(date, serviceIds, selectedStaffIds, selectedTime, customerId, houseId, null);
    }

    @Override
    public BookingAvailabilityOptions optionsForStaffIds(LocalDate date, List<Long> serviceIds, List<Long> selectedStaffIds,
            LocalTime selectedTime, Long customerId, Long houseId, Long excludeBookingId) {
        return optionsForStaffIds(date, serviceIds, selectedStaffIds, selectedTime, customerId, houseId, excludeBookingId, null);
    }

    @Override
    public BookingAvailabilityOptions optionsForStaffIds(LocalDate date, List<Long> serviceIds, List<Long> selectedStaffIds,
            LocalTime selectedTime, Long customerId, Long houseId, Long excludeBookingId, Integer durationOverride) {
        int duration = durationOverride == null ? serviceDuration(serviceIds) : validateDuration(durationOverride);
        House house=requireBookableHouse(customerId,houseId);
        return options(date,duration,selectedStaffIds,selectedTime,house.getLatitude(),house.getLongitude(),excludeBookingId);
    }

    @Transactional(readOnly = true)
    private BookingAvailabilityOptions options(LocalDate date,int duration,List<Long> selectedStaffIds,
            LocalTime selectedTime,java.math.BigDecimal latitude,java.math.BigDecimal longitude,Long excludeBookingId) {
        if (selectedStaffIds == null || selectedStaffIds.stream().anyMatch(java.util.Objects::isNull)
                || selectedStaffIds.stream().distinct().count() != selectedStaffIds.size())
            throw new IllegalArgumentException("Danh sách nhân viên được chọn không hợp lệ.");
        Set<Long> selectedIds = Set.copyOf(selectedStaffIds);
        LocalDate today = LocalDate.now(BUSINESS_ZONE);
        if (date == null || date.isBefore(today) || date.isAfter(today.plusDays(30))) {
            throw new IllegalArgumentException("Chỉ tra cứu lịch từ hôm nay đến 30 ngày tới.");
        }
        int day = date.getDayOfWeek().getValue();
        List<Interval> companyWindows = companySettings.operatingHoursForDate(date).stream()
                .filter(window -> window.dayOfWeek() == day && !window.closed() && window.openingTime() != null && window.closingTime() != null)
                .map(window -> new Interval(null, window.openingTime(), window.closingTime())).toList();
        List<BookingAvailabilityRepository.StaffOption> staff = availability.activeStaff();
        List<Interval> partTimeHours = availability.activeAvailability(date);
        Set<Long> onLeave = new HashSet<>(availability.staffOnApprovedLeave(date));
        List<Interval> busy = availability.bookedIntervals(date).stream()
                .filter(interval -> excludeBookingId == null || !excludeBookingId.equals(interval.bookingId())).toList();
        LocalDateTime earliest = LocalDateTime.now(BUSINESS_ZONE).plusMinutes(120);
        List<LocalTime> slots = generateSlots(date, duration, companyWindows, earliest);
        if (selectedTime != null && (selectedTime.getSecond() != 0 || selectedTime.getNano() != 0 || selectedTime.getMinute() % 30 != 0)) {
            throw new IllegalArgumentException("Giờ bắt đầu chỉ được chọn phút 00 hoặc 30.");
        }

        List<BookingReservationService.TravelAssessment> selectedTravelChecks=new ArrayList<>();
        List<String> travelMessages=new ArrayList<>();
        List<TimeOption> timeOptions = slots.stream().map(time -> {
            List<BookingAvailabilityRepository.StaffOption> candidates=staff.stream()
                    .filter(person -> selectedIds.isEmpty() || selectedIds.contains(person.id()))
                    .filter(person -> selectedTime!=null&&selectedTime.equals(time)
                            ? eligibleForSchedule(person,date,time,duration,companyWindows,partTimeHours,onLeave)
                            : eligible(person,date,time,duration,companyWindows,partTimeHours,onLeave,busy)).toList();
            List<StaffOption> feasibleStaff=new ArrayList<>();
            for(var person:candidates) {
                if(selectedTime==null||!selectedTime.equals(time)) {
                    feasibleStaff.add(new StaffOption(person.id(),person.name(),person.staffType()));
                    continue;
                }
                BookingReservationService.TravelAssessment check=reservations.assessTravel(person.id(),date.atTime(time),
                        date.atTime(time).plusMinutes(duration),latitude,longitude,excludeBookingId);
                selectedTravelChecks.add(check);
                if(check.feasible()) {
                    feasibleStaff.add(new StaffOption(person.id(),person.name(),person.staffType()));
                } else {
                    travelMessages.add(check.message());
                }
            }
            return new TimeOption(format(time),feasibleStaff);
        }).filter(option -> !option.staffOptions().isEmpty()).toList();
        if (!selectedIds.isEmpty()) {
            timeOptions = timeOptions.stream().filter(option -> option.staffOptions().size() == selectedIds.size()
                    && option.staffOptions().stream().map(StaffOption::id).collect(Collectors.toSet()).containsAll(selectedIds)).toList();
        }
        boolean selectedSlotFeasible = selectedTime != null && timeOptions.stream()
                .anyMatch(option -> option.startTime().equals(format(selectedTime)));
        if (selectedSlotFeasible) {
            timeOptions = timeOptions.stream().filter(option -> option.startTime().equals(format(selectedTime))).toList();
        }
        List<String> validTimes = timeOptions.stream().map(TimeOption::startTime).toList();

        List<StaffOption> validStaff=selectedTime==null?staff.stream()
                .filter(person->selectedIds.isEmpty()||selectedIds.contains(person.id()))
                .map(person->new StaffOption(person.id(),person.name(),person.staffType())).toList()
                :timeOptions.stream().filter(option->option.startTime().equals(format(selectedTime)))
                        .findFirst().map(TimeOption::staffOptions).orElse(List.of());
        if (!selectedIds.isEmpty() && validStaff.size() != selectedIds.size()) validStaff = List.of();
        String message = availabilityMessage(date,selectedIds,selectedTime,staff,companyWindows,partTimeHours,
                onLeave,timeOptions,validStaff);
        LocalDateTime earliestStart=null,latestStart=null;
        if(selectedTime!=null&&!selectedIds.isEmpty()&&!selectedTravelChecks.isEmpty()) {
            var check=selectedTravelChecks.getFirst();earliestStart=check.earliestStart();latestStart=check.latestStart();
            if(!check.feasible())message=check.message();
        } else if(selectedTime!=null&&validStaff.isEmpty()) {
            if(travelMessages.stream().anyMatch(text->text.startsWith("Booking trước")))message=travelMessages.stream().filter(text->text.startsWith("Booking trước")).findFirst().orElse(message);
            else if(!travelMessages.isEmpty())message=String.join(" ",travelMessages.stream().distinct().limit(3).toList());
            earliestStart=selectedTravelChecks.stream().map(BookingReservationService.TravelAssessment::earliestStart)
                    .filter(java.util.Objects::nonNull).min(LocalDateTime::compareTo).orElse(null);
            latestStart=selectedTravelChecks.stream().map(BookingReservationService.TravelAssessment::latestStart)
                    .filter(java.util.Objects::nonNull).max(LocalDateTime::compareTo).orElse(null);
        }
        if(earliestStart!=null) {
            int earliestMinute=earliestStart.toLocalTime().toSecondOfDay()/60;
            int halfHour=((earliestMinute+29)/30)*30;
            Set<String> filteredTimes=validTimes.stream().filter(value->LocalTime.parse(value).toSecondOfDay()/60>=halfHour)
                    .collect(Collectors.toSet());
            validTimes=validTimes.stream().filter(filteredTimes::contains).toList();
            timeOptions=timeOptions.stream().filter(option->filteredTimes.contains(option.startTime())).toList();
        }
        return new BookingAvailabilityOptions(validStaff, validTimes,timeOptions,message,
                earliestStart==null?null:format(earliestStart.toLocalTime()),latestStart==null?null:format(latestStart.toLocalTime()));
    }

    public BookingAvailabilityDates availableDates(List<Long> serviceIds, Long selectedStaffId, Long customerId, Long houseId) {
        return availableDatesForStaffIds(serviceIds, selectedStaffId == null ? List.of() : List.of(selectedStaffId), customerId, houseId);
    }

    @Override
    public BookingAvailabilityDates availableDatesForStaffIds(List<Long> serviceIds, List<Long> selectedStaffIds, Long customerId, Long houseId) {
        return availableDatesForStaffIds(serviceIds, selectedStaffIds, customerId, houseId, null);
    }

    @Override
    public BookingAvailabilityDates availableDatesForStaffIds(List<Long> serviceIds, List<Long> selectedStaffIds,
            Long customerId, Long houseId, Long excludeBookingId) {
        return availableDatesForStaffIds(serviceIds, selectedStaffIds, customerId, houseId, excludeBookingId, null);
    }

    @Override
    public BookingAvailabilityDates availableDatesForStaffIds(List<Long> serviceIds, List<Long> selectedStaffIds,
            Long customerId, Long houseId, Long excludeBookingId, Integer durationOverride) {
        LocalDate today=LocalDate.now(BUSINESS_ZONE);
        LocalDate through=today.plusDays(30);
        int duration=durationOverride == null ? serviceDuration(serviceIds) : validateDuration(durationOverride);
        House selectedHouse = requireBookableHouse(customerId, houseId);
        java.math.BigDecimal latitude = selectedHouse.getLatitude(), longitude = selectedHouse.getLongitude();
        if (selectedStaffIds == null || selectedStaffIds.stream().anyMatch(java.util.Objects::isNull)
                || selectedStaffIds.stream().distinct().count() != selectedStaffIds.size())
            throw new IllegalArgumentException("Danh sách nhân viên được chọn không hợp lệ.");
        Set<Long> selectedIds = Set.copyOf(selectedStaffIds);
        List<BookingAvailabilityRepository.StaffOption> staff=availability.activeStaff();
        if(!selectedIds.isEmpty()&&selectedIds.stream().anyMatch(id->staff.stream().noneMatch(person->person.id().equals(id)))) {
            return new BookingAvailabilityDates(List.of(),"Nhân viên đã chọn không còn hoạt động.");
        }
        Map<LocalDate,List<Interval>> partTimeHours=availability.activeAvailability(today,through).stream()
                .collect(Collectors.groupingBy(BookingAvailabilityRepository.DatedInterval::date,
                        Collectors.mapping(item->new Interval(item.staffId(),item.start(),item.end()),Collectors.toList())));
        List<BookingAvailabilityRepository.LeaveRange> leaveRanges=availability.approvedLeaves(today,through);
        Map<LocalDate,List<Interval>> busy=availability.bookedIntervals(today,through).stream()
                .filter(item -> excludeBookingId == null || !excludeBookingId.equals(item.bookingId()))
                .collect(Collectors.groupingBy(BookingAvailabilityRepository.DatedInterval::date,
                        Collectors.mapping(item->new Interval(item.staffId(),item.start(),item.end()),Collectors.toList())));
        List<CompanySettingsService.ScheduleWindow> companyVersions=companySettings.scheduleWindows();
        LocalDateTime earliest=LocalDateTime.now(BUSINESS_ZONE).plusMinutes(120);
        List<LocalDate> result=new ArrayList<>();
        boolean routeUnavailable=false;
        availabilityScan:
        for(LocalDate date=today;!date.isAfter(through);date=date.plusDays(1)) {
            LocalDate candidateDate=date;
            int weekday=date.getDayOfWeek().getValue();
            LocalDate effectiveFrom=companyVersions.stream().map(CompanySettingsService.ScheduleWindow::effectiveFrom)
                    .filter(version->!version.isAfter(candidateDate)).max(LocalDate::compareTo).orElse(null);
            List<Interval> companyWindows=effectiveFrom==null?List.of():companyVersions.stream()
                    .filter(window->window.effectiveFrom().equals(effectiveFrom)&&window.dayOfWeek()==weekday&&!window.closed()
                            &&window.openingTime()!=null&&window.closingTime()!=null)
                    .map(window->new Interval(null,window.openingTime(),window.closingTime())).toList();
            if(companyWindows.isEmpty())continue;
            Set<Long> onLeave=leaveRanges.stream().filter(range->!candidateDate.isBefore(range.start())&&!candidateDate.isAfter(range.end()))
                    .map(BookingAvailabilityRepository.LeaveRange::staffId).collect(Collectors.toSet());
            List<Interval> staffHours=partTimeHours.getOrDefault(date,List.of());
            List<Interval> busyThatDay=busy.getOrDefault(date,List.of());
            boolean found=false;
            for(LocalTime time:generateSlots(date,duration,companyWindows,earliest)) {
                List<BookingAvailabilityRepository.StaffOption> candidates = selectedIds.isEmpty() ? staff
                        : staff.stream().filter(person -> selectedIds.contains(person.id())).toList();
                List<BookingAvailabilityRepository.StaffOption> feasibleTeam = new ArrayList<>();
                for(BookingAvailabilityRepository.StaffOption person:candidates) {
                    if(!eligible(person,candidateDate,time,duration,companyWindows,staffHours,onLeave,busyThatDay))continue;
                    BookingReservationService.TravelAssessment assessment=reservations.assessTravel(person.id(),
                            candidateDate.atTime(time),candidateDate.atTime(time).plusMinutes(duration),latitude,longitude,excludeBookingId);
                    if(assessment.status()==TravelStatus.ROUTE_UNAVAILABLE) {
                        routeUnavailable=true;
                        break availabilityScan;
                    }
                    if(assessment.feasible()) feasibleTeam.add(person);
                }
                found = selectedIds.isEmpty() ? !feasibleTeam.isEmpty() : feasibleTeam.size() == selectedIds.size();
                if(found)break;
            }
            if(found)result.add(date);
        }
        String message;
        if (routeUnavailable) {
            message = result.isEmpty() ? "Chưa thể xác minh tuyến di chuyển với VietMap. Hãy thử tải lịch lại sau."
                    : "Một số ngày chưa thể xác minh tuyến với VietMap; các ngày đang hiển thị đã được kiểm tra.";
        } else if (!result.isEmpty()) {
            message = selectedIds.isEmpty()
                    ? "Các ngày dưới đây có ít nhất một nhân viên phù hợp. Chọn ngày để xem từng giờ và nhóm nhân viên cùng rảnh."
                    : "Chỉ hiển thị ngày và giờ mà toàn bộ nhóm đã chọn cùng khả dụng.";
        } else if (!selectedIds.isEmpty()) {
            List<String> partTimeWithoutAvailability = staff.stream()
                    .filter(person -> selectedIds.contains(person.id()))
                    .filter(person -> "PART_TIME".equals(person.staffType()))
                    .filter(person -> partTimeHours.values().stream().flatMap(List::stream)
                            .noneMatch(interval -> person.id().equals(interval.staffId())))
                    .map(BookingAvailabilityRepository.StaffOption::name).toList();
            message = partTimeWithoutAvailability.isEmpty()
                    ? "Nhóm này chưa có ngày giờ chung trong 30 ngày tới. Hãy thử đổi thành viên hoặc dịch vụ; giờ gợi ý chỉ hiện khi tất cả người trong nhóm cùng rảnh đủ thời lượng."
                    : "Chưa có giờ chung vì nhân viên bán thời gian " + String.join(", ", partTimeWithoutAvailability)
                            + " chưa đăng ký ca trong 30 ngày tới. Hãy cập nhật lịch làm việc hoặc bỏ nhân viên này khỏi nhóm.";
        } else {
            message = "Không có ngày nào trong 30 ngày tới còn giờ và nhân viên phù hợp.";
        }
        return new BookingAvailabilityDates(result,message);
    }

    private House requireBookableHouse(Long customerId, Long houseId) {
        House house = houses.findCustomerHouse(customerId, houseId);
        if (house.getLatitude() == null || house.getLongitude() == null) {
            throw new IllegalArgumentException("Địa chỉ nhà chưa có tọa độ VietMap. Hãy cập nhật địa chỉ trước khi xem giờ.");
        }
        if (!SpecificAddressRules.isSpecific(house.getAddressDetail())) {
            throw new IllegalArgumentException("Địa chỉ nhà cần có số nhà và tên đường cụ thể trước khi xem giờ.");
        }
        if (!houses.isWithinServiceRadius(house.getLatitude(), house.getLongitude())) {
            throw new IllegalArgumentException("Địa chỉ nằm ngoài bán kính phục vụ hiện tại của công ty.");
        }
        return house;
    }

    private String availabilityMessage(LocalDate date,Set<Long> selectedStaffIds,LocalTime selectedTime,
            List<BookingAvailabilityRepository.StaffOption> staff,List<Interval> companyWindows,
            List<Interval> partTimeHours,Set<Long> onLeave,List<TimeOption> timeOptions,List<StaffOption> validStaff) {
        if (companyWindows.isEmpty()) return "Admin đang cấu hình nghỉ vào ngày này.";
        if (selectedTime != null && validStaff.isEmpty()) return selectedStaffIds.isEmpty()
                ? "Không có nhân viên đủ điều kiện nhận dịch vụ vào giờ này; có thể họ chưa đăng ký, đã nghỉ hoặc đang có booking."
                : "Không phải toàn bộ nhóm đã chọn cùng rảnh vào giờ này. Hãy chọn giờ khác hoặc đổi thành viên.";
        if (selectedTime != null) return selectedStaffIds.isEmpty()
                ? "Có " + validStaff.size() + " nhân viên phù hợp với giờ " + format(selectedTime) + "."
                : "Cả nhóm " + validStaff.size() + " người cùng có thể làm vào giờ " + format(selectedTime) + ".";
        if (!selectedStaffIds.isEmpty()) {
            if (selectedStaffIds.stream().anyMatch(id -> staff.stream().noneMatch(item -> item.id().equals(id))))
                return "Một nhân viên đã chọn không còn hoạt động hoặc không tồn tại.";
            if (selectedStaffIds.stream().anyMatch(onLeave::contains)) return "Có nhân viên trong nhóm đã được duyệt nghỉ vào ngày này.";
            if (selectedStaffIds.stream().anyMatch(id -> staff.stream().filter(item -> item.id().equals(id)).anyMatch(person ->
                    "PART_TIME".equals(person.staffType()) && partTimeHours.stream().noneMatch(period -> id.equals(period.staffId()))))) {
                return "Có nhân viên trong nhóm chưa đăng ký giờ làm vào ngày này.";
            }
            if (timeOptions.isEmpty()) return "Nhân viên có lịch trong ngày nhưng không còn giờ phù hợp với thời lượng dịch vụ, thời gian báo trước hoặc booking khác.";
        }
        if (timeOptions.isEmpty() && !staff.isEmpty() && staff.stream().allMatch(person -> "PART_TIME".equals(person.staffType()))
                && partTimeHours.isEmpty()) {
            return "Chưa có nhân viên part-time đăng ký giờ làm vào ngày này.";
        }
        if (timeOptions.isEmpty()) return "Không có nhân viên và giờ làm phù hợp trong ngày này.";
        return "Chọn một giờ để xem những nhân viên có thể nhận đủ thời lượng dịch vụ.";
    }

    private static String format(LocalTime time) { return String.format("%02d:%02d",time.getHour(),time.getMinute()); }

    private int serviceDuration(List<Long> requestedIds) {
        if (requestedIds == null || requestedIds.isEmpty() || requestedIds.stream().anyMatch(id -> id == null)
                || new HashSet<>(requestedIds).size() != requestedIds.size()) {
            throw new IllegalArgumentException("Hãy chọn ít nhất một dịch vụ hợp lệ.");
        }
        Map<Long,Services> byId = services.findActiveByIds(requestedIds).stream()
                .collect(Collectors.toMap(Services::getServiceId, Function.identity()));
        int duration = 0;
        for (Long id : requestedIds) {
            Services service = byId.get(id);
            if (service == null) throw new IllegalArgumentException("Một dịch vụ đã ngừng hoạt động hoặc không tồn tại.");
            duration = Math.addExact(duration,service.getDurationMinutes());
        }
        return duration;
    }

    private static int validateDuration(Integer duration) {
        if (duration == null || duration <= 0 || duration > 1440) {
            throw new IllegalArgumentException("Thời lượng dịch vụ không hợp lệ.");
        }
        return duration;
    }

    private List<LocalTime> generateSlots(LocalDate date, int duration, List<Interval> windows, LocalDateTime earliest) {
        List<LocalTime> result = new ArrayList<>();
        if (date.isBefore(earliest.toLocalDate())) return result;
        for (Interval window : windows) {
            int openingMinute = window.start().toSecondOfDay() / 60;
            int windowStart = ((openingMinute + 29) / 30) * 30;
            int windowEnd = window.end().toSecondOfDay() / 60;
            for (int minute = windowStart; minute + duration <= windowEnd && minute + duration < 24 * 60; minute += 30) {
                LocalTime start = LocalTime.of(minute / 60,minute % 60);
                if (date.equals(earliest.toLocalDate()) && start.isBefore(earliest.toLocalTime())) continue;
                result.add(start);
            }
        }
        return result.stream().distinct().sorted().toList();
    }

    private boolean eligible(BookingAvailabilityRepository.StaffOption person, LocalDate date, LocalTime start, int duration,
            List<Interval> companyWindows, List<Interval> partTimeHours, Set<Long> onLeave, List<Interval> busy) {
        if(!eligibleForSchedule(person,date,start,duration,companyWindows,partTimeHours,onLeave))return false;
        int startMinute = start.toSecondOfDay() / 60;
        LocalTime end = LocalTime.of((startMinute + duration) / 60,(startMinute + duration) % 60);
        return busy.stream().noneMatch(interval -> person.id().equals(interval.staffId())
                && interval.start().isBefore(end) && interval.end().isAfter(start));
    }

    private boolean eligibleForSchedule(BookingAvailabilityRepository.StaffOption person,LocalDate date,LocalTime start,int duration,
            List<Interval> companyWindows,List<Interval> partTimeHours,Set<Long> onLeave) {
        int startMinute = start.toSecondOfDay() / 60;
        if (startMinute + duration >= 24 * 60 || onLeave.contains(person.id())) return false;
        LocalTime end = LocalTime.of((startMinute + duration) / 60,(startMinute + duration) % 60);
        boolean inCompany = companyWindows.stream().anyMatch(window -> !start.isBefore(window.start()) && !end.isAfter(window.end()));
        if (!inCompany) return false;
        if ("PART_TIME".equals(person.staffType()) && partTimeHours.stream().noneMatch(interval -> person.id().equals(interval.staffId())
                && !start.isBefore(interval.start()) && !end.isAfter(interval.end()))) return false;
        return true;
    }
}
