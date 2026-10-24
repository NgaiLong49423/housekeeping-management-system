package com.housekeeping.management.repository;

import com.housekeeping.management.dto.request.PortalForms.CompanySettingsForm;
import com.housekeeping.management.dto.request.PortalForms.OperatingHoursForm;
import java.math.BigDecimal;
import java.sql.Time;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class CompanySettingsRepository {
    private final JdbcTemplate jdbc;

    public CompanySettingsRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public Optional<CompanySettingsForm> findSettings() {
        List<CompanySettingsForm> companies = jdbc.query("""
                SELECT company_id, company_address, latitude, longitude, service_radius_km
                FROM dbo.Company_Settings ORDER BY company_id
                """, (rs, row) -> {
            long id = rs.getLong("company_id");
            List<OperatingHoursForm> hours = jdbc.query("""
                    SELECT day_of_week, is_closed, opening_time, closing_time
                    FROM dbo.Company_Operating_Hours WHERE company_id=? ORDER BY day_of_week
                    """, (hourRs, hourRow) -> new OperatingHoursForm(hourRs.getInt("day_of_week"),
                    hourRs.getBoolean("is_closed"), hourRs.getTime("opening_time") == null ? null : hourRs.getTime("opening_time").toLocalTime(),
                    hourRs.getTime("closing_time") == null ? null : hourRs.getTime("closing_time").toLocalTime()), id);
            return new CompanySettingsForm(rs.getString("company_address"), rs.getBigDecimal("latitude"),
                    rs.getBigDecimal("longitude"), rs.getBigDecimal("service_radius_km"), hours);
        });
        if (companies.size() > 1) throw new IllegalStateException("Phát hiện nhiều hơn một cấu hình công ty.");
        return companies.stream().findFirst();
    }

    public void save(CompanySettingsForm form) {
        List<Long> ids = jdbc.query("SELECT company_id FROM dbo.Company_Settings WITH (UPDLOCK, HOLDLOCK) ORDER BY company_id",
                (rs, row) -> rs.getLong(1));
        if (ids.size() > 1) throw new IllegalStateException("Phát hiện nhiều hơn một cấu hình công ty.");
        long companyId;
        if (ids.isEmpty()) {
            companyId = jdbc.queryForObject("""
                    INSERT INTO dbo.Company_Settings (company_address, latitude, longitude, service_radius_km)
                    OUTPUT INSERTED.company_id VALUES (?, ?, ?, ?)
                    """, Long.class, form.companyAddress().trim(), form.latitude(), form.longitude(), form.serviceRadiusKm());
        } else {
            companyId = ids.getFirst();
            jdbc.update("""
                    UPDATE dbo.Company_Settings SET company_address=?, latitude=?, longitude=?, service_radius_km=?
                    WHERE company_id=?
                    """, form.companyAddress().trim(), form.latitude(), form.longitude(), form.serviceRadiusKm(), companyId);
        }
        for (OperatingHoursForm hour : form.operatingHours()) {
            Time opening = hour.closed() ? null : Time.valueOf(hour.openingTime());
            Time closing = hour.closed() ? null : Time.valueOf(hour.closingTime());
            int changed = jdbc.update("""
                    UPDATE dbo.Company_Operating_Hours SET is_closed=?, opening_time=?, closing_time=?
                    WHERE company_id=? AND day_of_week=?
                    """, hour.closed(), opening, closing, companyId, hour.dayOfWeek());
            if (changed == 0) jdbc.update("""
                    INSERT INTO dbo.Company_Operating_Hours (company_id, day_of_week, is_closed, opening_time, closing_time)
                    VALUES (?, ?, ?, ?, ?)
                    """, companyId, hour.dayOfWeek(), hour.closed(), opening, closing);
        }
    }
}
