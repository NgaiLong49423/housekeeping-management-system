package com.housekeeping.management.controller;

import com.housekeeping.management.dto.request.PortalForms.AvailabilityForm;
import com.housekeeping.management.dto.request.PortalForms.AvailabilityBatchForm;
import com.housekeeping.management.dto.response.PortalViews.StaffCard;
import com.housekeeping.management.dto.response.PortalViews.ScheduleCard;
import com.housekeeping.management.dto.response.StaffAvailabilityWeekDay;
import com.housekeeping.management.service.BookingArrivalService;
import com.housekeeping.management.service.BookingCompletionService;
import com.housekeeping.management.service.BookingIncidentService;
import com.housekeeping.management.service.CompanySettingsService;
import com.housekeeping.management.service.EvidenceStorageService;
import com.housekeeping.management.service.CaseAttachmentStorageService;
import com.housekeeping.management.service.PortalQueryService;
import com.housekeeping.management.service.StaffAvailabilityService;
import com.housekeeping.management.service.StaffFollowService;
import com.housekeeping.management.service.StaffIncidentNoticeService;
import com.housekeeping.management.web.PortalMessages;
import com.housekeeping.management.web.PortalUser;
import jakarta.validation.Valid;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.DayOfWeek;
import java.time.temporal.TemporalAdjusters;
import java.util.Comparator;
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
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class StaffController {
    private final PortalQueryService query;
    private final StaffAvailabilityService availability;
    private final CompanySettingsService companySettings;
    private final StaffFollowService follows;
    private final BookingCompletionService completion;
    private final BookingArrivalService arrival;
    private final BookingIncidentService incidents;
    private final EvidenceStorageService storage;
    private final CaseAttachmentStorageService caseAttachments;
    private final StaffIncidentNoticeService incidentNotices;
    private final Path evidenceRoot;

    public StaffController(PortalQueryService query, StaffAvailabilityService availability, CompanySettingsService companySettings, StaffFollowService follows,
            BookingCompletionService completion, BookingArrivalService arrival, BookingIncidentService incidents, EvidenceStorageService storage, CaseAttachmentStorageService caseAttachments, StaffIncidentNoticeService incidentNotices,
            @Value("${app.evidence.storage-root:./var/evidence}") String evidenceRoot) {
        this.query = query;
        this.availability = availability;
        this.companySettings = companySettings;
        this.follows = follows;
        this.completion = completion;
        this.arrival = arrival;
        this.incidents = incidents;
        this.storage = storage;
        this.caseAttachments=caseAttachments;
        this.incidentNotices=incidentNotices;
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
    String schedules(@AuthenticationPrincipal PortalUser user, @RequestParam(required = false) LocalDate weekStart, Model model) {
        List<ScheduleCard> ownSchedules = query.schedules(user);
        model.addAttribute("schedules", ownSchedules);
        if ("STAFF".equals(user.getRole())) {
            boolean partTime = query.isPartTime(user.getAccountId());
            model.addAttribute("partTime", partTime);
            if (partTime) addAvailabilityFormModel(model, new AvailabilityForm(
                    LocalDate.now(ZoneOffset.ofHours(7)), LocalTime.of(8, 0), LocalTime.of(12, 0)), ownSchedules, weekStart);
        }
        return "schedules";
    }

    @GetMapping("/staff/incident-actions")
    String incidentActions(@AuthenticationPrincipal PortalUser user, Model model) {
        model.addAttribute("actions",incidentNotices.ownApprovedActions(user.getAccountId()));
        return "staff/incident-actions";
    }

    @PostMapping("/staff/incident-actions/{id}/acknowledge")
    String acknowledgeIncidentAction(@PathVariable Long id, @AuthenticationPrincipal PortalUser user, RedirectAttributes flash) {
        try { incidentNotices.acknowledge(id,user.getAccountId()); flash.addFlashAttribute("success","Đã xác nhận đã nhận thông báo; thao tác này không đồng nghĩa thừa nhận lỗi."); }
        catch (IllegalStateException failure) { flash.addFlashAttribute("error",failure.getMessage()); }
        return "redirect:/staff/incident-actions";
    }

    @PostMapping("/staff/incident-actions/{id}/response")
    String respondIncidentAction(@PathVariable Long id, @RequestParam String response,
            @RequestParam(defaultValue="false") boolean appeal, @RequestParam(required=false) List<MultipartFile> files,
            @AuthenticationPrincipal PortalUser user, RedirectAttributes flash) {
        try {
            if(files!=null && files.stream().anyMatch(file->file!=null && !file.isEmpty()))
                caseAttachments.uploadStaffActionEvidence(id,user.getAccountId(),user.getUserId(),appeal?"APPEAL":"RESPONSE",
                        files.stream().filter(file->file!=null && !file.isEmpty()).toList());
            incidentNotices.respond(id,user.getAccountId(),response,appeal);
            flash.addFlashAttribute("success",appeal?"Đã gửi phản đối kèm giải trình; Manager sẽ xem xét.":"Đã gửi giải trình; hồ sơ chuyển đến Manager.");
        } catch(IllegalArgumentException | IllegalStateException failure) { flash.addFlashAttribute("error",PortalMessages.business(failure)); }
        return "redirect:/staff/incident-actions";
    }

    @PostMapping("/staff/schedules")
    String addAvailability(@AuthenticationPrincipal PortalUser user,
            @Valid @ModelAttribute AvailabilityForm form, BindingResult errors, Model model, RedirectAttributes flash,
            @RequestParam(required = false) LocalDate weekStart) {
        if (errors.hasErrors()) {
            model.addAttribute("error", "Ngày và giờ sẵn sàng chưa hợp lệ.");
            List<ScheduleCard> ownSchedules=query.schedules(user); model.addAttribute("schedules", ownSchedules); model.addAttribute("partTime", true); addAvailabilityFormModel(model, form,ownSchedules,weekStart);
            return "schedules";
        } else {
            try {
                availability.save(user.getUserId(), null, form);
                flash.addFlashAttribute("success", "Đã đăng ký khoảng sẵn sàng.");
            } catch (IllegalArgumentException | IllegalStateException failure) {
                model.addAttribute("error", PortalMessages.business(failure));
                List<ScheduleCard> ownSchedules=query.schedules(user); model.addAttribute("schedules", ownSchedules); model.addAttribute("partTime", true); addAvailabilityFormModel(model, form,ownSchedules,weekStart);
                return "schedules";
            }
        }
        return staffWeekRedirect(weekStart);
    }

    @PostMapping("/staff/schedules/batch")
    String addAvailabilityBatch(@AuthenticationPrincipal PortalUser user,
            @Valid @ModelAttribute AvailabilityBatchForm form, BindingResult errors, Model model, RedirectAttributes flash) {
        if (errors.hasErrors()) {
            flash.addFlashAttribute("error", "Vui lòng chọn khoảng ngày và giờ hợp lệ.");
            return "redirect:/staff/schedules";
        }
        try {
            int added = availability.saveBatch(user.getUserId(), form);
            flash.addFlashAttribute("success", "Đã đăng ký " + added + " ngày làm việc theo lịch lặp.");
        } catch (IllegalArgumentException | IllegalStateException failure) {
            flash.addFlashAttribute("error", PortalMessages.business(failure));
        }
        return "redirect:/staff/schedules";
    }

    @PostMapping("/staff/schedules/day")
    String addAvailabilityForDate(@AuthenticationPrincipal PortalUser user,
            @RequestParam(required = false) String workDate, @RequestParam(required = false) List<String> startTimes,
            @RequestParam(required = false) List<String> endTimes, @RequestParam(required = false) String weekStart,
            RedirectAttributes flash) {
        LocalDate parsedWorkDate = null;
        LocalDate parsedWeekStart = null;
        try {
            parsedWorkDate = parseDateRequired(workDate, "Hãy chọn ngày đăng ký.");
            parsedWeekStart = parseDate(weekStart);
            int added = availability.saveForDate(user.getUserId(), parsedWorkDate,
                    parseTimes(startTimes, "giờ bắt đầu"), parseTimes(endTimes, "giờ kết thúc"));
            flash.addFlashAttribute("success", "Đã đăng ký " + added + " khoảng giờ trong ngày.");
        } catch (java.time.DateTimeException | IllegalArgumentException | IllegalStateException failure) {
            flash.addFlashAttribute("error", PortalMessages.business(failure));
        }
        LocalDate returnWeek = parsedWeekStart != null ? parsedWeekStart
                : parsedWorkDate == null ? null : parsedWorkDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        return staffWeekRedirect(returnWeek);
    }

    private static LocalDate parseDate(String value) {
        if (value == null || value.isBlank()) return null;
        return LocalDate.parse(value);
    }

    private static LocalDate parseDateRequired(String value, String message) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(message);
        return LocalDate.parse(value);
    }

    private static List<LocalTime> parseTimes(List<String> values, String label) {
        if (values == null || values.isEmpty()) return List.of();
        try {
            if (values.stream().anyMatch(value -> value == null || value.isBlank())) {
                throw new IllegalArgumentException("Hãy chọn đủ " + label + " cho từng khoảng.");
            }
            return values.stream().map(LocalTime::parse).toList();
        } catch (IllegalArgumentException validationFailure) {
            throw validationFailure;
        } catch (java.time.DateTimeException invalidTime) {
            throw new IllegalArgumentException("Có " + label + " không hợp lệ. Hãy chọn lại giờ theo từng khoảng.", invalidTime);
        }
    }

    @PostMapping("/staff/schedules/{id}/replace")
    String replaceAvailability(@PathVariable Long id, @AuthenticationPrincipal PortalUser user,
            @Valid @ModelAttribute AvailabilityForm form, BindingResult errors, Model model, RedirectAttributes flash,
            @RequestParam(required = false) LocalDate weekStart) {
        LocalDate returnWeek = form.workDate() == null ? weekStart : form.workDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        if (errors.hasErrors()) {
            model.addAttribute("error", "Ngày và giờ sẵn sàng chưa hợp lệ.");
            List<ScheduleCard> ownSchedules=query.schedules(user); model.addAttribute("schedules", ownSchedules); model.addAttribute("partTime", true); addAvailabilityFormModel(model, form,ownSchedules,returnWeek);
            return "schedules";
        }
        try {
            availability.replace(user.getUserId(), id, form);
            flash.addFlashAttribute("success", "Đã cập nhật khoảng sẵn sàng. Lịch sử khoảng cũ vẫn được giữ.");
        } catch (IllegalArgumentException | IllegalStateException failure) {
            model.addAttribute("error", PortalMessages.business(failure));
            List<ScheduleCard> ownSchedules=query.schedules(user); model.addAttribute("schedules", ownSchedules); model.addAttribute("partTime", true); addAvailabilityFormModel(model, form,ownSchedules,returnWeek);
            return "schedules";
        }
        return staffWeekRedirect(returnWeek);
    }

    private String staffWeekRedirect(LocalDate weekStart) {
        return weekStart == null ? "redirect:/staff/schedules" : "redirect:/staff/schedules?weekStart=" + weekStart;
    }

    private void addAvailabilityFormModel(Model model, AvailabilityForm form, List<ScheduleCard> schedules, LocalDate requestedWeekStart) {
        model.addAttribute("availabilityForm", form);
        List<CompanySettingsService.ScheduleWindow> allWindows=companySettings.scheduleWindows();
        model.addAttribute("companyScheduleWindows",allWindows);
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh"));
        model.addAttribute("businessToday", today);
        LocalDate currentWeek=today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate selected=requestedWeekStart==null?currentWeek:requestedWeekStart.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        model.addAttribute("staffWeekStart",selected);
        model.addAttribute("staffWeekEnd",selected.plusDays(6));
        model.addAttribute("staffPreviousWeek",selected.minusWeeks(1));
        model.addAttribute("staffNextWeek",selected.plusWeeks(1));
        model.addAttribute("staffCanNavigateNext",!selected.plusWeeks(1).isAfter(currentWeek.plusWeeks(52)));
        model.addAttribute("staffWeekReadOnly",selected.isBefore(currentWeek));
        var formRules = availability.formRules(query.services().stream().map(card -> card.duration()).toList(),
                LocalDateTime.now(ZoneId.of("Asia/Ho_Chi_Minh")));
        model.addAttribute("minimumServiceDuration",formRules.minimumServiceDuration());
        List<StaffAvailabilityWeekDay> days=new java.util.ArrayList<>();
        for(int offset=0;offset<7;offset++){
            LocalDate date=selected.plusDays(offset);
            if(selected.equals(currentWeek)&&date.isBefore(today))continue;
            LocalDate effective=allWindows.stream().map(CompanySettingsService.ScheduleWindow::effectiveFrom)
                    .filter(version->!version.isAfter(date)).max(LocalDate::compareTo).orElse(null);
            int weekday=date.getDayOfWeek().getValue();
            List<StaffAvailabilityWeekDay.AdminWindow> adminWindows=effective==null?List.of():allWindows.stream()
                    .filter(window->window.effectiveFrom().equals(effective)&&window.dayOfWeek()==weekday&&!window.closed()
                            &&window.openingTime()!=null&&window.closingTime()!=null)
                    .sorted(Comparator.comparing(CompanySettingsService.ScheduleWindow::openingTime))
                    .map(window->new StaffAvailabilityWeekDay.AdminWindow(window.openingTime(),window.closingTime())).toList();
            List<ScheduleCard> registered=schedules.stream().filter(slot->slot.start().toLocalDate().equals(date)).toList();
            boolean canRegister=date.isAfter(today)&&!adminWindows.isEmpty();
            if(date.equals(today))canRegister=availability.canRegisterToday(today,serviceWindows(adminWindows),formRules);
            LocalTime defaultStart=adminWindows.isEmpty()?null:adminWindows.getFirst().start();
            LocalTime defaultEnd=adminWindows.isEmpty()?null:adminWindows.getFirst().end();
            days.add(new StaffAvailabilityWeekDay(date,adminWindows,registered,canRegister,defaultStart,defaultEnd));
        }
        model.addAttribute("staffWeekDays",days);
        LocalDate effectiveToday=allWindows.stream().map(CompanySettingsService.ScheduleWindow::effectiveFrom)
                .filter(version->!version.isAfter(today)).max(LocalDate::compareTo).orElse(null);
        int todayWeekday=today.getDayOfWeek().getValue();
        List<StaffAvailabilityWeekDay.AdminWindow> todayWindows=effectiveToday==null?List.of():allWindows.stream()
                .filter(window->window.effectiveFrom().equals(effectiveToday)&&window.dayOfWeek()==todayWeekday&&!window.closed()
                        &&window.openingTime()!=null&&window.closingTime()!=null)
                .map(window->new StaffAvailabilityWeekDay.AdminWindow(window.openingTime(),window.closingTime())).toList();
        model.addAttribute("staffTodayEditable",availability.canRegisterToday(today,serviceWindows(todayWindows),formRules));
    }

    private static List<StaffAvailabilityService.AvailabilityWindow> serviceWindows(List<StaffAvailabilityWeekDay.AdminWindow> windows) {
        return windows.stream().map(window -> new StaffAvailabilityService.AvailabilityWindow(window.start(), window.end())).toList();
    }

    @PostMapping("/staff/schedules/{id}/delete")
    String deleteAvailability(@PathVariable Long id, @AuthenticationPrincipal PortalUser user,
            RedirectAttributes flash, @RequestParam(required = false) LocalDate weekStart) {
        try {
            availability.delete(user.getUserId(), id);
            flash.addFlashAttribute("success", "Đã ngừng hiệu lực khoảng sẵn sàng; lịch sử vẫn được giữ.");
        } catch (IllegalArgumentException | IllegalStateException failure) {
            flash.addFlashAttribute("error", PortalMessages.business(failure));
        }
        return staffWeekRedirect(weekStart);
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

    @PostMapping("/staff/bookings/{id}/arrived")
    String confirmArrival(@PathVariable Long id, @AuthenticationPrincipal PortalUser user, RedirectAttributes flash) {
        query.booking(id, user);
        try {
            arrival.confirmStaffArrival(id, user.getAccountId());
            flash.addFlashAttribute("success", "Đã ghi nhận bạn đến địa điểm.");
        } catch (IllegalStateException failure) {
            flash.addFlashAttribute("error", PortalMessages.business(failure));
        }
        return "redirect:/staff/bookings/" + id;
    }

    @PostMapping("/staff/bookings/{id}/incident")
    String reportIncident(@PathVariable Long id, @RequestParam String note,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime expectedArrivalAt,
            @AuthenticationPrincipal PortalUser user, RedirectAttributes flash) {
        query.booking(id, user);
        try {
            incidents.report(id, user.getAccountId(), note, expectedArrivalAt);
            flash.addFlashAttribute("success", "Đã gửi giờ đến dự kiến cho khách. Hãy gọi khách để xác nhận.");
        } catch (IllegalArgumentException | IllegalStateException failure) {
            flash.addFlashAttribute("error", PortalMessages.business(failure));
        }
        return "redirect:/staff/bookings/" + id;
    }

    @PostMapping("/staff/bookings/{id}/start")
    String start(@PathVariable Long id, @AuthenticationPrincipal PortalUser user, RedirectAttributes flash) {
        query.booking(id, user);
        try {
            completion.startBooking(id, user.getAccountId());
            flash.addFlashAttribute("success", "Đã ghi nhận bắt đầu công việc.");
        } catch (IllegalArgumentException | IllegalStateException failure) {
            flash.addFlashAttribute("error", PortalMessages.business(failure));
        }
        return "redirect:/staff/bookings/" + id;
    }

    @PostMapping("/staff/bookings/{id}/complete")
    String complete(@PathVariable Long id, @AuthenticationPrincipal PortalUser user, RedirectAttributes flash) {
        query.booking(id, user);
        try {
            completion.completeBooking(id, user.getAccountId());
            flash.addFlashAttribute("success", "Công việc đã hoàn thành. Khách hàng có thể chọn phương thức thanh toán.");
        } catch (IllegalArgumentException | IllegalStateException failure) {
            flash.addFlashAttribute("error", PortalMessages.business(failure));
        }
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
