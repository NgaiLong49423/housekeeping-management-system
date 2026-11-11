package com.housekeeping.management.controller;

import com.housekeeping.management.dto.request.PortalForms.AdminBookingCancellationForm;
import com.housekeeping.management.dto.request.PortalForms.BookingReassignmentForm;
import com.housekeeping.management.dto.request.PortalForms.ArrivalVerificationForm;
import com.housekeeping.management.service.AdminBookingManagementService;
import com.housekeeping.management.web.PortalUser;
import com.housekeeping.management.web.PortalMessages;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/admin")
public class AdminBookingController {
    private final AdminBookingManagementService bookingManagement;

    public AdminBookingController(AdminBookingManagementService bookingManagement) {
        this.bookingManagement = bookingManagement;
    }

    @PostMapping("/bookings/{id}/cancel")
    String cancelBooking(@PathVariable Long id, @AuthenticationPrincipal PortalUser user,
            @Valid @ModelAttribute AdminBookingCancellationForm form, BindingResult errors, RedirectAttributes flash) {
        if (errors.hasErrors()) flash.addFlashAttribute("error", "Lý do hủy không được để trống và tối đa 500 ký tự.");
        else try {
            bookingManagement.cancelConfirmedBooking(id, user.getUserId(), form.reason());
            flash.addFlashAttribute("success", "Admin đã hủy lịch CONFIRMED; lịch sử và snapshot được giữ nguyên.");
        } catch (IllegalArgumentException | IllegalStateException failure) {
            flash.addFlashAttribute("error", failure.getMessage());
        }
        return "redirect:/admin/bookings/" + id;
    }


    @PostMapping("/bookings/{id}/reassign")
    String reassignBooking(@PathVariable Long id, @AuthenticationPrincipal PortalUser user,
            @Valid @ModelAttribute BookingReassignmentForm form, BindingResult errors, RedirectAttributes flash) {
        if (errors.hasErrors()) flash.addFlashAttribute("error", "Hãy chọn nhân viên hợp lệ.");
        else try {
            bookingManagement.reassignConfirmedBooking(id, user.getUserId(), form.staffId());
            flash.addFlashAttribute("success", "Đã cập nhật nhân viên phụ trách; nhân viên ban đầu được giữ nguyên trong lịch sử.");
        } catch (IllegalArgumentException | IllegalStateException failure) {
            flash.addFlashAttribute("error", failure.getMessage());
        }
        return "redirect:/admin/bookings/" + id;
    }

    @PostMapping("/bookings/{id}/arrival-dispute/verify")
    String verifyArrival(@PathVariable Long id, @AuthenticationPrincipal PortalUser user,
            @Valid @ModelAttribute ArrivalVerificationForm form, BindingResult errors, RedirectAttributes flash) {
        try {
            if (errors.hasErrors()) throw new IllegalArgumentException("Nhập giờ đến thực tế và ghi chú xác minh (tối đa 500 ký tự).");
            bookingManagement.verifyDisputedArrival(id, user.getUserId(), form.actualArrivalAt(), form.note());
            flash.addFlashAttribute("success", "Đã xác minh giờ đến, cập nhật mức giảm và mở khóa công việc.");
        } catch (IllegalArgumentException | IllegalStateException failure) {
            flash.addFlashAttribute("error", failure.getMessage());
        }
        return "redirect:/admin/bookings/" + id;
    }


}
