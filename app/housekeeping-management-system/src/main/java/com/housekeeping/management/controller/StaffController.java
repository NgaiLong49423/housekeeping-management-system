package com.housekeeping.management.controller;

import com.housekeeping.management.dto.request.PortalForms.AvailabilityForm;
import com.housekeeping.management.dto.response.PortalViews.StaffCard;
import com.housekeeping.management.service.BookingCompletionService;
import com.housekeeping.management.service.EvidenceStorageService;
import com.housekeeping.management.service.PortalQueryService;
import com.housekeeping.management.service.StaffAvailabilityService;
import com.housekeeping.management.service.StaffFollowService;
import com.housekeeping.management.web.PortalMessages;
import com.housekeeping.management.web.PortalUser;
import jakarta.validation.Valid;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class StaffController {
    private final PortalQueryService query;
    private final StaffAvailabilityService availability;
    private final StaffFollowService follows;
    private final BookingCompletionService completion;
    private final EvidenceStorageService storage;
    private final Path evidenceRoot;

    public StaffController(PortalQueryService query, StaffAvailabilityService availability, StaffFollowService follows,
            BookingCompletionService completion, EvidenceStorageService storage,
            @Value("${app.evidence.storage-root:./var/evidence}") String evidenceRoot) {
        this.query = query;
        this.availability = availability;
        this.follows = follows;
        this.completion = completion;
        this.storage = storage;
        this.evidenceRoot = Path.of(evidenceRoot).toAbsolutePath().normalize();
    }

    @GetMapping("/customer/staff")
    String staff(@AuthenticationPrincipal PortalUser user, Model model) {
        List<StaffCard> staff = query.staff(user);
        model.addAttribute("staff", staff);
        return "staff";
    }

    @PostMapping("/customer/staff/{id}/favorite")
    String favorite(@PathVariable Long id, @RequestParam boolean favorite, @AuthenticationPrincipal PortalUser user) {
        follows.setFollowing(id, user.getAccountId(), favorite);
        return "redirect:/customer/staff";
    }

    @GetMapping({"/customer/schedules", "/staff/schedules"})
    String schedules(@AuthenticationPrincipal PortalUser user, Model model) {
        model.addAttribute("schedules", query.schedules(user));
        if ("STAFF".equals(user.getRole())) {
            boolean partTime = query.isPartTime(user.getAccountId());
            model.addAttribute("partTime", partTime);
            if (partTime) model.addAttribute("availabilityForm", new AvailabilityForm(
                    LocalDate.now(ZoneOffset.ofHours(7)), LocalTime.of(8, 0), LocalTime.of(12, 0)));
        }
        return "schedules";
    }

    @PostMapping("/staff/schedules")
    String addAvailability(@AuthenticationPrincipal PortalUser user,
            @Valid @ModelAttribute AvailabilityForm form, BindingResult errors, RedirectAttributes flash) {
        if (errors.hasErrors()) {
            flash.addFlashAttribute("error", "Ngày và giờ sẵn sàng chưa hợp lệ.");
        } else {
            try {
                availability.save(user.getUserId(), null, form);
                flash.addFlashAttribute("success", "Đã đăng ký khoảng sẵn sàng.");
            } catch (IllegalArgumentException | IllegalStateException failure) {
                flash.addFlashAttribute("error", PortalMessages.business(failure));
            }
        }
        return "redirect:/staff/schedules";
    }

    @PostMapping("/staff/schedules/{id}/delete")
    String deleteAvailability(@PathVariable Long id, @AuthenticationPrincipal PortalUser user,
            RedirectAttributes flash) {
        try {
            availability.delete(user.getUserId(), id);
            flash.addFlashAttribute("success", "Đã xóa khoảng sẵn sàng.");
        } catch (IllegalArgumentException | IllegalStateException failure) {
            flash.addFlashAttribute("error", PortalMessages.business(failure));
        }
        return "redirect:/staff/schedules";
    }

    @PostMapping("/staff/bookings/{id}/evidence")
    String upload(@PathVariable Long id, @RequestParam MultipartFile image,
            @AuthenticationPrincipal PortalUser user, RedirectAttributes flash) {
        query.booking(id, user);
        try {
            storage.upload(id, user.getAccountId(), image);
            flash.addFlashAttribute("success", "Đã lưu ảnh minh chứng cho lịch hẹn.");
        } catch (IllegalArgumentException | IllegalStateException failure) {
            flash.addFlashAttribute("error", PortalMessages.business(failure));
        }
        return "redirect:/staff/bookings/" + id;
    }

    @PostMapping("/staff/bookings/{id}/complete")
    String complete(@PathVariable Long id, @AuthenticationPrincipal PortalUser user, RedirectAttributes flash) {
        query.booking(id, user);
        completion.completeBooking(id, user.getAccountId());
        flash.addFlashAttribute("success", "Công việc đã hoàn thành. Khách hàng có thể chọn phương thức thanh toán.");
        return "redirect:/staff/bookings/" + id;
    }

    @GetMapping("/evidence/{id}")
    ResponseEntity<Resource> evidence(@PathVariable Long id, @AuthenticationPrincipal PortalUser user) throws IOException {
        Path file = evidenceRoot.resolve(query.evidencePath(id, user)).normalize();
        if (!file.getParent().equals(evidenceRoot) || !Files.isRegularFile(file) || Files.isSymbolicLink(file)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        String mime = file.toString().endsWith(".png") ? MediaType.IMAGE_PNG_VALUE : MediaType.IMAGE_JPEG_VALUE;
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(mime)).cacheControl(CacheControl.noStore())
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline").body(new FileSystemResource(file));
    }
}
