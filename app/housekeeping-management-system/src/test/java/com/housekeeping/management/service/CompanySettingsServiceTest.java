package com.housekeeping.management.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.doThrow;

import com.housekeeping.management.dto.request.PortalForms.CompanySettingsForm;
import com.housekeeping.management.dto.request.PortalForms.CompanyScheduleVersionForm;
import com.housekeeping.management.dto.request.PortalForms.CompanyLocationForm;
import com.housekeeping.management.dto.request.PortalForms.OperatingHoursForm;
import com.housekeeping.management.repository.CompanySettingsRepository;
import com.housekeeping.management.entity.CompanySettings;
import com.housekeeping.management.integration.vietmap.VietmapClient;
import java.math.BigDecimal;
import java.time.LocalTime;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class CompanySettingsServiceTest {
    private final CompanySettingsRepository repository = mock(CompanySettingsRepository.class);
    private final CompanyOperatingHoursService hours = mock(CompanyOperatingHoursService.class);
    private final BookingQueryService bookings = mock(BookingQueryService.class);
    private final StaffAvailabilityService availability = mock(StaffAvailabilityService.class);
    private final VietmapClient vietmap = mock(VietmapClient.class);
    private final AccountAuthorizationService authorization = mock(AccountAuthorizationService.class);
    private final CompanySettingsService service = new com.housekeeping.management.service.impl.CompanySettingsServiceImpl(
            repository, hours, bookings, availability, vietmap, authorization);

    @Test
    void companyLocationAndScheduleMutationsRequireActiveAdminBeforeReadingOrWriting() {
        doThrow(new org.springframework.security.access.AccessDeniedException("Admin inactive"))
                .when(authorization).requireActiveAdmin(99L);

        assertThatThrownBy(() -> service.saveLocation(99L, null)).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        assertThatThrownBy(() -> service.saveSchedule(99L, null)).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        assertThatThrownBy(() -> service.saveSchedule(99L, null, true)).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        assertThatThrownBy(() -> service.cancelSchedule(99L, null)).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);

        verify(authorization, org.mockito.Mockito.times(4)).requireActiveAdmin(99L);
        verifyNoInteractions(repository, hours, bookings, availability, vietmap);
    }

    @Test
    void firstSetupDoesNotInventCompanyLocationOrOpeningHours() {
        when(repository.findAllByOrderByCompanyIdAsc()).thenReturn(List.of());
        when(hours.forDate(org.mockito.ArgumentMatchers.any())).thenReturn(List.of());

        CompanySettingsForm form = service.get();

        assertThat(form.latitude()).isNull();
        assertThat(form.longitude()).isNull();
        assertThat(form.serviceRadiusKm()).isEqualByComparingTo("10.00");
        assertThat(form.operatingHours()).hasSize(7).allSatisfy(day -> assertThat(day.closed()).isTrue());
    }

    @Test
    void acceptsACompleteSevenDaySchedule() {
        CompanySettingsForm form = validForm();
        service.validate(form);
    }

    @Test
    void rejectsMissingDayBeforeCallingRepository() {
        CompanySettingsForm form = new CompanySettingsForm("Address", BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ONE, validForm().operatingHours().subList(0, 6));

        assertThatThrownBy(() -> service.validate(form)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("đủ bảy ngày");
    }

    @Test
    void rejectsOpenDayWithoutValidTimeRange() {
        List<OperatingHoursForm> hours = validForm().operatingHours().stream().toList();
        hours = new java.util.ArrayList<>(hours);
        hours.set(0, new OperatingHoursForm(1, false, LocalTime.of(17, 0), LocalTime.of(9, 0)));
        CompanySettingsForm form = new CompanySettingsForm("Address", BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ONE, hours);

        assertThatThrownBy(() -> service.validate(form)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("giờ bắt đầu và giờ kết thúc");
    }

    @Test
    void acceptsMultipleNonOverlappingWindowsButRejectsOverlapAndOffQuarterHour() {
        var base = validForm().operatingHours();
        List<OperatingHoursForm> windows = new java.util.ArrayList<>(base);
        windows.set(0, new OperatingHoursForm(1, false, LocalTime.of(6, 0), LocalTime.of(11, 0)));
        windows.add(new OperatingHoursForm(1, false, LocalTime.of(13, 0), LocalTime.of(21, 0)));
        CompanySettingsForm valid = new CompanySettingsForm("Address", BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.TEN, windows);
        service.validate(valid);

        windows.set(7, new OperatingHoursForm(1, false, LocalTime.of(10, 45), LocalTime.of(21, 0)));
        CompanySettingsForm overlap = new CompanySettingsForm("Address", BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.TEN, windows);
        assertThatThrownBy(() -> service.validate(overlap)).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("chồng lấn");

        windows.set(7, new OperatingHoursForm(1, false, LocalTime.of(13, 5), LocalTime.of(21, 0)));
        CompanySettingsForm offGrid = new CompanySettingsForm("Address", BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.TEN, windows);
        assertThatThrownBy(() -> service.validate(offGrid)).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("00, 15, 30 hoặc 45");
    }

    @Test
    void blocksLocationChangeWhenActiveBookingsWouldFallOutsideTheRadius() {
        CompanyLocationForm form = new CompanyLocationForm("place-1", BigDecimal.ONE);
        when(vietmap.place("place-1")).thenReturn(new VietmapClient.Place("Verified address", null, null, null, null,
                BigDecimal.TEN, BigDecimal.valueOf(106)));
        when(repository.findAllForUpdate()).thenReturn(List.of(company()));
        when(bookings.futureActiveBookingsWithLocation()).thenReturn(List.of(
                new BookingQueryService.BookingLocation(15L, BigDecimal.ZERO, BigDecimal.ZERO),
                new BookingQueryService.BookingLocation(19L, BigDecimal.ONE, BigDecimal.ONE)));
        assertThatThrownBy(() -> service.saveLocation(1L, form)).isInstanceOf(IllegalStateException.class).hasMessageContaining("15, 19");
        verify(repository, never()).saveAndFlush(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void resolvesSelectedVietmapPlaceBeforeSavingCompanyCoordinates() {
        CompanyLocationForm form = new CompanyLocationForm("place-1", BigDecimal.ONE);
        VietmapClient.Place place = new VietmapClient.Place("Verified address", null, null, null, null,
                BigDecimal.TEN, BigDecimal.valueOf(106));
        when(vietmap.place("place-1")).thenReturn(place);
        CompanySettings existing = company();
        when(repository.findAllForUpdate()).thenReturn(List.of(existing));
        when(bookings.futureActiveBookingsWithLocation()).thenReturn(List.of());

        service.saveLocation(1L, form);

        verify(repository).saveAndFlush(existing);
        assertThat(existing.getCompanyAddress()).isEqualTo("Verified address");
    }

    @Test
    void vietmapFailureLeavesCompanySettingsUntouched() {
        CompanyLocationForm form = new CompanyLocationForm("place-1", BigDecimal.ONE);
        when(vietmap.place("place-1")).thenThrow(new IllegalStateException("VietMap unavailable"));

        assertThatThrownBy(() -> service.saveLocation(1L, form)).isInstanceOf(IllegalStateException.class)
                .hasMessage("VietMap unavailable");
        verifyNoInteractions(repository);
    }

    @Test
    void createsFutureScheduleVersionAfterCheckingItsEffectiveInterval() {
        LocalDate effective=service.firstSelectableWeekStart().plusWeeks(1);
        CompanyScheduleVersionForm form=new CompanyScheduleVersionForm(effective,validForm().operatingHours());
        when(repository.findAllForUpdate()).thenReturn(List.of(company()));
        when(hours.hasAnySchedule()).thenReturn(true);
        when(hours.forDate(effective)).thenReturn(validForm().operatingHours());
        when(hours.nextVersionDate(effective)).thenReturn(null);
        when(bookings.activeBookingIdsInInterval(effective,null)).thenReturn(List.of());
        service.saveSchedule(1L, form);
        verify(hours).saveVersion(1L, effective, form.operatingHours());
    }

    @Test
    void blocksScheduleMutationForAnyActiveBookingInItsEffectiveInterval() {
        LocalDate effective=service.firstSelectableWeekStart().plusWeeks(1);
        CompanyScheduleVersionForm form=new CompanyScheduleVersionForm(effective,validForm().operatingHours());
        when(repository.findAllForUpdate()).thenReturn(List.of(company()));
        when(hours.hasAnySchedule()).thenReturn(true);
        when(hours.forDate(effective)).thenReturn(validForm().operatingHours());
        when(bookings.activeBookingIdsInInterval(effective,null)).thenReturn(List.of(42L));
        assertThatThrownBy(()->service.saveSchedule(1L, form)).isInstanceOf(IllegalStateException.class).hasMessageContaining("42");
        verify(hours,never()).saveVersion(1L, effective, form.operatingHours());
    }

    @Test
    void rejectsPastScheduleVersions() {
        CompanyScheduleVersionForm form=new CompanyScheduleVersionForm(service.today().minusDays(1),validForm().operatingHours());
        assertThatThrownBy(()->service.saveSchedule(1L, form)).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("hôm nay");
        verify(hours,never()).saveVersion(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(form.effectiveFrom()), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void allowsEditingTheVersionCurrentlyAppliedToThisWeek() {
        LocalDate effective=service.today().with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY));
        CompanyScheduleVersionForm form=new CompanyScheduleVersionForm(effective,validForm().operatingHours());
        when(repository.findAllForUpdate()).thenReturn(List.of(company()));
        when(hours.exactVersionDate(effective)).thenReturn(Optional.of(effective));
        when(hours.versionDates()).thenReturn(List.of(effective,LocalDate.of(1900,1,1)));
        when(hours.hasAnySchedule()).thenReturn(true);
        when(hours.forDate(org.mockito.ArgumentMatchers.any(LocalDate.class))).thenReturn(validForm().operatingHours());
        when(hours.nextVersionDate(effective)).thenReturn(null);
        when(bookings.activeBookingIdsInInterval(effective,null)).thenReturn(List.of());

        service.saveSchedule(1L, form,true);

        verify(hours).saveVersion(1L, effective, form.operatingHours());
    }

    @Test
    void allowsCreatingCurrentWeekScheduleWhenNoVersionStartsThisWeek() {
        LocalDate effective=service.today().with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY));
        CompanyScheduleVersionForm form=new CompanyScheduleVersionForm(effective,validForm().operatingHours());
        when(repository.findAllForUpdate()).thenReturn(List.of(company()));
        when(hours.exactVersionDate(effective)).thenReturn(Optional.empty());
        when(hours.versionDates()).thenReturn(List.of(LocalDate.of(1900,1,1)));
        when(hours.hasAnySchedule()).thenReturn(true);
        when(hours.forDate(org.mockito.ArgumentMatchers.any(LocalDate.class))).thenReturn(validForm().operatingHours());
        when(hours.nextVersionDate(effective)).thenReturn(null);
        when(bookings.activeBookingIdsInInterval(effective,null)).thenReturn(List.of());

        service.saveSchedule(1L, form,false);

        verify(hours).saveVersion(1L, effective, form.operatingHours());
    }

    @Test
    void rejectsScheduleVersionsThatDoNotStartOnMonday() {
        LocalDate effective=service.firstSelectableWeekStart().plusDays(1);
        CompanyScheduleVersionForm form=new CompanyScheduleVersionForm(effective,validForm().operatingHours());
        assertThatThrownBy(()->service.saveSchedule(1L, form)).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Thứ Hai");
        verify(hours,never()).saveVersion(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(form.effectiveFrom()), org.mockito.ArgumentMatchers.any());
    }

    private static CompanySettingsForm validForm() {
        List<OperatingHoursForm> week = java.util.stream.IntStream.rangeClosed(1, 7)
                .mapToObj(day -> day == 7
                        ? new OperatingHoursForm(day, true, null, null)
                        : new OperatingHoursForm(day, false, LocalTime.of(8, 0), LocalTime.of(17, 0)))
                .toList();
        return new CompanySettingsForm("Address", BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ONE, week);
    }

    private static CompanySettings company() {
        CompanySettings settings = new CompanySettings();
        settings.setCompanyId(1L);
        settings.setCompanyAddress("Office");
        settings.setLatitude(BigDecimal.ZERO);
        settings.setLongitude(BigDecimal.ZERO);
        settings.setServiceRadiusKm(BigDecimal.TEN);
        return settings;
    }
}
