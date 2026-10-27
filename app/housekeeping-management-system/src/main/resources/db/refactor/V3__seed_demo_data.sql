-- Optional demo fixtures belong only to the isolated SQL Server demo database.
SET XACT_ABORT ON;

IF UPPER(DB_NAME()) <> N'HOME_CLEANING_DEMO'
    THROW 51110, 'Refusing to insert demo fixtures outside home_cleaning_demo.', 1;

IF EXISTS (
    SELECT 1
    FROM dbo.Users
    WHERE email IN (
        N'customer@demo.gonnha.local', N'staff@demo.gonnha.local',
        N'staff.fulltime@demo.gonnha.local', N'admin@demo.gonnha.local'
    )
    AND NOT (
        (email = N'customer@demo.gonnha.local' AND role = 'CUSTOMER') OR
        (email IN (N'staff@demo.gonnha.local', N'staff.fulltime@demo.gonnha.local') AND role = 'STAFF') OR
        (email = N'admin@demo.gonnha.local' AND role = 'ADMIN')
    )
)
    THROW 51111, 'A demo email already exists with a different role; no demo fixtures were inserted.', 1;

INSERT INTO dbo.Users (full_name, email, password_hash, phone, role, status)
SELECT fixture.full_name, fixture.email, fixture.password_hash, fixture.phone, fixture.role, 'ACTIVE'
FROM (VALUES
    (N'Khách hàng Demo', N'customer@demo.gonnha.local', N'{bcrypt}$2a$10$J9pdp//ZJo3yTljIx9WD6O.Zt.4WhoDHld3p/uzDzGWDiBKe0Da.i', N'0900000001', 'CUSTOMER'),
    (N'Nhân viên bán thời gian Demo', N'staff@demo.gonnha.local', N'{bcrypt}$2a$10$J9pdp//ZJo3yTljIx9WD6O.Zt.4WhoDHld3p/uzDzGWDiBKe0Da.i', N'0900000002', 'STAFF'),
    (N'Nhân viên toàn thời gian Demo', N'staff.fulltime@demo.gonnha.local', N'{bcrypt}$2a$10$J9pdp//ZJo3yTljIx9WD6O.Zt.4WhoDHld3p/uzDzGWDiBKe0Da.i', N'0900000003', 'STAFF'),
    (N'Quản trị viên Demo', N'admin@demo.gonnha.local', N'{bcrypt}$2a$10$J9pdp//ZJo3yTljIx9WD6O.Zt.4WhoDHld3p/uzDzGWDiBKe0Da.i', N'0900000004', 'ADMIN')
) AS fixture(full_name, email, password_hash, phone, role)
WHERE NOT EXISTS (SELECT 1 FROM dbo.Users existing WHERE existing.email = fixture.email);

INSERT INTO dbo.Staff (user_id, staff_type)
SELECT account.user_id, fixture.staff_type
FROM (VALUES
    (N'staff@demo.gonnha.local', 'PART_TIME'),
    (N'staff.fulltime@demo.gonnha.local', 'FULL_TIME')
) AS fixture(email, staff_type)
JOIN dbo.Users account ON account.email = fixture.email AND account.role = 'STAFF'
WHERE NOT EXISTS (SELECT 1 FROM dbo.Staff existing WHERE existing.user_id = account.user_id);

IF (SELECT COUNT(*) FROM dbo.Company_Settings) > 1
    THROW 51112, 'Demo database has multiple company settings; no demo fixtures were inserted.', 1;

IF NOT EXISTS (SELECT 1 FROM dbo.Company_Settings)
    INSERT INTO dbo.Company_Settings (company_address, latitude, longitude, service_radius_km)
    VALUES (N'Địa điểm mô phỏng, Quận 1, TP. Hồ Chí Minh', 10.7769000, 106.7009000, 25.00);

INSERT INTO dbo.Company_Operating_Hours (company_id, day_of_week, opening_time, closing_time, is_closed)
SELECT company.company_id, days.day_of_week, '08:00', '18:00', 0
FROM dbo.Company_Settings company
CROSS JOIN (VALUES (1), (2), (3), (4), (5), (6), (7)) AS days(day_of_week)
WHERE NOT EXISTS (
    SELECT 1 FROM dbo.Company_Operating_Hours existing
    WHERE existing.company_id = company.company_id AND existing.day_of_week = days.day_of_week
);

INSERT INTO dbo.Services (service_name, description, price, duration_minutes, status)
SELECT fixture.service_name, fixture.description, fixture.price, fixture.duration_minutes, 'ACTIVE'
FROM (VALUES
    (N'Dọn dẹp nhà cơ bản', N'Lau dọn các khu vực sinh hoạt chính.', CAST(150000.00 AS DECIMAL(12,2)), 120),
    (N'Tổng vệ sinh', N'Làm sạch kỹ các phòng và bề mặt trong nhà.', CAST(300000.00 AS DECIMAL(12,2)), 240),
    (N'Vệ sinh nhà bếp', N'Làm sạch mặt bếp, bồn rửa và khu vực nấu ăn.', CAST(120000.00 AS DECIMAL(12,2)), 90)
) AS fixture(service_name, description, price, duration_minutes)
WHERE NOT EXISTS (SELECT 1 FROM dbo.Services existing WHERE existing.service_name = fixture.service_name);

INSERT INTO dbo.Houses (
    customer_id, house_name, address, latitude, longitude, address_note, is_deleted,
    recipient_name, recipient_phone, province_city, ward_commune, address_detail, address_type, is_default
)
SELECT customer.user_id, N'Nhà riêng', N'Địa chỉ mẫu, Quận 1, TP. Hồ Chí Minh', NULL, NULL,
    NULL, 0, customer.full_name, customer.phone, N'TP. Hồ Chí Minh', N'Phường Bến Nghé',
    N'Địa chỉ mẫu, Quận 1', 'HOME', 1
FROM dbo.Users customer
WHERE customer.email = N'customer@demo.gonnha.local' AND customer.role = 'CUSTOMER'
  AND NOT EXISTS (SELECT 1 FROM dbo.Houses existing WHERE existing.customer_id = customer.user_id AND existing.is_deleted = 0);

INSERT INTO dbo.Staff_Availability (staff_id, work_date, start_time, end_time)
SELECT staff.staff_id,
    DATEADD(day, 1, CONVERT(date, SYSDATETIMEOFFSET() AT TIME ZONE 'SE Asia Standard Time')),
    '08:00', '18:00'
FROM dbo.Staff staff
JOIN dbo.Users account ON account.user_id = staff.user_id
WHERE account.email = N'staff@demo.gonnha.local'
  AND NOT EXISTS (SELECT 1 FROM dbo.Staff_Availability existing WHERE existing.staff_id = staff.staff_id);
