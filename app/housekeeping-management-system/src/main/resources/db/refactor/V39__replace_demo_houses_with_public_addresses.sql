SET XACT_ABORT ON;

-- Replace the demo Customer accounts' placeholder/duplicate houses with consistent,
-- publicly listed Ho Chi Minh City locations. Delete unreferenced old House rows;
-- keep referenced rows soft-deleted so Bookings and their address snapshots remain intact.
IF UPPER(DB_NAME()) = N'HOME_CLEANING_DEMO'
BEGIN
    IF (SELECT COUNT(*) FROM dbo.Company_Settings) <> 1
        THROW 51390, 'Demo database must have exactly one company setting before replacing demo addresses.', 1;

    -- Replace only the original demo placeholder. An Admin-edited center is preserved.
    UPDATE dbo.Company_Settings
    SET company_address = N'43-45-47 Nguyễn Thị Minh Khai, Phường Sài Gòn, Thành phố Hồ Chí Minh',
        latitude = 10.7835000,
        longitude = 106.6998000
    WHERE company_address = N'Địa điểm mô phỏng, Quận 1, TP. Hồ Chí Minh';

    DECLARE @homes TABLE (
        email NVARCHAR(255) COLLATE Latin1_General_100_CI_AS NOT NULL PRIMARY KEY,
        house_name NVARCHAR(100) NOT NULL,
        address_detail NVARCHAR(500) NOT NULL,
        ward_commune NVARCHAR(100) NOT NULL,
        latitude DECIMAL(10,7) NOT NULL,
        longitude DECIMAL(10,7) NOT NULL
    );

    INSERT INTO @homes (email, house_name, address_detail, ward_commune, latitude, longitude)
    VALUES
        (N'customer@demo.gonnha.local', N'Địa điểm demo', N'13-15 Nguyễn Thái Học', N'Phường Cầu Ông Lãnh', 10.7665000, 106.6932000),
        (N'customer2@demo.gonnha.local', N'Địa điểm demo', N'43-45-47 Nguyễn Thị Minh Khai', N'Phường Sài Gòn', 10.7835000, 106.6998000),
        (N'customer3@demo.gonnha.local', N'Địa điểm demo', N'18 Trần Quang Khải', N'Phường Tân Định', 10.8001000, 106.6910000),
        (N'customer4@demo.gonnha.local', N'Địa điểm demo', N'70-72 Võ Văn Tần', N'Phường Xuân Hòa', 10.7785000, 106.6899000),
        (N'customer5@demo.gonnha.local', N'Địa điểm demo', N'69 Nguyễn Thái Học', N'Phường Cầu Ông Lãnh', 10.7663000, 106.6934000),
        (N'customer6@demo.gonnha.local', N'Địa điểm demo', N'208 Nguyễn Hữu Cảnh', N'Phường Thạnh Mỹ Tây', 10.7927000, 106.7223000);

    IF EXISTS (
        SELECT 1 FROM @homes fixture
        LEFT JOIN dbo.Users customer ON customer.email = fixture.email AND customer.role = 'CUSTOMER'
        WHERE customer.user_id IS NULL
    )
        THROW 51391, 'Expected demo Customer accounts are missing; no demo addresses were changed.', 1;

    DECLARE @service_radius_km DECIMAL(7,2);
    SELECT @service_radius_km = service_radius_km FROM dbo.Company_Settings;
    IF @service_radius_km IS NULL OR @service_radius_km <= 0
        THROW 51392, 'Demo company service radius must be configured before locating demo addresses.', 1;

    IF EXISTS (
        SELECT 1
        FROM @homes fixture
        CROSS JOIN dbo.Company_Settings company
        WHERE geography::Point(company.latitude, company.longitude, 4326).STDistance(
                  geography::Point(fixture.latitude, fixture.longitude, 4326)) / 1000.0 > @service_radius_km
    )
        THROW 51393, 'A public demo address falls outside the configured company service radius.', 1;

    -- Preserve only House rows referenced by Bookings. Remove stale address and
    -- recipient details from all unreferenced House rows for these demo accounts.
    UPDATE house
    SET house.is_deleted = 1,
        house.is_default = 0
    FROM dbo.Houses house
    JOIN dbo.Users customer ON customer.user_id = house.customer_id
    JOIN @homes fixture ON fixture.email = customer.email
    WHERE EXISTS (SELECT 1 FROM dbo.Bookings booking WHERE booking.house_id = house.house_id);

    DELETE house
    FROM dbo.Houses house
    JOIN dbo.Users customer ON customer.user_id = house.customer_id
    JOIN @homes fixture ON fixture.email = customer.email
    WHERE NOT EXISTS (SELECT 1 FROM dbo.Bookings booking WHERE booking.house_id = house.house_id);

    INSERT INTO dbo.Houses (
        customer_id, house_name, address, latitude, longitude, address_note, is_deleted,
        recipient_name, recipient_phone, province_city, ward_commune, address_detail, address_type, is_default
    )
    SELECT customer.user_id,
        fixture.house_name,
        fixture.address_detail + N', ' + fixture.ward_commune + N', Thành phố Hồ Chí Minh',
        fixture.latitude,
        fixture.longitude,
        N'Địa điểm công khai dùng làm dữ liệu demo; không phải địa chỉ cư trú của tài khoản.',
        0,
        customer.full_name,
        customer.phone,
        N'Thành phố Hồ Chí Minh',
        fixture.ward_commune,
        fixture.address_detail,
        'HOME',
        1
    FROM @homes fixture
    JOIN dbo.Users customer ON customer.email = fixture.email AND customer.role = 'CUSTOMER';
END;
