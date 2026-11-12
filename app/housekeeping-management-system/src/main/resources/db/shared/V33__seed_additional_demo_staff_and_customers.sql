SET XACT_ABORT ON;

-- Five extra Staff and five extra Customer accounts for the isolated demo database only.
-- Every account uses the initial demo password Demo@12345 (same bcrypt hash as V3).
-- Re-running is safe: rows are inserted only when the email/staff/address does not exist yet.
IF UPPER(DB_NAME()) = N'HOME_CLEANING_DEMO'
BEGIN
    DECLARE @demo_password_hash NVARCHAR(255) =
        N'{bcrypt}$2a$10$J9pdp//ZJo3yTljIx9WD6O.Zt.4WhoDHld3p/uzDzGWDiBKe0Da.i';

    DECLARE @accounts TABLE (
        email NVARCHAR(255) COLLATE Latin1_General_100_CI_AS NOT NULL PRIMARY KEY,
        full_name NVARCHAR(150) NOT NULL,
        phone NVARCHAR(20) NOT NULL,
        role VARCHAR(20) NOT NULL
    );

    INSERT INTO @accounts (email, full_name, phone, role)
    VALUES
        (N'staff3@demo.gonnha.local', N'Nguyễn Thị Mai Lan', N'0912401003', 'STAFF'),
        (N'staff4@demo.gonnha.local', N'Trần Văn Khoa', N'0912401004', 'STAFF'),
        (N'staff5@demo.gonnha.local', N'Lê Thị Bích Ngọc', N'0912401005', 'STAFF'),
        (N'staff6@demo.gonnha.local', N'Hoàng Minh Tuấn', N'0912401006', 'STAFF'),
        (N'staff7@demo.gonnha.local', N'Phan Thị Thanh Thảo', N'0912401007', 'STAFF'),
        (N'customer2@demo.gonnha.local', N'Đỗ Ngọc Hân', N'0938502002', 'CUSTOMER'),
        (N'customer3@demo.gonnha.local', N'Bùi Quang Huy', N'0938502003', 'CUSTOMER'),
        (N'customer4@demo.gonnha.local', N'Ngô Thị Kim Oanh', N'0938502004', 'CUSTOMER'),
        (N'customer5@demo.gonnha.local', N'Vũ Đức Thắng', N'0938502005', 'CUSTOMER'),
        (N'customer6@demo.gonnha.local', N'Lý Gia Hân', N'0938502006', 'CUSTOMER');

    IF EXISTS (
        SELECT 1 FROM dbo.Users existing
        JOIN @accounts fixture ON fixture.email = existing.email
        WHERE existing.role <> fixture.role
    )
        THROW 51320, 'A demo email already exists with a different role; no extra demo accounts were inserted.', 1;

    IF EXISTS (
        SELECT 1 FROM dbo.Users existing
        JOIN @accounts fixture ON fixture.phone = existing.phone
        WHERE existing.email <> fixture.email
    )
        THROW 51321, 'A demo phone number already belongs to another account; no extra demo accounts were inserted.', 1;

    IF (SELECT COUNT(*) FROM dbo.Company_Settings) <> 1
        THROW 51322, 'Demo database must have exactly one company setting to place demo addresses.', 1;

    INSERT INTO dbo.Users (full_name, email, password_hash, phone, role, status)
    SELECT fixture.full_name, fixture.email, @demo_password_hash, fixture.phone, fixture.role, 'ACTIVE'
    FROM @accounts fixture
    WHERE NOT EXISTS (SELECT 1 FROM dbo.Users existing WHERE existing.email = fixture.email);

    DECLARE @staff TABLE (
        email NVARCHAR(255) COLLATE Latin1_General_100_CI_AS NOT NULL PRIMARY KEY,
        staff_type VARCHAR(20) NOT NULL,
        bio NVARCHAR(500) NOT NULL
    );

    INSERT INTO @staff (email, staff_type, bio)
    VALUES
        (N'staff3@demo.gonnha.local', 'FULL_TIME',
            N'5 năm kinh nghiệm dọn dẹp căn hộ, cẩn thận với đồ gỗ và mặt kính.'),
        (N'staff4@demo.gonnha.local', 'PART_TIME',
            N'Chuyên vệ sinh nhà bếp và thiết bị gia dụng, nhận ca tối và cuối tuần.'),
        (N'staff5@demo.gonnha.local', 'FULL_TIME',
            N'Thành thạo tổng vệ sinh nhà phố, sắp xếp tủ quần áo gọn gàng.'),
        (N'staff6@demo.gonnha.local', 'PART_TIME',
            N'Nhanh nhẹn, đúng giờ, quen dọn dẹp phòng trọ và căn hộ nhỏ.'),
        (N'staff7@demo.gonnha.local', 'FULL_TIME',
            N'Có kinh nghiệm với nhà có trẻ nhỏ và thú cưng, ưu tiên dung dịch an toàn.');

    INSERT INTO dbo.Staff (user_id, staff_type)
    SELECT account.user_id, fixture.staff_type
    FROM @staff fixture
    JOIN dbo.Users account ON account.email = fixture.email AND account.role = 'STAFF'
    WHERE NOT EXISTS (SELECT 1 FROM dbo.Staff existing WHERE existing.user_id = account.user_id);

    INSERT INTO dbo.Staff_Profiles (staff_id, avatar_url, bio)
    SELECT staff.staff_id, NULL, fixture.bio
    FROM @staff fixture
    JOIN dbo.Users account ON account.email = fixture.email AND account.role = 'STAFF'
    JOIN dbo.Staff staff ON staff.user_id = account.user_id
    WHERE NOT EXISTS (SELECT 1 FROM dbo.Staff_Profiles existing WHERE existing.staff_id = staff.staff_id);

    -- Addresses are placed a few kilometres from the company location (as V7 does with
    -- Company_Settings), scaled down when the service radius is small so every home stays inside it.
    DECLARE @homes TABLE (
        email NVARCHAR(255) COLLATE Latin1_General_100_CI_AS NOT NULL PRIMARY KEY,
        house_name NVARCHAR(100) NOT NULL,
        address_detail NVARCHAR(500) NOT NULL,
        ward_commune NVARCHAR(100) NOT NULL,
        address_note NVARCHAR(500) NULL,
        latitude_offset DECIMAL(10,7) NOT NULL,
        longitude_offset DECIMAL(10,7) NOT NULL
    );

    INSERT INTO @homes (email, house_name, address_detail, ward_commune, address_note, latitude_offset, longitude_offset)
    VALUES
        (N'customer2@demo.gonnha.local', N'Căn hộ', N'Căn hộ 12.05, 45 Nguyễn Thị Minh Khai',
            N'Phường Bến Thành', N'Gửi xe tầng hầm B1.', 0.0060000, -0.0080000),
        (N'customer3@demo.gonnha.local', N'Nhà riêng', N'18 Trần Quang Khải',
            N'Phường Tân Định', NULL, 0.0180000, -0.0020000),
        (N'customer4@demo.gonnha.local', N'Nhà riêng', N'72 Võ Văn Tần',
            N'Phường Xuân Hòa', N'Nhà có chó nhỏ, vui lòng bấm chuông.', 0.0110000, -0.0140000),
        (N'customer5@demo.gonnha.local', N'Nhà riêng', N'9 Nguyễn Thái Học',
            N'Phường Cầu Ông Lãnh', NULL, -0.0050000, -0.0060000),
        (N'customer6@demo.gonnha.local', N'Căn hộ', N'Căn hộ 7.12, 208 Nguyễn Hữu Cảnh',
            N'Phường Thạnh Mỹ Tây', N'Liên hệ lễ tân để lên tầng.', 0.0150000, 0.0100000);

    INSERT INTO dbo.Houses (
        customer_id, house_name, address, latitude, longitude, address_note, is_deleted,
        recipient_name, recipient_phone, province_city, ward_commune, address_detail, address_type, is_default
    )
    SELECT customer.user_id, fixture.house_name,
        fixture.address_detail + N', ' + fixture.ward_commune + N', Thành phố Hồ Chí Minh',
        CAST(company.latitude + fixture.latitude_offset * company.offset_scale AS DECIMAL(10,7)),
        CAST(company.longitude + fixture.longitude_offset * company.offset_scale AS DECIMAL(10,7)),
        fixture.address_note, 0, customer.full_name, customer.phone, N'Thành phố Hồ Chí Minh',
        fixture.ward_commune, fixture.address_detail, 'HOME', 1
    FROM @homes fixture
    JOIN dbo.Users customer ON customer.email = fixture.email AND customer.role = 'CUSTOMER'
    CROSS JOIN (
        -- The largest offset is about 2.5 km; keep it under half of the configured radius.
        SELECT TOP (1) latitude, longitude,
            CASE WHEN service_radius_km >= 5 THEN CAST(1 AS DECIMAL(10,7))
                 ELSE CAST(service_radius_km / 5 AS DECIMAL(10,7)) END AS offset_scale
        FROM dbo.Company_Settings
        ORDER BY company_id
    ) company
    WHERE NOT EXISTS (
        SELECT 1 FROM dbo.Houses existing
        WHERE existing.customer_id = customer.user_id AND existing.is_deleted = 0
    );
END;
