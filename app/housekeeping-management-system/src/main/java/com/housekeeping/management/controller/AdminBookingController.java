package com.housekeeping.management.controller;

import com.housekeeping.management.dto.request.PortalForms.AdminBookingCancellationForm;
import com.housekeeping.management.dto.request.PortalForms.BookingReassignmentForm;
import com.housekeeping.management.dto.request.PortalForms.ArrivalVerificationForm;
import com.housekeeping.management.dto.request.PortalForms.AdminTeamChangeForm;
import com.housekeeping.management.service.AdminBookingManagementService;
import com.housekeeping.management.service.BookingTeamChangeService;
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
    private final BookingTeamChangeService teamChanges;

    public AdminBookingController(AdminBookingManagementService bookingManagement, BookingTeamChangeService teamChanges) {
        this.bookingManagement = bookingManagement;
        this.teamChanges = teamChanges;
    }

    @PostMapping("/bookings/{id}/request-team-reduction")
    String requestTeamReduction(@PathVariable Long id, @AuthenticationPrincipal PortalUser user,
            @Valid @ModelAttribute AdminTeamChangeForm form, BindingResult errors, RedirectAttributes flash) {
        if (errors.hasErrors()) flash.addFlashAttribute("error", "Chọn thành viên và nhập lý do thiếu nhân viên (tối đa 1.000 ký tự).");
        else try {
            teamChanges.requestReduction(id, user.getUserId(), form.staffId(), form.reason());
            flash.addFlashAttribute("success", "Đã thông báo khách chọn tiếp tục với nhóm nhỏ hơn, dời lịch hoặc hủy miễn phí.");
        } catch (IllegalArgumentException | IllegalStateException failure) {
            flash.addFlashAttribute("error", PortalMessages.business(failure));
        }
        return "redirect:/admin/bookings/" + id;
    }

    @PostMapping("/bookings/{id}/cancel")
    String cancelBooking(@PathVariable Long id, @AuthenticationPrincipal PortalUser user,
            @Valid @ModelAttribute AdminBookingCancellationForm form, BindingResult errors, RedirectAttributes flash) {
        if (errors.hasErrors()) flash.addFlashAttribute("error", "Lý do hủy không được để trống và tối đa 500 ký tự.");
        else try {
            bookingManagement.cancelConfirmedBooking(id, user.getUserId(), form.reason());
            teamChanges.resolvePendingAsCancelled(id, user.getUserId());
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
            if (teamChanges.hasPendingRequest(id)) {
                throw new IllegalStateException("Booking đang chờ khách chọn cách xử lý nhóm. Hãy đợi lựa chọn trước khi đổi thành viên.");
            }
            bookingManagement.replaceAssignedStaff(id, user.getUserId(), form.currentStaffId(), form.staffId());
            flash.addFlashAttribute("success", "Đã cập nhật nhóm nhân viên; thành viên cũ được giữ trong lịch sử.");
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

    @PostMapping("/bookings/{id}/arrival-dispute/not-arrived")
    String verifyNotArrived(@PathVariable Long id, @AuthenticationPrincipal PortalUser user,
            @RequestParam String note, RedirectAttributes flash) {
        try {
            bookingManagement.verifyStaffNotArrived(id, user.getUserId(), note);
            flash.addFlashAttribute("success", "Đã xác minh nhân viên chưa tới; khách có thể chọn đợi hoặc hủy.");
        } catch (IllegalArgumentException | IllegalStateException failure) {
            flash.addFlashAttribute("error", PortalMessages.business(failure));
        }
        return "redirect:/admin/bookings/" + id;
    }


}
