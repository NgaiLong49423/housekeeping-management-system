package com.housekeeping.management.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verifyNoInteractions;

import com.housekeeping.management.dto.request.PortalForms.CompanySettingsForm;
import com.housekeeping.management.dto.request.PortalForms.CompanyScheduleVersionForm;
import com.housekeeping.management.dto.request.PortalForms.CompanyLocationForm;
import com.housekeeping.management.dto.request.PortalForms.OperatingHoursForm;
import com.housekeeping.management.repository.CompanySettingsRepository;
import java.math.BigDecimal;
import java.time.LocalTime;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class CompanySettingsServiceTest {
    private final CompanySettingsRepository repository = mock(CompanySettingsRepository.class);
    private final VietmapClient vietmap = mock(VietmapClient.class);
    private final CompanySettingsService service = new CompanySettingsService(repository, vietmap);

    @Test
    void firstSetupDoesNotInventCompanyLocationOrOpeningHours() {
        when(repository.findSettings()).thenReturn(Optional.empty());

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
        when(repository.conflictingFutureConfirmedBookings(BigDecimal.TEN, BigDecimal.valueOf(106), BigDecimal.ONE)).thenReturn(List.of(15L,19L));
        assertThatThrownBy(() -> service.saveLocation(form)).isInstanceOf(IllegalStateException.class).hasMessageContaining("15, 19");
        verify(repository).lockCompanySettings();
        verify(repository, never()).saveLocation(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void resolvesSelectedVietmapPlaceBeforeSavingCompanyCoordinates() {
        CompanyLocationForm form = new CompanyLocationForm("place-1", BigDecimal.ONE);
        VietmapClient.Place place = new VietmapClient.Place("Verified address", null, null, null, null,
                BigDecimal.TEN, BigDecimal.valueOf(106));
        when(vietmap.place("place-1")).thenReturn(place);
        when(repository.conflictingFutureConfirmedBookings(place.latitude(), place.longitude(), BigDecimal.ONE)).thenReturn(List.of());

        service.saveLocation(form);

        verify(repository).saveLocation("Verified address", place.latitude(), place.longitude(), BigDecimal.ONE);
    }

    @Test
    void vietmapFailureLeavesCompanySettingsUntouched() {
        CompanyLocationForm form = new CompanyLocationForm("place-1", BigDecimal.ONE);
        when(vietmap.place("place-1")).thenThrow(new IllegalStateException("VietMap unavailable"));

        assertThatThrownBy(() -> service.saveLocation(form)).isInstanceOf(IllegalStateException.class)
                .hasMessage("VietMap unavailable");
        verifyNoInteractions(repository);
    }

    @Test
    void createsFutureScheduleVersionAfterCheckingItsEffectiveInterval() {
        LocalDate effective=service.firstSelectableWeekStart().plusWeeks(1);
        CompanyScheduleVersionForm form=new CompanyScheduleVersionForm(effective,validForm().operatingHours());
        when(repository.lockCompanySettings()).thenReturn(List.of(1L));
        when(repository.hasAnySchedule()).thenReturn(true);
        when(repository.findOperatingHoursForDate(effective)).thenReturn(validForm().operatingHours());
        when(repository.nextVersionDate(effective)).thenReturn(null);
        when(repository.bookingsInVersionInterval(effective,null)).thenReturn(List.of());
        service.saveSchedule(form);
        verify(repository).saveSchedule(effective,form.operatingHours());
    }

    @Test
    void blocksScheduleMutationForAnyActiveBookingInItsEffectiveInterval() {
        LocalDate effective=service.firstSelectableWeekStart().plusWeeks(1);
        CompanyScheduleVersionForm form=new CompanyScheduleVersionForm(effective,validForm().operatingHours());
        when(repository.lockCompanySettings()).thenReturn(List.of(1L));
        when(repository.hasAnySchedule()).thenReturn(true);
        when(repository.findOperatingHoursForDate(effective)).thenReturn(validForm().operatingHours());
        when(repository.bookingsInVersionInterval(effective,null)).thenReturn(List.of(42L));
        assertThatThrownBy(()->service.saveSchedule(form)).isInstanceOf(IllegalStateException.class).hasMessageContaining("42");
        verify(repository,never()).saveSchedule(effective,form.operatingHours());
    }

    @Test
    void rejectsPastScheduleVersions() {
        CompanyScheduleVersionForm form=new CompanyScheduleVersionForm(service.today().minusWeeks(1).with(java.time.DayOfWeek.MONDAY),validForm().operatingHours());
        assertThatThrownBy(()->service.saveSchedule(form)).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("hôm nay");
        verify(repository,never()).saveSchedule(form.effectiveFrom(),form.operatingHours());
    }

    @Test
    void allowsEditingTheVersionCurrentlyAppliedToThisWeek() {
        LocalDate effective=service.today().with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY));
        CompanyScheduleVersionForm form=new CompanyScheduleVersionForm(effective,validForm().operatingHours());
        when(repository.lockCompanySettings()).thenReturn(List.of(1L));
        when(repository.exactVersionDate(effective)).thenReturn(Optional.of(effective));
        when(repository.scheduleVersionDates()).thenReturn(List.of(effective,LocalDate.of(1900,1,1)));
        when(repository.hasAnySchedule()).thenReturn(true);
        when(repository.findOperatingHoursForDate(org.mockito.ArgumentMatchers.any(LocalDate.class))).thenReturn(validForm().operatingHours());
        when(repository.nextVersionDate(effective)).thenReturn(null);
        when(repository.bookingsInVersionInterval(effective,null)).thenReturn(List.of());

        service.saveSchedule(form,true);

        verify(repository).saveSchedule(effective,form.operatingHours());
    }

    @Test
    void allowsCreatingCurrentWeekScheduleWhenNoVersionStartsThisWeek() {
        LocalDate effective=service.today().with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY));
        CompanyScheduleVersionForm form=new CompanyScheduleVersionForm(effective,validForm().operatingHours());
        when(repository.lockCompanySettings()).thenReturn(List.of(1L));
        when(repository.exactVersionDate(effective)).thenReturn(Optional.empty());
        when(repository.scheduleVersionDates()).thenReturn(List.of(LocalDate.of(1900,1,1)));
        when(repository.hasAnySchedule()).thenReturn(true);
        when(repository.findOperatingHoursForDate(org.mockito.ArgumentMatchers.any(LocalDate.class))).thenReturn(validForm().operatingHours());
        when(repository.nextVersionDate(effective)).thenReturn(null);
        when(repository.bookingsInVersionInterval(effective,null)).thenReturn(List.of());

        service.saveSchedule(form,false);

        verify(repository).saveSchedule(effective,form.operatingHours());
    }

    @Test
    void rejectsScheduleVersionsThatDoNotStartOnMonday() {
        LocalDate effective=service.firstSelectableWeekStart().plusDays(1);
        CompanyScheduleVersionForm form=new CompanyScheduleVersionForm(effective,validForm().operatingHours());
        assertThatThrownBy(()->service.saveSchedule(form)).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Thứ Hai");
        verify(repository,never()).saveSchedule(form.effectiveFrom(),form.operatingHours());
    }

    private static CompanySettingsForm validForm() {
        List<OperatingHoursForm> week = java.util.stream.IntStream.rangeClosed(1, 7)
                .mapToObj(day -> day == 7
                        ? new OperatingHoursForm(day, true, null, null)
                        : new OperatingHoursForm(day, false, LocalTime.of(8, 0), LocalTime.of(17, 0)))
                .toList();
        return new CompanySettingsForm("Address", BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ONE, week);
    }
}
