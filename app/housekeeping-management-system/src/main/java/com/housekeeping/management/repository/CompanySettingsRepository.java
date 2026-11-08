package com.housekeeping.management.repository;

import com.housekeeping.management.dto.request.PortalForms.CompanySettingsForm;
import com.housekeeping.management.dto.request.PortalForms.OperatingHoursForm;
import java.math.BigDecimal;
import java.sql.Time;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class CompanySettingsRepository {
    private final JdbcTemplate jdbc;
    public CompanySettingsRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public Optional<CompanySettingsForm> findSettings() { return findSettingsForDate(LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh"))); }

    public Optional<CompanySettingsForm> findSettingsForDate(LocalDate date) {
        List<CompanyLocation> companies=jdbc.query("SELECT company_id,company_address,latitude,longitude,service_radius_km FROM dbo.Company_Settings ORDER BY company_id",
                (rs,row)->new CompanyLocation(rs.getLong(1),rs.getString(2),rs.getBigDecimal(3),rs.getBigDecimal(4),rs.getBigDecimal(5)));
        if(companies.size()>1)throw new IllegalStateException("Phát hiện nhiều hơn một cấu hình công ty.");
        if(companies.isEmpty())return Optional.empty();
        CompanyLocation company=companies.getFirst();
        return Optional.of(new CompanySettingsForm(company.address(),company.latitude(),company.longitude(),company.radius(),findOperatingHours(company.id(),date)));
    }

    public List<OperatingHoursForm> findOperatingHoursForDate(LocalDate date) {
        List<Long> ids=jdbc.query("SELECT company_id FROM dbo.Company_Settings ORDER BY company_id",(rs,row)->rs.getLong(1));
        return ids.isEmpty()?List.of():findOperatingHours(ids.getFirst(),date);
    }

    private List<OperatingHoursForm> findOperatingHours(long companyId, LocalDate date) {
        return jdbc.query("""
                SELECT day_of_week,is_closed,opening_time,closing_time FROM dbo.Company_Operating_Hours
                WHERE company_id=? AND effective_from=(SELECT MAX(effective_from) FROM dbo.Company_Operating_Hours WHERE company_id=? AND effective_from<=?)
                ORDER BY day_of_week,period_no
                """,(rs,row)->new OperatingHoursForm(rs.getInt(1),rs.getBoolean(2),rs.getTime(3)==null?null:rs.getTime(3).toLocalTime(),rs.getTime(4)==null?null:rs.getTime(4).toLocalTime()),companyId,companyId,date);
    }

    public List<LocalDate> scheduleVersionDates() {
        return jdbc.query("SELECT DISTINCT effective_from FROM dbo.Company_Operating_Hours ORDER BY effective_from DESC",(rs,row)->rs.getObject(1,LocalDate.class));
    }
    public boolean hasAnySchedule(){Integer count=jdbc.queryForObject("SELECT COUNT(*) FROM dbo.Company_Operating_Hours",Integer.class);return count!=null&&count>0;}

    public Optional<LocalDate> exactVersionDate(LocalDate date) {
        List<LocalDate> dates=jdbc.query("SELECT DISTINCT effective_from FROM dbo.Company_Operating_Hours WHERE effective_from=?",(rs,row)->rs.getObject(1,LocalDate.class),date);
        return dates.stream().findFirst();
    }

    public List<Long> lockCompanySettings() {
        return jdbc.query("SELECT company_id FROM dbo.Company_Settings WITH (UPDLOCK,HOLDLOCK,INDEX(0)) ORDER BY company_id",(rs,row)->rs.getLong(1));
    }

    public void saveLocation(String address, BigDecimal latitude, BigDecimal longitude, BigDecimal radiusKm) {
        List<Long> ids=lockCompanySettings(); if(ids.size()>1) throw new IllegalStateException("Phát hiện nhiều hơn một cấu hình công ty.");
        if(ids.isEmpty()) jdbc.update("INSERT INTO dbo.Company_Settings(company_address,latitude,longitude,service_radius_km) VALUES(?,?,?,?)",address,latitude,longitude,radiusKm);
        else jdbc.update("UPDATE dbo.Company_Settings SET company_address=?,latitude=?,longitude=?,service_radius_km=? WHERE company_id=?",address,latitude,longitude,radiusKm,ids.getFirst());
    }

    public List<Long> bookingsInVersionInterval(LocalDate from, LocalDate until) {
        String upper=until==null?"":" AND start_time < CAST(? AS DATETIME2)";
        List<Object> args=new ArrayList<>(); args.add(from.atStartOfDay()); if(until!=null) args.add(until.atStartOfDay());
        return jdbc.query("SELECT booking_id FROM dbo.Bookings WHERE status IN ('CONFIRMED','AWAITING_PAYMENT') AND start_time >= CAST(? AS DATETIME2)"+upper+" ORDER BY start_time",(rs,row)->rs.getLong(1),args.toArray());
    }

    public void saveSchedule(LocalDate effectiveFrom,List<OperatingHoursForm> hours) {
        long companyId=requireCompanyId();
        jdbc.update("DELETE FROM dbo.Company_Operating_Hours WHERE company_id=? AND effective_from=?",companyId,effectiveFrom);
        insertHours(companyId,effectiveFrom,hours);
        deactivateOutOfHoursAvailability(effectiveFrom,nextVersionDate(effectiveFrom),hours);
    }

    public void cancelSchedule(LocalDate effectiveFrom) {
        long companyId=requireCompanyId(); LocalDate next=nextVersionDate(effectiveFrom);
        jdbc.update("DELETE FROM dbo.Company_Operating_Hours WHERE company_id=? AND effective_from=?",companyId,effectiveFrom);
        deactivateOutOfHoursAvailability(effectiveFrom,next,null);
    }

    public LocalDate nextVersionDate(LocalDate date) {
        List<LocalDate> values=jdbc.query("SELECT MIN(effective_from) FROM dbo.Company_Operating_Hours WHERE effective_from>?",(rs,row)->rs.getObject(1,LocalDate.class),date);
        return values.isEmpty()?null:values.getFirst();
    }

    private void insertHours(long companyId,LocalDate date,List<OperatingHoursForm> hours) {
        java.util.Map<Integer,Short> periods=new java.util.HashMap<>();
        for(OperatingHoursForm h:hours) {
            short no=(short)periods.merge(h.dayOfWeek(),(short)1,(a,b)->(short)(a+b));
            jdbc.update("INSERT INTO dbo.Company_Operating_Hours(company_id,effective_from,day_of_week,period_no,is_closed,opening_time,closing_time) VALUES(?,?,?,?,?,?,?)",companyId,date,h.dayOfWeek(),no,h.closed(),h.closed()?null:Time.valueOf(h.openingTime()),h.closed()?null:Time.valueOf(h.closingTime()));
        }
    }

    private void deactivateOutOfHoursAvailability(LocalDate from,LocalDate until,List<OperatingHoursForm> proposed) {
        String end=until==null?"":" AND work_date<?";
        List<AvailabilityPeriod> active=jdbc.query("SELECT availability_id,work_date,start_time,end_time FROM dbo.Staff_Availability WHERE is_active=1 AND work_date>=?"+end,(rs,row)->new AvailabilityPeriod(rs.getLong(1),rs.getObject(2,LocalDate.class),rs.getTime(3).toLocalTime(),rs.getTime(4).toLocalTime()),until==null?new Object[]{from}:new Object[]{from,until});
        for(AvailabilityPeriod a:active) {
            List<OperatingHoursForm> windows=proposed!=null?proposed:findOperatingHoursForDate(a.date()); int day=a.date().getDayOfWeek().getValue();
            boolean contained=windows.stream().anyMatch(h->h.dayOfWeek()==day&&!h.closed()&&h.openingTime()!=null&&h.closingTime()!=null&&!a.start().isBefore(h.openingTime())&&!a.end().isAfter(h.closingTime()));
            if(!contained) jdbc.update("UPDATE dbo.Staff_Availability SET is_active=0 WHERE availability_id=?",a.id());
        }
    }
    private long requireCompanyId(){List<Long> ids=lockCompanySettings();if(ids.size()!=1)throw new IllegalStateException("Cần cấu hình chính xác một công ty.");return ids.getFirst();}
    public List<Long> conflictingFutureConfirmedBookings(BigDecimal latitude, BigDecimal longitude, BigDecimal radiusKm) {
        List<Long> ids=lockCompanySettings();if(ids.isEmpty())return List.of();
        List<Long> invalid=new ArrayList<>();
        List<BookingLocation> bookings=jdbc.query("SELECT booking_id,latitude_snapshot,longitude_snapshot FROM dbo.Bookings WHERE status IN ('CONFIRMED','AWAITING_PAYMENT') AND start_time>SYSDATETIME()",(rs,row)->new BookingLocation(rs.getLong(1),rs.getBigDecimal(2),rs.getBigDecimal(3)));
        for(BookingLocation b:bookings) if(distanceKm(b.lat(),b.lon(),latitude,longitude)>radiusKm.doubleValue())invalid.add(b.id());
        return invalid;
    }
    private static double distanceKm(BigDecimal a,BigDecimal b,BigDecimal c,BigDecimal d){if(a==null||b==null)return Double.POSITIVE_INFINITY;double p1=Math.toRadians(a.doubleValue()),p2=Math.toRadians(c.doubleValue()),dp=p2-p1,dl=Math.toRadians(d.doubleValue()-b.doubleValue());return 12742.0176*Math.asin(Math.sqrt(Math.min(1,Math.pow(Math.sin(dp/2),2)+Math.cos(p1)*Math.cos(p2)*Math.pow(Math.sin(dl/2),2))));}
    private record AvailabilityPeriod(long id,LocalDate date,LocalTime start,LocalTime end){}
    private record BookingLocation(long id,BigDecimal lat,BigDecimal lon){}
    private record CompanyLocation(long id,String address,BigDecimal latitude,BigDecimal longitude,BigDecimal radius){}

}
