package com.housekeeping.management.demo;

import com.housekeeping.management.service.DuplicateEmailException;
import com.housekeeping.management.service.GlobalEmailUniquenessService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/** Creates optional demo identities in the shared Users table without changing existing accounts. */
@Component
@Profile("demo")
public class DemoAccountSeeder implements ApplicationRunner {
    private static final Logger LOG = LoggerFactory.getLogger(DemoAccountSeeder.class);
    private static final String FIND_ROLE = "SELECT role FROM dbo.Users WHERE email = ?";
    private static final List<DemoAccount> ACCOUNTS = List.of(
            new DemoAccount("CUSTOMER", "customer@demo.gonnha.local", "Khách hàng Demo"),
            new DemoAccount("STAFF", "staff@demo.gonnha.local", "Nhân viên bán thời gian Demo", "PART_TIME"),
            new DemoAccount("STAFF", "staff.fulltime@demo.gonnha.local", "Nhân viên toàn thời gian Demo", "FULL_TIME"),
            new DemoAccount("ADMIN", "admin@demo.gonnha.local", "Quản trị viên Demo"));

    private static final List<DemoService> DEMO_SERVICES = List.of(
            new DemoService("Dọn dẹp nhà cơ bản", "Lau dọn các khu vực sinh hoạt chính.", 120, new BigDecimal("150000.00")),
            new DemoService("Tổng vệ sinh", "Làm sạch kỹ các phòng và bề mặt trong nhà.", 240, new BigDecimal("300000.00")),
            new DemoService("Vệ sinh nhà bếp", "Làm sạch mặt bếp, bồn rửa và khu vực nấu ăn.", 90, new BigDecimal("120000.00")));

    private static final ZoneOffset BUSINESS_OFFSET = ZoneOffset.ofHours(7);

    private final JdbcTemplate jdbc;
    private final GlobalEmailUniquenessService emails;
    private final PasswordEncoder encoder;
    private final String demoPassword;

    public DemoAccountSeeder(JdbcTemplate jdbc, GlobalEmailUniquenessService emails, PasswordEncoder encoder,
            @Value("${app.demo.account-password:}") String demoPassword) {
        this.jdbc = jdbc;
        this.emails = emails;
        this.encoder = encoder;
        this.demoPassword = demoPassword;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (demoPassword == null || demoPassword.isBlank()) {
            LOG.warn("Demo accounts were not seeded because app.demo.account-password is not configured.");
            return;
        }
        LOG.warn("Demo profile is enabled. Use this profile only with a disposable local database.");
        for (DemoAccount account : ACCOUNTS) {
            List<String> existing = roles(account.email());
            if (!existing.isEmpty()) {
                requireExpectedRole(account, existing);
                LOG.info("Keeping existing demo account {} and its password", account.email());
                continue;
            }
            try {
                emails.executeWithUniqueEmail(account.email(), canonical -> {
                    Long userId = jdbc.queryForObject("""
                            INSERT INTO dbo.Users (full_name, email, password_hash, phone, role, status)
                            OUTPUT INSERTED.user_id
                            VALUES (?, ?, ?, ?, ?, 'ACTIVE')
                            """, Long.class, account.fullName(), canonical, encoder.encode(demoPassword),
                            "0000000000", account.role());
                    if ("STAFF".equals(account.role())) {
                        jdbc.update("INSERT INTO dbo.Staff (user_id, staff_type) VALUES (?, ?)", userId, account.staffType());
                    }
                    return null;
                });
                LOG.info("Created demo account {} with role {}", account.email(), account.role());
            } catch (DuplicateEmailException concurrentInsert) {
                requireExpectedRole(account, roles(account.email()));
            }
        }
        seedBookingPrerequisites();
    }

    private void seedBookingPrerequisites() {
        Long customerId = demoUserId("customer@demo.gonnha.local", "CUSTOMER");
        Long partTimeStaffId = demoStaffId("staff@demo.gonnha.local");
        Long companyId = ensureCompanySettings();
        seedCompanyHours(companyId);
        seedServices();
        seedCustomerHouse(customerId, companyId);
        seedPartTimeAvailability(partTimeStaffId);
    }

    private Long ensureCompanySettings() {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM dbo.Company_Settings", Integer.class);
        if (count != null && count > 1) {
            throw new IllegalStateException("Demo database has more than one company configuration; no settings were changed.");
        }
        if (count == null || count == 0) {
            return jdbc.queryForObject("""
                    INSERT INTO dbo.Company_Settings (company_address, latitude, longitude, service_radius_km)
                    OUTPUT INSERTED.company_id VALUES (?, ?, ?, ?)
                    """, Long.class, "Địa điểm mô phỏng, Quận 1, TP. Hồ Chí Minh",
                    new BigDecimal("10.7769000"), new BigDecimal("106.7009000"), new BigDecimal("25.00"));
        }
        return jdbc.queryForObject("SELECT TOP (1) company_id FROM dbo.Company_Settings ORDER BY company_id", Long.class);
    }

