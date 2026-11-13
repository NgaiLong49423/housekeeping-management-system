package com.housekeeping.management.controller;

import com.housekeeping.management.dto.request.PortalForms.BookingForm;
import com.housekeeping.management.dto.request.PortalForms.FeedbackForm;
import com.housekeeping.management.dto.request.PortalForms.FeedbackIncidentForm;
import com.housekeeping.management.dto.request.PortalForms.IncidentItemDetails;
import com.housekeeping.management.dto.request.PortalForms.CustomerCompensationResponseForm;
import com.housekeeping.management.dto.response.PortalViews.BookingCard;
import com.housekeeping.management.dto.response.PortalViews.DetailCard;
import com.housekeeping.management.dto.response.BookingAvailabilityOptions;
import com.housekeeping.management.dto.response.BookingAvailabilityDates;
import com.housekeeping.management.enums.FeedbackTopic;
import com.housekeeping.management.entity.House;
import com.housekeeping.management.service.BookingReservationService;
import com.housekeeping.management.service.CustomerFeedbackSubmissionService;
import com.housekeeping.management.service.FeedbackCaseService;
import com.housekeeping.management.service.CaseAttachmentStorageService;
import com.housekeeping.management.service.BookingFinancialService;
import com.housekeeping.management.service.HouseService;
import com.housekeeping.management.service.BookingCancellationService;
import com.housekeeping.management.service.BookingArrivalService;
import com.housekeeping.management.service.BookingIncidentService;
import com.housekeeping.management.service.CustomerDelayReportService;
import com.housekeeping.management.service.PortalQueryService;
import com.housekeeping.management.service.BookingAvailabilityService;
import com.housekeeping.management.service.LeaveManagementService;
import com.housekeeping.management.service.BookingTeamChangeService;
import com.housekeeping.management.web.PortalMessages;
import com.housekeeping.management.web.PortalUser;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class BookingController {
    private final PortalQueryService query;
    private final BookingCancellationService cancellation;
    private final BookingReservationService reservations;
    private final CustomerFeedbackSubmissionService feedbackSubmission;
    private final FeedbackCaseService feedbackCases;
    private final CaseAttachmentStorageService attachments;
    private final BookingFinancialService financials;
    private final HouseService houses;
    private final BookingArrivalService arrival;
    private final BookingAvailabilityService bookingAvailability;
    private final BookingIncidentService incidents;
    private final CustomerDelayReportService customerDelayReports;
    private final LeaveManagementService leaves;
    private final BookingTeamChangeService teamChanges;

    public BookingController(PortalQueryService query, BookingCancellationService cancellation,
            BookingReservationService reservations, CustomerFeedbackSubmissionService feedbackSubmission,
            FeedbackCaseService feedbackCases,
            CaseAttachmentStorageService attachments, BookingFinancialService financials, HouseService houses, BookingArrivalService arrival,
            BookingAvailabilityService bookingAvailability,
            BookingIncidentService incidents, CustomerDelayReportService customerDelayReports,
            LeaveManagementService leaves, BookingTeamChangeService teamChanges) {
        this.query = query;
        this.cancellation = cancellation;
        this.reservations = reservations;
        this.feedbackSubmission = feedbackSubmission;
        this.feedbackCases = feedbackCases;
        this.attachments = attachments;
        this.financials = financials;
        this.houses = houses;
        this.arrival = arrival;
        this.bookingAvailability = bookingAvailability;
        this.incidents = incidents;
        this.customerDelayReports = customerDelayReports;
        this.leaves = leaves;
        this.teamChanges = teamChanges;
    }

    @PostMapping("/customer/bookings/{id}/report-staff-delay")
    String reportStaffDelay(@PathVariable Long id, @AuthenticationPrincipal PortalUser user, RedirectAttributes flash) {
        try {
            customerDelayReports.reportStaffNotArrived(id, user.getAccountId());
            flash.addFlashAttribute("success", "Đã ghi nhận báo cáo Staff chưa đến và chuyển cho Admin theo dõi.");
        } catch (IllegalStateException failure) {
            flash.addFlashAttribute("error", PortalMessages.business(failure));
        }
        return "redirect:/customer/bookings/" + id;
    }

    @PostMapping("/customer/bookings/{id}/arrived")
    String confirmArrival(@PathVariable Long id, @AuthenticationPrincipal PortalUser user, RedirectAttributes flash) {
        try { arrival.confirm(id, user.getUserId()); flash.addFlashAttribute("success", "Đã ghi nhận nhân viên đến địa điểm."); }
        catch (IllegalStateException failure) { flash.addFlashAttribute("error", failure.getMessage()); }
        return "redirect:/customer/bookings/" + id;
    }

    @GetMapping({"/customer/bookings", "/staff/bookings", "/admin/bookings", "/manager/bookings"})
    String bookings(@AuthenticationPrincipal PortalUser user, Model model) {
        if (user == null) {
            return "redirect:/";
        }
        List<BookingCard> items = query.bookings(user);
        model.addAttribute("bookings", items);
        model.addAttribute("now", OffsetDateTime.now(java.time.ZoneOffset.ofHours(7)));
        if (user.isAdmin() || user.isManager()) {
            model.addAttribute("staffLeaveConflicts", leaves.findStaffLeaveConflicts());
        }
        if ("CUSTOMER".equals(user.getRole())) {
            model.addAttribute("pendingCompensationBookingIds",financials.customerCompensationOffers(user.getAccountId()).stream()
                    .filter(offer -> "PENDING_CUSTOMER".equals(offer.responseStatus()))
                    .map(com.housekeeping.management.dto.response.PortalViews.CustomerCompensationOfferCard::bookingId)
                    .collect(java.util.stream.Collectors.toSet()));
        }
        model.addAttribute("confirmedCount", items.stream().filter(b -> b.status().equals("CONFIRMED")).count());
        model.addAttribute("completedCount", items.stream().filter(b -> b.status().equals("COMPLETED")).count());
        model.addAttribute("unpaidCount", items.stream().filter(b -> "AWAITING_PAYMENT".equals(b.status())).count());
        return "bookings";
    }

    @GetMapping("/customer/bookings/new")
    String newBooking(@AuthenticationPrincipal PortalUser user, @RequestParam(required = false) Long houseId, Model model) {
        if (user == null) {
            return "redirect:/login";
        }
        model.addAttribute("services", query.services());
        model.addAttribute("staff", query.staff(user));
        java.time.LocalDate businessToday = java.time.LocalDate.now(java.time.ZoneId.of("Asia/Ho_Chi_Minh"));
        model.addAttribute("businessToday", businessToday);
        model.addAttribute("lastBookableDate", businessToday.plusDays(30));
        var customerHouses = houses.findCustomerHouses(user.getUserId());
        model.addAttribute("houses", customerHouses);
        if(!model.containsAttribute("bookingRetryState")) model.addAttribute("bookingRetryState",null);
        if (!model.containsAttribute("bookingForm")) {
            Long defaultHouseId = customerHouses.stream().filter(house -> houseId != null
                            ? house.getHouseId().equals(houseId) : house.isDefaultAddress())
                    .map(House::getHouseId).findFirst().orElse(null);
            model.addAttribute("bookingForm", new BookingForm(null, defaultHouseId, null, List.of(), ""));
        }
        // Busy intervals are only a display hint; the reservation service rechecks atomically on submit.
        model.addAttribute("busy", query.busyIntervals());
        return "booking-new-team";
    }

    @GetMapping("/customer/bookings/availability-options")
    @org.springframework.web.bind.annotation.ResponseBody
    org.springframework.http.ResponseEntity<?> availabilityOptions(@RequestParam LocalDate workDate,
            @RequestParam List<Long> serviceIds, @RequestParam(required = false) Long staffId,
            @RequestParam(required = false) List<Long> staffIds,
            @RequestParam(required = false) String startTime,@RequestParam Long houseId,
            @RequestParam(required = false) Long excludeBookingId,
            @RequestParam(required = false) Integer durationMinutes,
            @AuthenticationPrincipal PortalUser user) {
        if (user == null || !"CUSTOMER".equals(user.getRole())) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        try {
            List<Long> selectedStaff = staffIds == null ? (staffId == null ? List.of() : List.of(staffId)) : staffIds;
            if (excludeBookingId != null) {
                var info = teamChanges.rescheduleInfo(excludeBookingId, user.getUserId());
                if (durationMinutes == null || durationMinutes != info.durationMinutes())
                    throw new IllegalArgumentException("Thời lượng booking đã thay đổi. Hãy tải lại trang dời lịch.");
            }
            LocalTime selected = startTime == null || startTime.isBlank() ? null : LocalTime.parse(startTime);
            if (excludeBookingId == null) {
                return org.springframework.http.ResponseEntity.ok(bookingAvailability.optionsForStaffIds(
                        workDate, serviceIds, selectedStaff, selected, user.getUserId(), houseId));
            }
            return org.springframework.http.ResponseEntity.ok(bookingAvailability.optionsForStaffIds(workDate,serviceIds,selectedStaff,
                    selected,user.getUserId(),houseId,excludeBookingId,durationMinutes));
        } catch (IllegalArgumentException | IllegalStateException | java.time.DateTimeException failure) {
            return org.springframework.http.ResponseEntity.badRequest().body(
                    org.springframework.http.ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,failure.getMessage()));
        }
    }

    @GetMapping("/customer/bookings/availability-dates")
    @org.springframework.web.bind.annotation.ResponseBody
    org.springframework.http.ResponseEntity<?> availabilityDates(@RequestParam List<Long> serviceIds,
            @RequestParam(required = false) Long staffId, @RequestParam(required = false) List<Long> staffIds,
            @RequestParam Long houseId, @RequestParam(required = false) Long excludeBookingId,
            @RequestParam(required = false) Integer durationMinutes,
            @AuthenticationPrincipal PortalUser user) {
        if (user == null || !"CUSTOMER".equals(user.getRole())) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        try {
            List<Long> selectedStaff = staffIds == null ? (staffId == null ? List.of() : List.of(staffId)) : staffIds;
            if (excludeBookingId != null) {
                var info = teamChanges.rescheduleInfo(excludeBookingId, user.getUserId());
                if (durationMinutes == null || durationMinutes != info.durationMinutes())
                    throw new IllegalArgumentException("Thời lượng booking đã thay đổi. Hãy tải lại trang dời lịch.");
            }
            if (excludeBookingId == null) {
                return org.springframework.http.ResponseEntity.ok(bookingAvailability.availableDatesForStaffIds(
                        serviceIds, selectedStaff, user.getUserId(), houseId));
            }
            return org.springframework.http.ResponseEntity.ok(bookingAvailability.availableDatesForStaffIds(serviceIds,selectedStaff,
                    user.getUserId(),houseId,excludeBookingId,durationMinutes));
        } catch (IllegalArgumentException | IllegalStateException failure) {
            return org.springframework.http.ResponseEntity.badRequest().body(
                    org.springframework.http.ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,failure.getMessage()));
        }
    }

    @GetMapping("/customer/bookings/{id}/reschedule")
    String rescheduleForm(@PathVariable Long id, @AuthenticationPrincipal PortalUser user, Model model) {
        if (user == null || !"CUSTOMER".equals(user.getRole())) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        try {
            var info = teamChanges.rescheduleInfo(id, user.getUserId());
            newBooking(user, info.houseId(), model);
            model.addAttribute("rescheduleInfo", info);
            model.addAttribute("bookingForm", new BookingForm(info.staffIds(), info.leadStaffId(), info.houseId(),
                    info.startAt(), info.serviceIds(), info.areaNote()));
            return "booking-new-team";
        } catch (IllegalArgumentException | IllegalStateException failure) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, PortalMessages.business(failure));
        }
    }

    @PostMapping("/customer/bookings/{id}/reschedule")
    String reschedule(@PathVariable Long id, @AuthenticationPrincipal PortalUser user,
            @RequestParam List<LocalDate> workDates, @RequestParam List<String> staffIds,
            @RequestParam List<Long> leadStaffIds, @RequestParam List<LocalTime> startTimes,
            RedirectAttributes flash) {
        if (user == null || !"CUSTOMER".equals(user.getRole())) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        try {
            if (workDates.size() != 1 || staffIds.size() != 1 || leadStaffIds.size() != 1 || startTimes.size() != 1) {
                throw new IllegalArgumentException("Khi dời lịch chỉ chọn một ngày, một giờ và một nhóm cho booking hiện tại.");
            }
            List<Long> selectedStaff = java.util.Arrays.stream(staffIds.getFirst().split("\\|"))
                    .map(String::trim).filter(value -> !value.isEmpty()).map(Long::valueOf).toList();
            teamChanges.reschedule(id, user.getUserId(), selectedStaff, leadStaffIds.getFirst(),
                    workDates.getFirst().atTime(startTimes.getFirst()));
            flash.addFlashAttribute("success", "Đã dời lịch trên booking hiện tại. Mã booking và dịch vụ được giữ nguyên; nhóm và giá đã cập nhật.");
        } catch (IllegalArgumentException | IllegalStateException failure) {
            flash.addFlashAttribute("error", PortalMessages.business(failure));
            return "redirect:/customer/bookings/" + id + "/reschedule";
        }
        return "redirect:/customer/bookings/" + id;
    }

    @PostMapping("/customer/bookings/{id}/team-change/proceed")
    String proceedWithFewerStaff(@PathVariable Long id, @AuthenticationPrincipal PortalUser user,
            RedirectAttributes flash) {
        if (user == null || !"CUSTOMER".equals(user.getRole())) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        try {
            teamChanges.proceedWithFewerStaff(id, user.getUserId());
            flash.addFlashAttribute("success", "Booking tiếp tục với nhóm nhân viên hiện có; phụ phí đã được tính lại.");
        } catch (IllegalArgumentException | IllegalStateException failure) {
            flash.addFlashAttribute("error", PortalMessages.business(failure));
        }
        return "redirect:/customer/bookings/" + id;
    }

    @PostMapping("/customer/bookings/{id}/team-change/cancel")
    String cancelForTeamChange(@PathVariable Long id, @AuthenticationPrincipal PortalUser user,
            RedirectAttributes flash) {
        if (user == null || !"CUSTOMER".equals(user.getRole())) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        try {
            teamChanges.cancelForTeamChange(id, user.getUserId());
            flash.addFlashAttribute("success", "Booking đã được hủy miễn phí do không đủ nhân viên.");
        } catch (IllegalArgumentException | IllegalStateException failure) {
            flash.addFlashAttribute("error", PortalMessages.business(failure));
        }
        return "redirect:/customer/bookings/" + id;
    }

    @GetMapping("/customer/bookings/house-service-area")
    @org.springframework.web.bind.annotation.ResponseBody
    HouseAreaStatus houseServiceArea(@RequestParam Long houseId, @AuthenticationPrincipal PortalUser user) {
        if (user == null || !"CUSTOMER".equals(user.getRole())) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        try {
            var assessment = houses.assessCustomerHouseServiceArea(user.getUserId(), houseId);
            return new HouseAreaStatus(assessment.eligible(), assessment.message());
        } catch (IllegalArgumentException | IllegalStateException failure) {
            return new HouseAreaStatus(false, "Chưa kiểm tra được bán kính phục vụ. Vui lòng thử lại.");
        }
    }

    record HouseAreaStatus(boolean eligible, String message) { }

    @PostMapping("/customer/bookings")
    String create(@AuthenticationPrincipal PortalUser user, @Valid @ModelAttribute BookingForm bookingForm,
            BindingResult errors, Model model, RedirectAttributes flash) {
        if (errors.hasErrors()) return newBooking(user, bookingForm.houseId(), model);
        try {
            Long id = reservations.createBooking(user.getUserId(), bookingForm.staffIds(), bookingForm.leadStaffId(), bookingForm.houseId(),
                    bookingForm.startAt(), bookingForm.serviceIds(), bookingForm.areaNote());
            flash.addFlashAttribute("success", "Đã đặt lịch thành công. Khi nhân viên đến, hãy xác nhận trên trang chi tiết để tiện theo dõi.");
            return "redirect:/customer/bookings/" + id;
        } catch (IllegalArgumentException | IllegalStateException failure) {
            model.addAttribute("error", PortalMessages.business(failure));
            return newBooking(user, bookingForm.houseId(), model);
        }
    }

    @PostMapping("/customer/bookings/multiple")
    String createMultiple(@AuthenticationPrincipal PortalUser user,
            @RequestParam Long houseId, @RequestParam List<Long> serviceIds,
            @RequestParam List<LocalDate> workDates, @RequestParam List<String> staffIds,
            @RequestParam List<Long> leadStaffIds,
            @RequestParam List<LocalTime> startTimes, @RequestParam(required = false) String areaNote,
            RedirectAttributes flash) {
        if (workDates.isEmpty() || workDates.size() > 31 || staffIds.stream().anyMatch(java.util.Objects::isNull)
                || workDates.size() != staffIds.size() || workDates.size() != leadStaffIds.size()
                || workDates.size() != startTimes.size() || workDates.stream().distinct().count() != workDates.size()) {
            flash.addFlashAttribute("error", "Danh sách ngày đặt chưa hợp lệ. Hãy tải lại biểu mẫu và chọn lại ngày, giờ, nhân viên.");
            return "redirect:/customer/bookings/new";
        }
        int created = 0;
        List<String> failures = new java.util.ArrayList<>();
        List<java.util.Map<String,Object>> retryDays=new java.util.ArrayList<>();
        for (int index = 0; index < workDates.size(); index++) {
            LocalDate date = workDates.get(index);
            try {
                LocalDateTime startAt = date.atTime(startTimes.get(index));
                List<Long> selectedStaff = java.util.Arrays.stream(staffIds.get(index).split("\\|"))
                        .map(String::trim).filter(value -> !value.isEmpty()).map(Long::valueOf).toList();
                reservations.createBooking(user.getUserId(), selectedStaff, leadStaffIds.get(index), houseId,
                        startAt, serviceIds, areaNote);
                created++;
            } catch (IllegalArgumentException | IllegalStateException failure) {
                String message = PortalMessages.business(failure);
                failures.add(date + ": " + message);
                retryDays.add(java.util.Map.of("date",date.toString(),"staffIds",staffIds.get(index),"leadStaffId",leadStaffIds.get(index),"time",startTimes.get(index).toString()));
            }
        }
        if (!failures.isEmpty()) {
            flash.addFlashAttribute("bookingRetryState",java.util.Map.of("houseId",houseId,"serviceIds",serviceIds,
                    "areaNote",areaNote==null?"":areaNote,"days",retryDays));
            if (created > 0) flash.addFlashAttribute("warning", "Đã tạo " + created + " lịch. Khi nhân viên đến, hãy xác nhận trên từng lịch để tiện theo dõi. Các ngày còn lại chưa đặt được: " + String.join("; ",failures));
            else flash.addFlashAttribute("error", "Chưa tạo được lịch nào. " + String.join("; ",failures));
            return "redirect:/customer/bookings/new?houseId=" + houseId;
        }
        if (created > 0) flash.addFlashAttribute("success", "Đã tạo " + created + " lịch hẹn riêng cho các ngày bạn chọn. Khi nhân viên đến, hãy xác nhận trên từng lịch để tiện theo dõi.");
        return "redirect:/customer/bookings";
    }

    @GetMapping({"/customer/bookings/{id}", "/staff/bookings/{id}", "/admin/bookings/{id}", "/manager/bookings/{id}"})
    String detail(@PathVariable Long id, @AuthenticationPrincipal PortalUser user, Model model) {
        BookingCard booking = query.booking(id, user);
        List<DetailCard> details = query.details(id, user);
        List<com.housekeeping.management.dto.response.PortalViews.BookingTeamMemberCard> team = query.bookingTeam(id, user);
        boolean pendingTeamChangeExists = teamChanges.hasPendingRequest(id);
        OffsetDateTime now = OffsetDateTime.now(java.time.ZoneOffset.ofHours(7));
        model.addAttribute("booking", booking);
        model.addAttribute("bookingTeam", team);
        model.addAttribute("pendingTeamChangeExists", pendingTeamChangeExists);
        model.addAttribute("bookingHasUnarrivedStaff", team.stream().anyMatch(member -> member.arrivedAt() == null));
        boolean teamAllArrived = !team.isEmpty() && team.stream().allMatch(member -> member.arrivedAt() != null);
        boolean staffIsAssigned = team.stream().anyMatch(member -> member.staffId().equals(user.getAccountId()));
        boolean currentStaffArrived = team.stream().anyMatch(member -> member.staffId().equals(user.getAccountId()) && member.arrivedAt() != null);
        boolean staffIsLead = booking.staffId().equals(user.getAccountId());
        model.addAttribute("teamAllArrived", teamAllArrived);
        model.addAttribute("staffIsAssigned", staffIsAssigned);
        model.addAttribute("currentStaffArrived", currentStaffArrived);
        model.addAttribute("staffIsLead", staffIsLead);
        model.addAttribute("now", now);
        if (user.isAdmin()) model.addAttribute("customerCancellationHistory", query.customerCancellationHistory(id, user));
        boolean customerVerifiedArrival = booking.customerArrivedAt() != null || booking.arrivalVerifiedAt() != null;
        boolean customerDelayReportOpen = booking.customerDelayReportedAt() != null && !customerVerifiedArrival;
        boolean acceptedEta = "ACCEPTED".equals(booking.incidentCustomerResponse())
                && booking.incidentExpectedArrivalAt() != null
                && booking.staffArrivedAt() != null
                && !booking.staffArrivedAt().isAfter(booking.incidentExpectedArrivalAt());
        boolean lateArrival = booking.staffArrivedAt() != null && booking.staffArrivedAt().isAfter(booking.start());
        boolean startAllowed = !customerDelayReportOpen && (customerVerifiedArrival || acceptedEta
                || (booking.incidentReportedAt() == null && !lateArrival));
        model.addAttribute("staffCanStartWork", "CONFIRMED".equals(booking.status()) && staffIsLead && teamAllArrived
                && booking.staffArrivedAt() != null && booking.workStartedAt() == null && startAllowed && !pendingTeamChangeExists);
        model.addAttribute("staffStartBlockMessage", pendingTeamChangeExists
                ? "Booking đang chờ khách chọn cách xử lý khi thiếu nhân viên."
                : customerDelayReportOpen
                ? (booking.adminNoShowVerifiedAt() != null && booking.customerWaitedAt() != null
                    ? "Khách đã chọn đợi sau khi Admin xác minh nhân viên chưa tới. Admin cần xác nhận giờ đến thực tế trước khi bắt đầu."
                    : "Khách đã báo giờ đến chưa đúng. Cần khách xác nhận nhân viên đã có mặt hoặc Admin xác minh trước khi bắt đầu.")
                : booking.incidentReportedAt() != null && !customerVerifiedArrival && !acceptedEta
                    ? "Cần khách đồng ý giờ đến trễ hoặc xác nhận nhân viên đã có mặt trước khi bắt đầu."
                    : lateArrival
                        && booking.incidentReportedAt() == null && !customerVerifiedArrival
                            ? "Nhân viên đến trễ cần gửi giờ dự kiến và chờ khách đồng ý, hoặc khách xác nhận nhân viên đã có mặt."
                            : null);
        model.addAttribute("details", details);
        model.addAttribute("bookingImages", query.bookingImages(id, user));
        if (user.isAdmin() || user.isManager()) {
            String staffLeaveConflict = leaves.findStaffLeaveConflicts().get(id);
            if (staffLeaveConflict != null) {
                model.addAttribute("staffLeaveConflict", staffLeaveConflict);
            }
        }
        if (user.isAdmin() || "CUSTOMER".equals(user.getRole())) model.addAttribute("assignableStaff", query.staff(user));
        if ("CUSTOMER".equals(user.getRole())) {
            model.addAttribute("pendingTeamChange", teamChanges.pendingRequest(id, user.getUserId())
                    .map(request -> new com.housekeeping.management.dto.response.PortalViews.BookingTeamChangeRequestCard(
                            request.requestId(), request.affectedStaffId(), request.proposedStaffCount(), request.reason(), request.createdAt()))
                    .orElse(null));
        }
        if ("CUSTOMER".equals(user.getRole()) && (booking.feedbackStatus() == null
                || ("OPEN".equals(booking.feedbackStatus()) && booking.feedbackRating() != null))) {
            model.addAttribute("feedbackForm", booking.feedbackStatus() == null
                    ? new FeedbackForm(null, List.of(), "")
                    : new FeedbackForm(booking.feedbackRating(), booking.feedbackTopicCodes(), booking.feedbackContent()));
        }
        if ("CUSTOMER".equals(user.getRole()) && booking.feedbackStatus() != null) {
            var incidentCase=feedbackCases.customerCase(id, user.getAccountId());
            model.addAttribute("feedbackCase", incidentCase);
            model.addAttribute("feedbackEvidenceIds", incidentCase==null ? List.of() : attachments.customerEvidenceIds(incidentCase.id(),user.getAccountId()));
            model.addAttribute("feedbackCaseMessages", incidentCase==null ? List.of() : feedbackCases.customerMessages(incidentCase.id(),user.getAccountId()));
            model.addAttribute("incidentForm", new FeedbackIncidentForm(List.of(), ""));
        }
        if ("CUSTOMER".equals(user.getRole())) {
            model.addAttribute("customerFinancialEntries",financials.customerVisible(id,user.getAccountId()));
            var offers=financials.customerCompensationOffersForBooking(id,user.getAccountId());
            model.addAttribute("customerCompensationOffers",offers);
            model.addAttribute("hasActiveCompensationOffer",offers.stream().anyMatch(offer ->
                    List.of("PROPOSED","PENDING_APPROVAL","APPROVED","PAID").contains(offer.entryStatus())));
        }
        model.addAttribute("feedbackTopics", FeedbackTopic.values());
        return "booking-detail";
    }

    @PostMapping("/customer/bookings/{id}/compensation-offers/{entryId}/response")
    String respondToCompensationOffer(@PathVariable Long id, @PathVariable Long entryId,
            @Valid @ModelAttribute CustomerCompensationResponseForm form, BindingResult errors,
            @AuthenticationPrincipal PortalUser user, RedirectAttributes flash) {
        try {
            if (errors.hasErrors()) throw new IllegalArgumentException("Kiểm tra lại lựa chọn và thông tin nhận tiền.");
            financials.respondToCompensationOffer(id,entryId,user.getAccountId(),form);
            flash.addFlashAttribute("success", "Đã gửi lựa chọn của bạn cho công ty.");
        } catch (IllegalArgumentException | IllegalStateException failure) {
            flash.addFlashAttribute("error", PortalMessages.business(failure));
        }
        return "redirect:/customer/bookings/" + id;
    }

    @PostMapping("/customer/bookings/{id}/compensation-offers/{entryId}/received")
    String confirmCompensationReceived(@PathVariable Long id, @PathVariable Long entryId,
            @AuthenticationPrincipal PortalUser user, RedirectAttributes flash) {
        try {
            financials.confirmCompensationReceived(id,entryId,user.getAccountId());
            flash.addFlashAttribute("success","Đã xác nhận nhận khoản bồi thường; hồ sơ đã hoàn tất.");
        } catch (IllegalArgumentException | IllegalStateException failure) {
            flash.addFlashAttribute("error",PortalMessages.business(failure));
        }
        return "redirect:/customer/bookings/" + id;
    }

    @PostMapping("/customer/bookings/{id}/feedback/cases/{caseId}/outcome")
    String respondToIncidentOutcome(@PathVariable Long id, @PathVariable Long caseId,
            @RequestParam String decision, @RequestParam(required=false) String message,
            @AuthenticationPrincipal PortalUser user, RedirectAttributes flash) {
        try {
            if ("CONFIRM".equals(decision)) feedbackCases.resolveAfterCustomerConfirmation(caseId,user.getAccountId());
            else if ("NEEDS_HELP".equals(decision)) feedbackCases.customerNeedsMoreHelp(caseId,id,user.getAccountId(),message);
            else throw new IllegalArgumentException("Lựa chọn phản hồi không hợp lệ.");
            flash.addFlashAttribute("success","Đã lưu phản hồi cho hồ sơ sự cố.");
        } catch (IllegalArgumentException | IllegalStateException failure) {
            flash.addFlashAttribute("error",PortalMessages.business(failure));
        }
        return "redirect:/customer/bookings/" + id;
    }

    @PostMapping("/customer/bookings/{id}/incident-response")
    String respondToIncident(@PathVariable Long id, @RequestParam String response,
            @AuthenticationPrincipal PortalUser user, RedirectAttributes flash) {
        try {
            incidents.respond(id, user.getAccountId(), response);
            flash.addFlashAttribute("success", "DECLINED".equals(response)
                    ? "Đã hủy miễn phí lịch do Staff đến trễ. Admin có thể xem lại sự cố."
                    : "Đã ghi nhận phản hồi. Nếu ETA đã lỡ, bạn vẫn có thể hủy miễn phí trước khi bắt đầu; để tiếp tục, hãy xác nhận giờ đến thực tế hoặc báo Admin xác minh.");
        } catch (IllegalArgumentException | IllegalStateException failure) {
            flash.addFlashAttribute("error", PortalMessages.business(failure));
        }
        return "redirect:/customer/bookings/" + id;
    }

    @PostMapping("/customer/bookings/{id}/arrival-dispute-response")
    String respondToArrivalDispute(@PathVariable Long id, @RequestParam String response,
            @AuthenticationPrincipal PortalUser user, RedirectAttributes flash) {
        try {
            customerDelayReports.respondAfterAdminReview(id, user.getAccountId(), response);
            flash.addFlashAttribute("success", "WAIT".equals(response)
                    ? "Đã ghi nhận bạn chọn đợi thêm. Mức giảm do trễ sẽ theo giờ đến Admin xác minh."
                    : "Đã hủy miễn phí lịch vì nhân viên đến trễ.");
        } catch (IllegalArgumentException | IllegalStateException failure) {
            flash.addFlashAttribute("error", PortalMessages.business(failure));
        }
        return "redirect:/customer/bookings/" + id;
    }

    @PostMapping("/customer/bookings/{id}/cancel")
    String cancel(@PathVariable Long id, @AuthenticationPrincipal PortalUser user, RedirectAttributes flash) {
        try {
            cancellation.cancelByCustomer(id, user.getAccountId());
            teamChanges.resolvePendingAsCancelled(id, user.getUserId());
            flash.addFlashAttribute("success", "Đã hủy lịch miễn phí và giải phóng thời gian.");
        } catch (IllegalArgumentException | IllegalStateException failure) {
            flash.addFlashAttribute("error", failure.getMessage());
        } catch (org.springframework.security.access.AccessDeniedException failure) {
            flash.addFlashAttribute("error", "Bạn không có quyền thao tác lịch hẹn này.");
        }
        return "redirect:/customer/bookings/" + id;
    }

    @PostMapping("/customer/bookings/{id}/feedback")
    String feedback(@PathVariable Long id, @AuthenticationPrincipal PortalUser user,
            @Valid @ModelAttribute FeedbackForm form, BindingResult errors,
            @RequestParam(required=false) List<org.springframework.web.multipart.MultipartFile> images,
            RedirectAttributes flash) {
        return persistFeedback(id, user, form, errors, images, flash, false);
    }

    @PostMapping("/customer/bookings/{id}/feedback/edit")
    String editFeedback(@PathVariable Long id, @AuthenticationPrincipal PortalUser user,
            @Valid @ModelAttribute FeedbackForm form, BindingResult errors,
            @RequestParam(required=false) List<org.springframework.web.multipart.MultipartFile> images,
            RedirectAttributes flash) {
        return persistFeedback(id, user, form, errors, images, flash, true);
    }

    private String persistFeedback(Long id, PortalUser user, FeedbackForm form, BindingResult errors,
            List<org.springframework.web.multipart.MultipartFile> images, RedirectAttributes flash, boolean edit) {
        if (errors.hasErrors()) {
            flash.addFlashAttribute("error", "Hãy chọn điểm đánh giá hợp lệ và kiểm tra nội dung lời nhắn.");
        } else try {
            feedbackSubmission.submit(id, user.getAccountId(), form, edit, images);
            flash.addFlashAttribute("success", Boolean.TRUE.equals(form.reportIncident())
                    ? "Đã gửi đánh giá và hồ sơ sự cố kèm bằng chứng cho công ty."
                    : "Đã lưu phản hồi cho công ty.");
        } catch (IllegalArgumentException | IllegalStateException failure) {
            flash.addFlashAttribute("error", PortalMessages.business(failure));
        }
        return "redirect:/customer/bookings/" + id;
    }

    @PostMapping("/customer/bookings/{id}/feedback/incident")
    String openFeedbackIncident(@PathVariable Long id, @Valid @ModelAttribute FeedbackIncidentForm form,
            BindingResult errors, @RequestParam(required=false) List<org.springframework.web.multipart.MultipartFile> images,
            @AuthenticationPrincipal PortalUser user, RedirectAttributes flash) {
        try {
            if (errors.hasErrors()) throw new IllegalArgumentException("Hãy chọn loại sự cố và mô tả nội dung tối đa 4.000 ký tự.");
            if(images!=null && images.stream().filter(file->file!=null&&!file.isEmpty()).count()>5)
                throw new IllegalArgumentException("Mỗi lần gửi tối đa 5 ảnh bằng chứng.");
            boolean hasFiles=images!=null && images.stream().anyMatch(file->file!=null&&!file.isEmpty());
            boolean hasDriveLink=form.driveLink()!=null&&!form.driveLink().isBlank();
            if(!hasFiles&&!hasDriveLink) throw new IllegalArgumentException("Cần gửi ít nhất một ảnh, tệp phù hợp hoặc link video làm bằng chứng.");
            if((images==null?0:images.stream().filter(file->file!=null&&!file.isEmpty()).count())+(hasDriveLink?1:0)>5)
                throw new IllegalArgumentException("Tệp và link bằng chứng cộng lại tối đa 5 mục.");
            Long caseId=feedbackCases.open(id, user.getAccountId(), form.incidentTypes(), form.description(),
                    new IncidentItemDetails(form.itemDescription(),form.estimatedItemValue(),form.itemAgeMonths()),
                    form.customerRequestedResolution(),hasFiles||hasDriveLink);
            if(images!=null) for(var image:images) if(image!=null&&!image.isEmpty()) attachments.uploadCustomerEvidence(caseId,id,user.getAccountId(),image);
            if(form.driveLink()!=null && !form.driveLink().isBlank()) attachments.addCustomerDriveEvidence(caseId,id,user.getAccountId(),feedbackCases.initialCustomerMessageId(caseId),form.driveLink());
            flash.addFlashAttribute("success", "Đã gửi khiếu nại sự cố. Công ty sẽ xác minh và cập nhật kết quả trên lịch này.");
        } catch (IllegalArgumentException | IllegalStateException failure) {
            flash.addFlashAttribute("error", PortalMessages.business(failure));
        }
        return "redirect:/customer/bookings/" + id;
    }

    @PostMapping("/customer/bookings/{id}/feedback/incident/{caseId}/messages")
    String sendFeedbackCaseMessage(@PathVariable Long id, @PathVariable Long caseId,
            @RequestParam(required=false) String message,
            @RequestParam(required=false) String driveLink,
            @RequestParam(required=false) List<org.springframework.web.multipart.MultipartFile> images,
            @RequestParam(required=false) org.springframework.web.multipart.MultipartFile image,
            @AuthenticationPrincipal PortalUser user, RedirectAttributes flash) {
        Long messageId=null;
        try {
            query.booking(id,user);
            List<org.springframework.web.multipart.MultipartFile> files=new java.util.ArrayList<>();
            if(images!=null) files.addAll(images.stream().filter(file->file!=null&&!file.isEmpty()).toList());
            if(image!=null&&!image.isEmpty()) files.add(image);
            if(files.size()+(driveLink!=null&&!driveLink.isBlank()?1:0)>5) throw new IllegalArgumentException("Mỗi tin nhắn tối đa 5 tệp/liên kết bằng chứng.");
            messageId=feedbackCases.addCustomerMessage(caseId,id,user.getAccountId(),message,!files.isEmpty() || (driveLink!=null&&!driveLink.isBlank()));
            if(!files.isEmpty()) attachments.uploadCustomerMessageEvidence(caseId,id,user.getAccountId(),messageId,files);
            if(driveLink!=null&&!driveLink.isBlank()) attachments.addCustomerDriveEvidence(caseId,id,user.getAccountId(),messageId,driveLink);
            flash.addFlashAttribute("success","Đã gửi tin nhắn vào hồ sơ sự cố.");
        } catch (IllegalArgumentException | IllegalStateException failure) {
            if(messageId!=null) feedbackCases.discardUnsentMessage(caseId,messageId,user.getAccountId(),false);
            flash.addFlashAttribute("error",PortalMessages.business(failure));
        }
        return "redirect:/customer/bookings/" + id;
    }
}
