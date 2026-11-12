package com.housekeeping.management.controller;

import com.housekeeping.management.dto.request.PortalForms;
import com.housekeeping.management.dto.request.PortalForms.CompanyLocationForm;
import com.housekeeping.management.dto.request.PortalForms.CompanyScheduleVersionForm;
import com.housekeeping.management.dto.request.PortalForms.CompanySettingsForm;
import com.housekeeping.management.dto.request.PortalForms.BookingDelaySettingsForm;
import com.housekeeping.management.dto.request.PortalForms.BookingDelayTierForm;
import com.housekeeping.management.service.CompanySettingsService;
import com.housekeeping.management.service.BookingDelayCompensationSettingsService;
import com.housekeeping.management.web.PortalUser;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.IntStream;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/admin/settings")
public class AdminCompanySettingsController {
    private final CompanySettingsService settings;
    private final BookingDelayCompensationSettingsService delaySettings;
    public AdminCompanySettingsController(CompanySettingsService settings,
            BookingDelayCompensationSettingsService delaySettings){this.settings=settings;this.delaySettings=delaySettings;}

    @GetMapping String view(@RequestParam(required=false) LocalDate effectiveFrom,Model model){
        LocalDate today=settings.today();
        LocalDate currentWeekStart=today.with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY));
        LocalDate firstSelectableWeek=settings.firstSelectableWeekStart();
        LocalDate baseline=LocalDate.of(1900,1,1);
        LocalDate selected=effectiveFrom==null||effectiveFrom.equals(baseline)
                ? today.with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY))
                : effectiveFrom;
        CompanyScheduleVersionForm schedule=settings.scheduleForEditor(selected);
        boolean exactVersion=settings.hasExactVersion(selected);
        CompanySettingsForm location=settings.get();
        CompanyLocationForm locationForm=new CompanyLocationForm("",location.serviceRadiusKm());
        model.addAttribute("settingsForm",locationForm);
        model.addAttribute("delaySettingsForm", new BookingDelaySettingsForm(delaySettings.get().stream()
                .map(tier -> new BookingDelayTierForm(tier.minDelayMinutes(), tier.maxDelayMinutes(), tier.discountPercent()))
                .toList()));
        model.addAttribute("currentCompanyAddress",location.companyAddress());
        model.addAttribute("scheduleForm",schedule);
        List<LocalDate> versionDates=settings.versionDates();
        model.addAttribute("scheduleVersions",versionDates.stream().filter(date->!date.equals(baseline)).toList());
        model.addAttribute("hasBaselineVersion",settings.hasExactVersion(baseline));
        model.addAttribute("businessToday",today);
        model.addAttribute("currentWeekStart",currentWeekStart);
        model.addAttribute("currentDayOfWeek",today.getDayOfWeek().getValue());
        model.addAttribute("selectedCurrentWeek",selected.equals(currentWeekStart));
        model.addAttribute("firstSelectableWeek",firstSelectableWeek);
        model.addAttribute("weekDates",java.util.stream.IntStream.range(0,7).mapToObj(selected::plusDays).toList());
        model.addAttribute("weekEnd",selected.plusDays(6));
        model.addAttribute("previousWeek",selected.minusWeeks(1));
        model.addAttribute("nextWeek",selected.plusWeeks(1));
        model.addAttribute("canNavigateNext",!selected.plusWeeks(1).isAfter(today.with(java.time.temporal.TemporalAdjusters.nextOrSame(java.time.DayOfWeek.MONDAY)).plusWeeks(52)));
        model.addAttribute("selectedVersionExists",exactVersion);
        model.addAttribute("selectedVersionFuture",exactVersion&&selected.isAfter(today));
        model.addAttribute("scheduleReadOnly",selected.isBefore(currentWeekStart));
        addOptions(model,schedule);
        return "admin/settings";
    }

    private void addOptions(Model model,CompanyScheduleVersionForm form){
        model.addAttribute("dayNames",new String[]{"Thứ Hai","Thứ Ba","Thứ Tư","Thứ Năm","Thứ Sáu","Thứ Bảy","Chủ nhật"});
        model.addAttribute("hourOptions",IntStream.range(0,24).mapToObj(i->String.format("%02d",i)).toList());
        model.addAttribute("minuteOptions",List.of("00","15","30","45"));
        model.addAttribute("closedDays",form.operatingHours().stream().filter(PortalForms.OperatingHoursForm::closed).map(PortalForms.OperatingHoursForm::dayOfWeek).collect(java.util.stream.Collectors.toSet()));
    }

    @PostMapping("/location") String saveLocation(@AuthenticationPrincipal PortalUser user, @Valid @ModelAttribute("settingsForm") CompanyLocationForm form,BindingResult errors,RedirectAttributes flash){
        if(errors.hasErrors()){flash.addFlashAttribute("error","Vui lòng nhập đầy đủ địa chỉ, tọa độ và bán kính hợp lệ.");return "redirect:/admin/settings";}
        try{settings.saveLocation(user.getUserId(),form);flash.addFlashAttribute("success","Đã lưu vị trí và bán kính phục vụ.");}
        catch(IllegalArgumentException|IllegalStateException ex){flash.addFlashAttribute("error",ex.getMessage());}
        return "redirect:/admin/settings";
    }

    @org.springframework.web.bind.annotation.GetMapping("/location/autocomplete")
    @org.springframework.web.bind.annotation.ResponseBody
    java.util.List<com.housekeeping.management.dto.response.LocationSuggestion> locationAutocomplete(@RequestParam String q) {
        return settings.locationAutocomplete(q);
    }

    @PostMapping("/schedule") String saveSchedule(@Valid @ModelAttribute("scheduleForm") CompanyScheduleVersionForm form,
            BindingResult errors,@RequestParam(defaultValue="false") boolean editingExisting,RedirectAttributes flash,
            @AuthenticationPrincipal PortalUser user){
        if(errors.hasErrors()){
            flash.addFlashAttribute("scheduleError","Vui lòng kiểm tra ngày hiệu lực và đủ khung giờ trong tuần.");
            return scheduleRedirect(form.effectiveFrom());
        }
        try{settings.saveSchedule(user.getUserId(),form,editingExisting);flash.addFlashAttribute("scheduleSuccess","Đã lưu giờ hoạt động cho tuần bắt đầu từ Thứ Hai "+form.effectiveFrom()+".");}
        catch(IllegalArgumentException|IllegalStateException ex){flash.addFlashAttribute("scheduleError",ex.getMessage());}
        return scheduleRedirect(form.effectiveFrom());
    }

    @PostMapping("/schedule/day") String saveScheduleDay(@Valid @ModelAttribute("scheduleForm") CompanyScheduleVersionForm form,
            BindingResult errors, @RequestParam int dayOfWeek, @RequestParam(defaultValue="false") boolean editingExisting,
            RedirectAttributes flash, @AuthenticationPrincipal PortalUser user) {
        if (errors.hasErrors()) {
            flash.addFlashAttribute("scheduleError", "Vui lòng kiểm tra khung giờ của ngày cần lưu.");
            return scheduleRedirect(form.effectiveFrom());
        }
        try {
            settings.saveScheduleDay(user.getUserId(), form, dayOfWeek, editingExisting);
            flash.addFlashAttribute("scheduleSuccess", "Đã lưu giờ hoạt động ngày "
                    + form.effectiveFrom().plusDays(dayOfWeek - 1L) + ".");
        } catch (IllegalArgumentException | IllegalStateException ex) {
            flash.addFlashAttribute("scheduleError", ex.getMessage());
        }
        return scheduleRedirect(form.effectiveFrom());
    }

    private static String scheduleRedirect(LocalDate effectiveFrom){
        String location="redirect:/admin/settings"+(effectiveFrom==null?"":"?effectiveFrom="+effectiveFrom);
        return location+"#schedule-save-feedback";
    }

    @PostMapping("/schedule/cancel") String cancelSchedule(@RequestParam LocalDate effectiveFrom,RedirectAttributes flash,
            @AuthenticationPrincipal PortalUser user){
        try{settings.cancelSchedule(user.getUserId(),effectiveFrom);flash.addFlashAttribute("success","Đã hủy phiên bản lịch. Các khoảng Staff không phù hợp đã được ngừng hiệu lực.");}
        catch(IllegalArgumentException|IllegalStateException ex){flash.addFlashAttribute("error",ex.getMessage());}
        return "redirect:/admin/settings?effectiveFrom="+effectiveFrom;
    }

    @PostMapping("/delay-compensation") String saveDelayCompensation(
            @Valid @ModelAttribute("delaySettingsForm") BookingDelaySettingsForm form,
            BindingResult errors, RedirectAttributes flash) {
        if (errors.hasErrors()) {
            flash.addFlashAttribute("delaySettingsError", "Vui lòng nhập đầy đủ ngưỡng phút và phần trăm giảm từ 0 đến 30.");
            return "redirect:/admin/settings#delay-compensation";
        }
        try {
            delaySettings.save(form.tiers().stream()
                    .map(tier -> new com.housekeeping.management.dto.BookingDelayCompensation.Tier(
                            tier.minDelayMinutes(), tier.maxDelayMinutes(), tier.discountPercent()))
                    .toList());
            flash.addFlashAttribute("delaySettingsSuccess", "Đã lưu các mức giảm do nhân viên đến trễ.");
        } catch (IllegalArgumentException | IllegalStateException ex) {
            flash.addFlashAttribute("delaySettingsError", ex.getMessage());
        }
        return "redirect:/admin/settings#delay-compensation";
    }
}
