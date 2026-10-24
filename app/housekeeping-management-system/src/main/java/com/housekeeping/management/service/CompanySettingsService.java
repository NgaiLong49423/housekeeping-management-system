package com.housekeeping.management.service;

import com.housekeeping.management.dto.request.PortalForms.CompanySettingsForm;
import com.housekeeping.management.dto.request.PortalForms.OperatingHoursForm;
import com.housekeeping.management.repository.CompanySettingsRepository;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CompanySettingsService {
    private final CompanySettingsRepository settings;
    public CompanySettingsService(CompanySettingsRepository settings) { this.settings = settings; }

    @Transactional(readOnly = true)
    public CompanySettingsForm get() {
        CompanySettingsForm current = settings.findSettings().orElse(new CompanySettingsForm("", null, null, null, List.of()));
        Map<Integer, OperatingHoursForm> byDay = current.operatingHours().stream()
                .collect(Collectors.toMap(OperatingHoursForm::dayOfWeek, Function.identity()));
        List<OperatingHoursForm> completeWeek = IntStream.rangeClosed(1, 7)
                .mapToObj(day -> byDay.getOrDefault(day, new OperatingHoursForm(day, true, null, null))).toList();
        return new CompanySettingsForm(current.companyAddress(), current.latitude(), current.longitude(),
                current.serviceRadiusKm(), completeWeek);
    }

    @Transactional
    public void save(CompanySettingsForm form) {
        List<OperatingHoursForm> hours = form.operatingHours();
        if (hours == null || hours.size() != 7 || hours.stream().anyMatch(hour -> hour == null || hour.dayOfWeek() == null)
                || hours.stream().map(OperatingHoursForm::dayOfWeek).collect(java.util.stream.Collectors.toSet()).size() != 7
                || !hours.stream().map(OperatingHoursForm::dayOfWeek).collect(java.util.stream.Collectors.toSet()).equals(Set.of(1, 2, 3, 4, 5, 6, 7))) {
            throw new IllegalArgumentException("Cần cấu hình đủ đúng một dòng cho từng ngày từ thứ Hai đến Chủ nhật.");
        }
        for (OperatingHoursForm hour : hours) {
            if (!hour.closed() && (hour.openingTime() == null || hour.closingTime() == null
                    || !hour.openingTime().isBefore(hour.closingTime()))) {
                throw new IllegalArgumentException("Ngày mở cửa phải có giờ bắt đầu và giờ kết thúc hợp lệ.");
            }
        }
        settings.save(form);
    }
}
