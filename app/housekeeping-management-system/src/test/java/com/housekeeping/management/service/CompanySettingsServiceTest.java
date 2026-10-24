package com.housekeeping.management.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.housekeeping.management.dto.request.PortalForms.CompanySettingsForm;
import com.housekeeping.management.dto.request.PortalForms.OperatingHoursForm;
import com.housekeeping.management.repository.CompanySettingsRepository;
import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class CompanySettingsServiceTest {
    private final CompanySettingsRepository repository = mock(CompanySettingsRepository.class);
    private final CompanySettingsService service = new CompanySettingsService(repository);

    @Test
    void firstSetupDoesNotInventCompanyLocationOrOpeningHours() {
        when(repository.findSettings()).thenReturn(Optional.empty());

        CompanySettingsForm form = service.get();

        assertThat(form.latitude()).isNull();
        assertThat(form.longitude()).isNull();
        assertThat(form.serviceRadiusKm()).isNull();
        assertThat(form.operatingHours()).hasSize(7).allSatisfy(day -> assertThat(day.closed()).isTrue());
    }

    @Test
    void savesSevenDayScheduleAndLocationTogether() {
        CompanySettingsForm form = validForm();

        service.save(form);

        verify(repository).save(form);
    }

    @Test
    void rejectsMissingDayBeforeCallingRepository() {
        CompanySettingsForm form = new CompanySettingsForm("Address", BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ONE, validForm().operatingHours().subList(0, 6));

        assertThatThrownBy(() -> service.save(form)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("đủ đúng một dòng");
        verify(repository, never()).save(form);
    }

    @Test
    void rejectsOpenDayWithoutValidTimeRange() {
        List<OperatingHoursForm> hours = validForm().operatingHours().stream().toList();
        hours = new java.util.ArrayList<>(hours);
        hours.set(0, new OperatingHoursForm(1, false, LocalTime.of(17, 0), LocalTime.of(9, 0)));
        CompanySettingsForm form = new CompanySettingsForm("Address", BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ONE, hours);

        assertThatThrownBy(() -> service.save(form)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("giờ bắt đầu và giờ kết thúc");
        verify(repository, never()).save(form);
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
