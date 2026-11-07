package com.housekeeping.management.controller;

import com.housekeeping.management.dto.request.PortalForms.BookingForm;
import com.housekeeping.management.dto.request.PortalForms.FeedbackForm;
import com.housekeeping.management.dto.response.PortalViews.BookingCard;
import com.housekeeping.management.dto.response.PortalViews.DetailCard;
import com.housekeeping.management.dto.response.BookingAvailabilityOptions;
import com.housekeeping.management.dto.response.BookingAvailabilityDates;
import com.housekeeping.management.entity.House;
import com.housekeeping.management.service.BookingReservationService;
import com.housekeeping.management.service.FeedbackService;
import com.housekeeping.management.service.HouseService;
import com.housekeeping.management.service.BookingCancellationService;
import com.housekeeping.management.service.BookingArrivalService;
import com.housekeeping.management.service.PortalQueryService;
import com.housekeeping.management.service.BookingAvailabilityService;
import com.housekeeping.management.web.PortalMessages;
import com.housekeeping.management.web.PortalUser;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;

@Controller
public class BookingController {
    private final PortalQueryService query;
    private final BookingCancellationService cancellation;
    private final BookingReservationService reservations;
    private final FeedbackService feedbackService;
    private final HouseService houses;
    private final BookingArrivalService arrival;
    private final BookingAvailabilityService bookingAvailability;

    public BookingController(PortalQueryService query, BookingCancellationService cancellation,
            BookingReservationService reservations, FeedbackService feedbackService, HouseService houses, BookingArrivalService arrival,
            BookingAvailabilityService bookingAvailability) {
        this.query = query;
        this.cancellation = cancellation;
        this.reservations = reservations;
        this.feedbackService = feedbackService;
        this.houses = houses;
        this.arrival = arrival;
        this.bookingAvailability = bookingAvailability;
    }

    @PostMapping("/customer/bookings/{id}/arrived")
    String confirmArrival(@PathVariable Long id, @AuthenticationPrincipal PortalUser user, RedirectAttributes flash) {
        try { arrival.confirm(id, user.getAccountId()); flash.addFlashAttribute("success", "Đã ghi nhận nhân viên đến địa điểm."); }
        catch (IllegalStateException failure) { flash.addFlashAttribute("error", failure.getMessage()); }
        return "redirect:/customer/bookings/" + id;
    }

    @GetMapping({"/customer/bookings", "/staff/bookings", "/admin/bookings"})
    String bookings(@AuthenticationPrincipal PortalUser user, Model model) {
        List<BookingCard> items = query.bookings(user);
        model.addAttribute("bookings", items);
        model.addAttribute("confirmedCount", items.stream().filter(b -> b.status().equals("CONFIRMED")).count());
        model.addAttribute("completedCount", items.stream().filter(b -> b.status().equals("COMPLETED")).count());
        model.addAttribute("unpaidCount", items.stream().filter(b -> "AWAITING_PAYMENT".equals(b.status())).count());
        return "bookings";
    }

    @GetMapping("/customer/bookings/new")
    String newBooking(@AuthenticationPrincipal PortalUser user, Model model) {
        model.addAttribute("services", query.services());
        model.addAttribute("staff", query.staff(user));
        java.time.LocalDate businessToday = java.time.LocalDate.now(java.time.ZoneId.of("Asia/Ho_Chi_Minh"));
        model.addAttribute("businessToday", businessToday);
        model.addAttribute("lastBookableDate", businessToday.plusDays(30));
        var customerHouses = houses.findCustomerHouses(user.getUserId());
        model.addAttribute("houses", customerHouses);
        if (!model.containsAttribute("bookingForm")) {
            Long defaultHouseId = customerHouses.stream().filter(House::isDefaultAddress)
                    .map(House::getHouseId).findFirst().orElse(null);
            model.addAttribute("bookingForm", new BookingForm(null, defaultHouseId, null, List.of(), ""));
        }
        // Busy intervals are only a display hint; the reservation service rechecks atomically on submit.
        model.addAttribute("busy", query.busyIntervals());
        return "booking-new";
    }