    private void seedCompanyHours(Long companyId) {
        for (int day = 1; day <= 7; day++) {
            Integer count = jdbc.queryForObject("""
                    SELECT COUNT(*) FROM dbo.Company_Operating_Hours WHERE company_id=? AND day_of_week=?
                    """, Integer.class, companyId, day);
            if (count == null || count == 0) {
                jdbc.update("""
                        INSERT INTO dbo.Company_Operating_Hours
                            (company_id, day_of_week, opening_time, closing_time, is_closed)
                        VALUES (?, ?, '08:00', '18:00', 0)
                        """, companyId, day);
            }
        }
    }

    private void seedServices() {
        for (DemoService service : DEMO_SERVICES) {
            Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM dbo.Services WHERE service_name=?",
                    Integer.class, service.name());
            if (count == null || count == 0) {
                jdbc.update("""
                        INSERT INTO dbo.Services (service_name, description, price, duration_minutes, status)
                        VALUES (?, ?, ?, ?, 'ACTIVE')
                        """, service.name(), service.description(), service.price(), service.durationMinutes());
            }
        }
    }

    private void seedCustomerHouse(Long customerId, Long companyId) {
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM dbo.Houses WHERE customer_id=? AND is_deleted=0
                """, Integer.class, customerId);
        if (count == null || count == 0) {
            BigDecimal latitude = jdbc.queryForObject("SELECT latitude FROM dbo.Company_Settings WHERE company_id=?", BigDecimal.class, companyId);
            BigDecimal longitude = jdbc.queryForObject("SELECT longitude FROM dbo.Company_Settings WHERE company_id=?", BigDecimal.class, companyId);
            jdbc.update("""
                    INSERT INTO dbo.Houses (customer_id, house_name, address, latitude, longitude,
                        recipient_name, recipient_phone, address_detail, address_type, is_default)
                    SELECT ?, N'Nhà riêng', N'Địa chỉ mô phỏng', ?, ?, full_name, phone,
                        N'Địa chỉ mô phỏng', 'HOME', 1
                    FROM dbo.Users WHERE user_id=?
                    """, customerId, latitude, longitude, customerId);
        }
    }

    private void seedPartTimeAvailability(Long staffId) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM dbo.Staff_Availability WHERE staff_id=?", Integer.class, staffId);
        if (count == null || count == 0) {
            LocalDate firstDemoWorkDate = LocalDate.now(BUSINESS_OFFSET).plusDays(1);
            jdbc.update("""
                    INSERT INTO dbo.Staff_Availability (staff_id, work_date, start_time, end_time)
                    VALUES (?, ?, ?, ?)
                    """, staffId, firstDemoWorkDate, LocalTime.of(8, 0), LocalTime.of(18, 0));
        }
    }

    private Long demoUserId(String email, String expectedRole) {
        return jdbc.queryForObject("SELECT user_id FROM dbo.Users WHERE email=? AND role=?", Long.class, email, expectedRole);
    }

    private Long demoStaffId(String email) {
        return jdbc.queryForObject("""
                SELECT s.staff_id FROM dbo.Staff s JOIN dbo.Users u ON u.user_id=s.user_id
                WHERE u.email=? AND u.role='STAFF'
                """, Long.class, email);
    }

    private List<String> roles(String email) {
        List<String> roles = jdbc.queryForList(FIND_ROLE, String.class, email);
        if (roles.size() == 1 && "STAFF".equals(roles.getFirst())) {
            Integer staffRows = jdbc.queryForObject("SELECT COUNT(*) FROM dbo.Staff s JOIN dbo.Users u ON u.user_id=s.user_id WHERE u.email=?", Integer.class, email);
            if (staffRows == null || staffRows != 1) {
                throw new IllegalStateException("Demo staff identity is missing its Staff record. No account was changed.");
            }
        }
        return roles;
    }

    private void requireExpectedRole(DemoAccount account, List<String> roles) {
        if (roles.size() != 1 || !roles.getFirst().equals(account.role())) {
            throw new IllegalStateException("Demo email has an unexpected or ambiguous role: " + account.email()
                    + ". No existing account was changed.");
        }
    }

    private record DemoAccount(String role, String email, String fullName, String staffType) {
        private DemoAccount(String role, String email, String fullName) { this(role, email, fullName, null); }
    }
    private record DemoService(String name, String description, int durationMinutes, BigDecimal price) { }
}
