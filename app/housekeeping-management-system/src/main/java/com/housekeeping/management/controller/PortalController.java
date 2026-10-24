package com.housekeeping.management.controller;

import static com.housekeeping.management.dto.request.PortalForms.*;
import static com.housekeeping.management.dto.response.PortalViews.*;
import com.housekeeping.management.dto.response.PortalViews;
import com.housekeeping.management.service.PortalActionsService;
import com.housekeeping.management.service.PortalQueryService;
import com.housekeeping.management.web.PortalUser;
import com.housekeeping.management.web.PortalMessages;
import com.housekeeping.management.domain.PaymentMethod;
import com.housekeeping.management.service.PayosPaymentService;
import com.housekeeping.management.service.PayosWebhookRequest;
import com.housekeeping.management.service.*;
import jakarta.validation.Valid;
import java.nio.charset.StandardCharsets;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class PortalController {
    private final PortalQueryService query;
    private final PortalActionsService actions;
    private final BookingReservationService reservations;
    private final BookingCompletionService completion;
    private final PaymentLifecycleService payments;
    private final PayosPaymentService payosPayments;
    private final EvidenceStorageService storage;
    private final HouseService houses;
    private final String mapsBrowserKey;
    private final Path evidenceRoot;

    public PortalController(PortalQueryService query, PortalActionsService actions, BookingReservationService reservations,
            BookingCompletionService completion, PaymentLifecycleService payments, PayosPaymentService payosPayments, EvidenceStorageService storage,
            HouseService houses, @Value("${app.evidence.storage-root:./var/evidence}") String evidenceRoot,
            @Value("${app.maps.browser-key:}") String mapsBrowserKey) {
        this.query = query; this.actions = actions; this.reservations = reservations; this.completion = completion;
        this.payments = payments; this.payosPayments = payosPayments; this.storage = storage; this.houses = houses;
        this.evidenceRoot = Path.of(evidenceRoot).toAbsolutePath().normalize();
        this.mapsBrowserKey = mapsBrowserKey;
    }

    @GetMapping("/")
    String home(Model model) { model.addAttribute("services", query.services()); return "home"; }

    @GetMapping("/services")
    String services(Model model) { model.addAttribute("services", query.services()); return "services"; }

    @GetMapping("/login")
    String login() { return "login"; }

    @GetMapping("/register")
    String registration(Model model) {
        if (!model.containsAttribute("registrationForm")) {
            model.addAttribute("registrationForm", new CustomerRegistrationForm("", "", "", "", ""));
        }
        return "register";
    }

    @PostMapping("/register")
    String register(@Valid @ModelAttribute("registrationForm") CustomerRegistrationForm form,
            BindingResult errors) {
        if (!Objects.equals(form.password(), form.confirmPassword())) {
            errors.rejectValue("confirmPassword", "mismatch", "Mật khẩu nhập lại chưa khớp.");
        }
        if (form.password() != null && form.password().getBytes(StandardCharsets.UTF_8).length > 72) {
            errors.rejectValue("password", "bcryptLength", "Mật khẩu không được vượt quá 72 byte UTF-8.");
        }
        if (errors.hasErrors()) { return "register"; }
        try {
            actions.registerCustomer(form);
            return "redirect:/login?registered";
        } catch (DuplicateEmailException duplicateEmail) {
            errors.rejectValue("email", "duplicate", "Email này đã được đăng ký. Hãy đăng nhập hoặc dùng email khác.");
            return "register";
        }
    }

    @GetMapping("/portal")
    String portal(@AuthenticationPrincipal PortalUser user) {
        return "redirect:" + switch (user.getRole()) { case "ADMIN" -> "/admin/bookings"; case "STAFF" -> "/staff/bookings"; default -> "/customer/bookings"; };
    }

    @GetMapping({"/customer/bookings", "/staff/bookings", "/admin/bookings"})
    String bookings(@AuthenticationPrincipal PortalUser user, Model model) {
        List<BookingCard> items = query.bookings(user);
        model.addAttribute("bookings", items);
        model.addAttribute("confirmedCount", items.stream().filter(b -> b.status().equals("CONFIRMED")).count());
        model.addAttribute("completedCount", items.stream().filter(b -> b.status().equals("COMPLETED")).count());
        model.addAttribute("unpaidCount", items.stream().filter(b -> "UNPAID".equals(b.paymentStatus())).count());
        return "bookings";
    }

    @GetMapping("/customer/staff")
    String staff(@AuthenticationPrincipal PortalUser user, Model model) {
        model.addAttribute("staff", query.staff(user)); return "staff";
    }

    @GetMapping("/customer/houses")
    String houses(@AuthenticationPrincipal PortalUser user, @RequestParam(required = false) Long editId, Model model) {
        model.addAttribute("houses", houses.findCustomerHouses(user.getUserId()));
        model.addAttribute("mapsBrowserKey", mapsBrowserKey);
        if (!model.containsAttribute("houseForm")) {
            if (editId == null) {
                model.addAttribute("houseForm", new HouseForm("", "", null, null, ""));
            } else {
                var house = houses.findCustomerHouse(user.getUserId(), editId);
                model.addAttribute("houseForm", new HouseForm(house.getHouseName(), house.getAddress(),
                        house.getLatitude(), house.getLongitude(), house.getAddressNote()));
                model.addAttribute("editHouseId", editId);
            }
        } else if (editId != null) {
            model.addAttribute("editHouseId", editId);
        }
        return "customer/houses";
    }

    @PostMapping("/customer/houses")
    String createHouse(@AuthenticationPrincipal PortalUser user, @Valid @ModelAttribute("houseForm") HouseForm form,
            BindingResult errors, Model model) {
        if (errors.hasErrors()) { return houses(user, null, model); }
        try {
            houses.save(user.getUserId(), null, form);
            return "redirect:/customer/houses?saved";
        } catch (IllegalArgumentException | IllegalStateException failure) {
            model.addAttribute("error", failure.getMessage());
            return houses(user, null, model);
        }
    }

    @PostMapping("/customer/houses/{id}")
    String updateHouse(@PathVariable Long id, @AuthenticationPrincipal PortalUser user,
            @Valid @ModelAttribute("houseForm") HouseForm form, BindingResult errors, Model model) {
        if (errors.hasErrors()) { return houses(user, id, model); }
        try {
            houses.save(user.getUserId(), id, form);
            return "redirect:/customer/houses?saved";
        } catch (IllegalArgumentException | IllegalStateException failure) {
            model.addAttribute("error", failure.getMessage());
            return houses(user, id, model);
        }
    }

    @PostMapping("/customer/houses/{id}/delete")
    String deleteHouse(@PathVariable Long id, @AuthenticationPrincipal PortalUser user, RedirectAttributes flash) {
        try {
            houses.softDelete(user.getUserId(), id);
            flash.addFlashAttribute("success", "Đã ẩn địa chỉ nhà khỏi danh sách đặt lịch.");
        } catch (IllegalArgumentException | IllegalStateException failure) {
            flash.addFlashAttribute("error", failure.getMessage());
        }
        return "redirect:/customer/houses";
    }

    @GetMapping({"/customer/schedules", "/staff/schedules"})
    String schedules(@AuthenticationPrincipal PortalUser user, Model model) {
        model.addAttribute("schedules", query.schedules(user)); return "schedules";
    }

    @GetMapping("/customer/bookings/new")
    String newBooking(@AuthenticationPrincipal PortalUser user, Model model) {
        model.addAttribute("services", query.services()); model.addAttribute("staff", query.staff(user));
        model.addAttribute("schedules", query.schedules(user));
        if (!model.containsAttribute("bookingForm")) { model.addAttribute("bookingForm", new BookingForm(null, null, null, List.of())); }
        // Occupied intervals are shown explicitly; the reservation service rechecks atomically on submit.
        model.addAttribute("busy", query.busyIntervals());
        return "booking-new";
    }

    @PostMapping("/customer/bookings")
    String create(@AuthenticationPrincipal PortalUser user, @Valid @ModelAttribute BookingForm bookingForm,
            BindingResult errors, Model model) {
        if (errors.hasErrors()) { return newBooking(user, model); }
        try {
            List<BookingReservationService.ServiceSelection> selections = new ArrayList<>();
            for (int i = 0; i < bookingForm.serviceIds().size(); i++) {
                selections.add(new BookingReservationService.ServiceSelection(bookingForm.scheduleId(), bookingForm.serviceIds().get(i), i + 1));
            }
            Long id = reservations.createBooking(user.getAccountId(), bookingForm.staffId(),
                    bookingForm.startAt().atOffset(ZoneOffset.ofHours(7)), selections).getBookingId();
            return "redirect:/customer/bookings/" + id;
        } catch (IllegalArgumentException | IllegalStateException failure) {
            model.addAttribute("error", PortalMessages.business(failure)); return newBooking(user, model);
        }
    }

    @GetMapping({"/customer/bookings/{id}", "/staff/bookings/{id}", "/admin/bookings/{id}"})
    String detail(@PathVariable Long id, @AuthenticationPrincipal PortalUser user, Model model) {
        BookingCard booking = query.booking(id, user);
        List<DetailCard> details = query.details(id, user);
        model.addAttribute("booking", booking); model.addAttribute("details", details);
        model.addAttribute("allEvidenceReady", !details.isEmpty() && details.stream().allMatch(d -> !d.evidence().isEmpty() && d.evidence().size() <= 5));
        return "booking-detail";
    }

    @PostMapping("/customer/bookings/{id}/cancel")
    String cancel(@PathVariable Long id, @AuthenticationPrincipal PortalUser user, RedirectAttributes flash) {
        actions.cancel(id, user.getAccountId()); flash.addFlashAttribute("success", "Đã hủy lịch hẹn và giải phóng thời gian."); return "redirect:/customer/bookings/" + id;
    }

    @PostMapping("/customer/bookings/{id}/payment")
    String method(@PathVariable Long id, @RequestParam PaymentMethod method, @AuthenticationPrincipal PortalUser user, RedirectAttributes flash) {
        query.booking(id, user);
        if (method == PaymentMethod.PAYOS) {
            return "redirect:" + payosPayments.checkout(id, user.getAccountId());
        }
        payments.chooseMethod(id, method);
        flash.addFlashAttribute("success", "Đã lưu phương thức. Thanh toán chờ quản trị viên xác minh nhận tiền."); return "redirect:/customer/bookings/" + id;
    }

    @GetMapping({"/payments/payos/return", "/payments/payos/cancel"})
    String payosReturn(RedirectAttributes flash) {
        flash.addFlashAttribute("success", "Đã quay lại từ PayOS. Trạng thái thanh toán chỉ cập nhật sau khi hệ thống xác minh webhook.");
        return "redirect:/customer/bookings";
    }

    @PostMapping("/payments/payos/webhook")
    ResponseEntity<Void> payosWebhook(@RequestBody PayosWebhookRequest webhook) {
        payosPayments.receiveWebhook(webhook);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/customer/bookings/{id}/feedback")
    String feedback(@PathVariable Long id, @AuthenticationPrincipal PortalUser user, @Valid @ModelAttribute FeedbackForm form,
            BindingResult errors, RedirectAttributes flash) {
        if (errors.hasErrors()) { flash.addFlashAttribute("error", "Chọn 1–5 sao và nhập nhận xét tối đa 2.000 ký tự."); }
        else { actions.feedback(id, user.getAccountId(), form); flash.addFlashAttribute("success", "Cảm ơn bạn đã gửi đánh giá."); }
        return "redirect:/customer/bookings/" + id;
    }

    @PostMapping("/customer/staff/{id}/favorite")
    String favorite(@PathVariable Long id, @RequestParam boolean favorite, @AuthenticationPrincipal PortalUser user) {
        actions.favorite(id, user.getAccountId(), favorite); return "redirect:/customer/staff";
    }

    @PostMapping("/staff/details/{id}/evidence")
    String upload(@PathVariable Long id, @RequestParam MultipartFile image, @AuthenticationPrincipal PortalUser user, RedirectAttributes flash) {
        Long bookingId = query.bookingIdForDetail(id, user);
        try { storage.upload(id, user.getAccountId(), image); flash.addFlashAttribute("success", "Đã lưu ảnh bằng chứng."); }
        catch (IllegalArgumentException | IllegalStateException failure) { flash.addFlashAttribute("error", PortalMessages.business(failure)); }
        return "redirect:/staff/bookings/" + bookingId;
    }

    @PostMapping("/staff/bookings/{id}/complete")
    String complete(@PathVariable Long id, @AuthenticationPrincipal PortalUser user, RedirectAttributes flash) {
        query.booking(id, user); completion.completeBooking(id, user.getAccountId(), PortalActionsService.now());
        flash.addFlashAttribute("success", "Công việc đã hoàn thành. Hệ thống đã tạo thanh toán chưa trả."); return "redirect:/staff/bookings/" + id;
    }

    @PostMapping("/admin/bookings/{id}/paid")
    String paid(@PathVariable Long id, @RequestParam(defaultValue = "false") boolean receiptVerified,
            @AuthenticationPrincipal PortalUser user, RedirectAttributes flash) {
        query.booking(id, user); payments.confirmReceivedByAdmin(id, receiptVerified, PortalActionsService.now());
        flash.addFlashAttribute("success", "Đã xác minh nhận tiền."); return "redirect:/admin/bookings/" + id;
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