    @GetMapping("/customer/bookings/availability-options")
    @org.springframework.web.bind.annotation.ResponseBody
    BookingAvailabilityOptions availabilityOptions(@RequestParam LocalDate workDate,
            @RequestParam List<Long> serviceIds, @RequestParam(required = false) Long staffId,
            @RequestParam(required = false) String startTime, @AuthenticationPrincipal PortalUser user) {
        if (user == null || !"CUSTOMER".equals(user.getRole())) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        try {
            return bookingAvailability.options(workDate,serviceIds,staffId,
                    startTime == null || startTime.isBlank() ? null : LocalTime.parse(startTime));
        } catch (IllegalArgumentException | java.time.DateTimeException failure) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,failure.getMessage(),failure);
        }
    }

    @GetMapping("/customer/bookings/availability-dates")
    @org.springframework.web.bind.annotation.ResponseBody
    BookingAvailabilityDates availabilityDates(@RequestParam List<Long> serviceIds,
            @RequestParam(required = false) Long staffId, @AuthenticationPrincipal PortalUser user) {
        if (user == null || !"CUSTOMER".equals(user.getRole())) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        try {
            return bookingAvailability.availableDates(serviceIds,staffId);
        } catch (IllegalArgumentException failure) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,failure.getMessage(),failure);
        }
    }

    @PostMapping("/customer/bookings")
    String create(@AuthenticationPrincipal PortalUser user, @Valid @ModelAttribute BookingForm bookingForm,
            BindingResult errors, Model model) {
        if (errors.hasErrors()) return newBooking(user, model);
        try {
            Long id = reservations.createBooking(user.getUserId(), bookingForm.staffId(), bookingForm.houseId(),
                    bookingForm.startAt(), bookingForm.serviceIds(), bookingForm.areaNote());
            return "redirect:/customer/bookings/" + id;
        } catch (IllegalArgumentException | IllegalStateException failure) {
            model.addAttribute("error", PortalMessages.business(failure));
            return newBooking(user, model);
        }
    }

    @PostMapping("/customer/bookings/multiple")
    String createMultiple(@AuthenticationPrincipal PortalUser user,
            @RequestParam Long houseId, @RequestParam List<Long> serviceIds,
            @RequestParam List<LocalDate> workDates, @RequestParam List<Long> staffIds,
            @RequestParam List<LocalTime> startTimes, @RequestParam(required = false) String areaNote,
            RedirectAttributes flash) {
        if (workDates.isEmpty() || workDates.size() > 31 || workDates.size() != staffIds.size()
                || workDates.size() != startTimes.size() || workDates.stream().distinct().count() != workDates.size()) {
            flash.addFlashAttribute("error", "Danh sách ngày đặt chưa hợp lệ. Hãy tải lại biểu mẫu và chọn lại ngày, giờ, nhân viên.");
            return "redirect:/customer/bookings/new";
        }
        int created = 0;
        List<String> failures = new java.util.ArrayList<>();
        for (int index = 0; index < workDates.size(); index++) {
            LocalDate date = workDates.get(index);
            try {
                LocalDateTime startAt = date.atTime(startTimes.get(index));
                reservations.createBooking(user.getUserId(), staffIds.get(index), houseId,
                        startAt, serviceIds, areaNote);
                created++;
            } catch (IllegalArgumentException | IllegalStateException failure) {
                failures.add(date + ": " + PortalMessages.business(failure));
            }
        }
        if (created > 0) flash.addFlashAttribute("success", "Đã tạo " + created + " lịch hẹn riêng cho các ngày bạn chọn.");
        if (created > 0 && !failures.isEmpty()) flash.addFlashAttribute("warning", "Một số ngày chưa đặt được: " + String.join("; ", failures));
        if (created == 0) flash.addFlashAttribute("error", "Chưa tạo được lịch nào. " + String.join("; ", failures));
        return "redirect:/customer/bookings";
    }

    @GetMapping({"/customer/bookings/{id}", "/staff/bookings/{id}", "/admin/bookings/{id}"})
    String detail(@PathVariable Long id, @AuthenticationPrincipal PortalUser user, Model model) {
        BookingCard booking = query.booking(id, user);
        List<DetailCard> details = query.details(id, user);
        model.addAttribute("booking", booking);
        model.addAttribute("details", details);
        model.addAttribute("bookingImages", query.bookingImages(id, user));
        if ("ADMIN".equals(user.getRole())) model.addAttribute("assignableStaff", query.staff(null));
        if ("CUSTOMER".equals(user.getRole()) && booking.feedbackStatus() == null) {
            model.addAttribute("feedbackForm", new FeedbackForm(""));
        }
        return "booking-detail";
    }

    @PostMapping("/customer/bookings/{id}/cancel")
    String cancel(@PathVariable Long id, @AuthenticationPrincipal PortalUser user, RedirectAttributes flash) {
        cancellation.cancelByCustomer(id, user.getAccountId());
        flash.addFlashAttribute("success", "Đã hủy lịch hẹn và giải phóng thời gian.");
        return "redirect:/customer/bookings/" + id;
    }

    @PostMapping("/customer/bookings/{id}/feedback")
    String feedback(@PathVariable Long id, @AuthenticationPrincipal PortalUser user,
            @Valid @ModelAttribute FeedbackForm form, BindingResult errors, RedirectAttributes flash) {
        if (errors.hasErrors()) {
            flash.addFlashAttribute("error", "Nội dung phản hồi không được để trống.");
        } else {
            feedbackService.submit(id, user.getAccountId(), form.content());
            flash.addFlashAttribute("success", "Đã gửi phản hồi cho công ty.");
        }
        return "redirect:/customer/bookings/" + id;
    }
}
