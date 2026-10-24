package com.housekeeping.management.controller;

import com.housekeeping.management.dto.request.PortalForms.BookingForm;
import com.housekeeping.management.dto.request.PortalForms.FeedbackForm;
import com.housekeeping.management.dto.response.PortalViews.BookingCard;
import com.housekeeping.management.dto.response.PortalViews.DetailCard;
import com.housekeeping.management.service.BookingReservationService;
import com.housekeeping.management.service.FeedbackService;
import com.housekeeping.management.service.HouseService;
import com.housekeeping.management.service.BookingCancellationService;
import com.housekeeping.management.service.PortalQueryService;
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

@Controller
public class BookingController {
    private final PortalQueryService query;
    private final BookingCancellationService cancellation;
    private final BookingReservationService reservations;
    private final FeedbackService feedbackService;
    private final HouseService houses;

    public BookingController(PortalQueryService query, BookingCancellationService cancellation,
            BookingReservationService reservations, FeedbackService feedbackService, HouseService houses) {
        this.query = query;
        this.cancellation = cancellation;
        this.reservations = reservations;
        this.feedbackService = feedbackService;
        this.houses = houses;
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
        model.addAttribute("houses", houses.findCustomerHouses(user.getUserId()));
        if (!model.containsAttribute("bookingForm")) {
            model.addAttribute("bookingForm", new BookingForm(null, null, null, List.of(), ""));
        }
        // Busy intervals are only a display hint; the reservation service rechecks atomically on submit.
        model.addAttribute("busy", query.busyIntervals());
        return "booking-new";
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
