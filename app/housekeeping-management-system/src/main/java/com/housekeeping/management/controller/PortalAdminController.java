package com.housekeeping.management.controller;

import static com.housekeeping.management.dto.request.PortalForms.*;
import com.housekeeping.management.dto.response.PortalViews;
import com.housekeeping.management.service.FeedbackService;
import com.housekeeping.management.service.PortalQueryService;
import com.housekeeping.management.service.AdminBookingManagementService;
import com.housekeeping.management.service.ServiceManagementService;
import com.housekeeping.management.service.StaffManagementService;
import com.housekeeping.management.web.PortalUser;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/admin")
public class PortalAdminController {
    private final PortalQueryService query;
    private final ServiceManagementService serviceManagement;
    private final StaffManagementService staffManagement;
    private final FeedbackService feedbackService;
    private final AdminBookingManagementService bookingManagement;
    public PortalAdminController(PortalQueryService query, ServiceManagementService serviceManagement,
            StaffManagementService staffManagement, FeedbackService feedbackService,
            AdminBookingManagementService bookingManagement) {
        this.query = query; this.serviceManagement = serviceManagement; this.staffManagement = staffManagement;
        this.feedbackService = feedbackService; this.bookingManagement = bookingManagement;
    }

    @GetMapping("/services")
    String services(Model model, @AuthenticationPrincipal PortalUser user) { model.addAttribute("services", query.services(user)); return "admin/services"; }

    @GetMapping("/staff")
    String staff(@AuthenticationPrincipal PortalUser user, Model model) { model.addAttribute("staff", query.staff(user)); return "admin/staff"; }

    @GetMapping("/schedules")
    String schedules(@AuthenticationPrincipal PortalUser user, Model model) {
        model.addAttribute("schedules", query.schedules(user)); return "admin/schedules";
    }

    @GetMapping("/feedback")
    String feedback(Model model) { model.addAttribute("feedback", query.feedback()); return "admin/feedback"; }

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

    @PostMapping("/feedback/{id}/reply")
    String reply(@PathVariable Long id, @AuthenticationPrincipal PortalUser user,
            @Valid @ModelAttribute FeedbackReplyForm form,
            BindingResult errors, RedirectAttributes flash) {
        if (!invalid(errors, flash)) {
            feedbackService.reply(id, user.getUserId(), form.reply());
            flash.addFlashAttribute("success", "Đã lưu phản hồi cho khách hàng.");
        }
        return "redirect:/admin/feedback";
    }

    @PostMapping("/feedback/{id}/resolve")
    String resolve(@PathVariable Long id, @AuthenticationPrincipal PortalUser user, RedirectAttributes flash) {
        feedbackService.resolve(id, user.getUserId());
        flash.addFlashAttribute("success", "Đã đánh dấu phản hồi là đã giải quyết.");
        return "redirect:/admin/feedback";
    }

    @PostMapping("/services")
    String createService(@AuthenticationPrincipal PortalUser user, @Valid @ModelAttribute ServiceForm form,
            BindingResult errors, RedirectAttributes flash) {
        if (!invalid(errors, flash)) { serviceManagement.save(user.getUserId(), null, form); flash.addFlashAttribute("success", "Đã thêm dịch vụ."); }
        return "redirect:/admin/services";
    }

    @PostMapping("/services/{id}")
    String updateService(@PathVariable Long id, @AuthenticationPrincipal PortalUser user,
            @Valid @ModelAttribute ServiceForm form, BindingResult errors, RedirectAttributes flash) {
        if (!invalid(errors, flash)) { serviceManagement.save(user.getUserId(), id, form); flash.addFlashAttribute("success", "Đã cập nhật dịch vụ."); }
        return "redirect:/admin/services";
    }

    @PostMapping("/services/{id}/delete")
    String deleteService(@PathVariable Long id, @AuthenticationPrincipal PortalUser user, RedirectAttributes flash) {
        serviceManagement.deactivate(user.getUserId(), id); flash.addFlashAttribute("success", "Đã ngừng cung cấp dịch vụ; lịch sử đặt dịch vụ được giữ nguyên."); return "redirect:/admin/services";
    }

    @PostMapping("/services/{id}/activate")
    String activateService(@PathVariable Long id, @AuthenticationPrincipal PortalUser user, RedirectAttributes flash) {
        serviceManagement.activate(user.getUserId(), id); flash.addFlashAttribute("success", "Đã kích hoạt lại dịch vụ."); return "redirect:/admin/services";
    }

    @PostMapping("/staff")
    String createStaff(@AuthenticationPrincipal PortalUser user, @Valid @ModelAttribute StaffCreateForm form,
            BindingResult errors, RedirectAttributes flash) {
        if (!invalid(errors, flash)) { staffManagement.create(user.getUserId(), form); flash.addFlashAttribute("success", "Đã tạo nhân viên và tài khoản đăng nhập."); }
        return "redirect:/admin/staff";
    }

    @PostMapping("/staff/{id}")
    String updateStaff(@PathVariable Long id, @AuthenticationPrincipal PortalUser user,
            @Valid @ModelAttribute StaffUpdateForm form, BindingResult errors, RedirectAttributes flash) {
        if (!invalid(errors, flash)) { staffManagement.update(user.getUserId(), id, form); flash.addFlashAttribute("success", "Đã cập nhật nhân viên."); }
        return "redirect:/admin/staff";
    }

    @PostMapping("/staff/{id}/delete")
    String deleteStaff(@PathVariable Long id, @AuthenticationPrincipal PortalUser user, RedirectAttributes flash) {
        staffManagement.deactivate(user.getUserId(), id); flash.addFlashAttribute("success", "Đã ngừng hoạt động tài khoản; dữ liệu lịch sử được giữ nguyên."); return "redirect:/admin/staff";
    }

    @PostMapping("/staff/{id}/activate")
    String activateStaff(@PathVariable Long id, @AuthenticationPrincipal PortalUser user, RedirectAttributes flash) {
        staffManagement.activate(user.getUserId(), id); flash.addFlashAttribute("success", "Đã kích hoạt lại tài khoản nhân viên."); return "redirect:/admin/staff";
    }

    private boolean invalid(BindingResult errors, RedirectAttributes flash) {
        if (!errors.hasErrors()) { return false; }
        flash.addFlashAttribute("error", "Dữ liệu chưa hợp lệ. Kiểm tra các trường bắt buộc, độ dài, thời lượng và giá tiền."); return true;
    }
}
