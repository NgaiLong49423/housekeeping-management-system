-- One isolated booking to exercise the customer/staff arrival-confirmation flow.
SET XACT_ABORT ON;

IF UPPER(DB_NAME()) <> N'HOME_CLEANING_DEMO'
    THROW 51120, 'Refusing to insert the arrival demo booking outside home_cleaning_demo.', 1;

DECLARE @customer_id BIGINT;
DECLARE @staff_id BIGINT;
DECLARE @house_id BIGINT;
DECLARE @service_id BIGINT;
DECLARE @service_name NVARCHAR(150);
DECLARE @unit_price DECIMAL(12,2);
DECLARE @duration_minutes INT;

SELECT @customer_id = user_id FROM dbo.Users WHERE email=N'customer@demo.gonnha.local' AND role='CUSTOMER';
SELECT @staff_id = staff.staff_id FROM dbo.Staff staff
JOIN dbo.Users account ON account.user_id=staff.user_id
WHERE account.email=N'staff@demo.gonnha.local' AND account.role='STAFF';
SELECT TOP (1) @house_id = house_id FROM dbo.Houses
WHERE customer_id=@customer_id AND is_deleted=0 ORDER BY is_default DESC, house_id;
SELECT TOP (1) @service_id=service_id, @service_name=service_name,
       @unit_price=price, @duration_minutes=duration_minutes
FROM dbo.Services WHERE service_name=N'Dọn dẹp nhà cơ bản' AND status='ACTIVE'
ORDER BY service_id;

IF @customer_id IS NULL OR @staff_id IS NULL OR @house_id IS NULL OR @service_id IS NULL
    THROW 51121, 'Required demo customer, staff, house, or active service is missing.', 1;

IF NOT EXISTS (
    SELECT 1 FROM dbo.Bookings b
    JOIN dbo.Booking_Details d ON d.booking_id=b.booking_id
    WHERE b.customer_id=@customer_id AND b.staff_id=@staff_id
      AND d.service_id=@service_id AND b.start_time>SYSDATETIME()
      AND b.created_at>=DATEADD(MINUTE, -5, SYSDATETIME())
)
BEGIN
    DECLARE @start_time DATETIME2(0) = DATEADD(MINUTE, 10,
        CAST(SYSDATETIMEOFFSET() AT TIME ZONE 'SE Asia Standard Time' AS DATETIME2(0)));
    DECLARE @booking_ids TABLE (booking_id BIGINT NOT NULL);

    INSERT INTO dbo.Bookings (
        customer_id, staff_id, original_staff_id, house_id, address_snapshot,
        latitude_snapshot, longitude_snapshot, address_note_snapshot,
        start_time, end_time, total_duration_minutes, total_amount, status
    )
    OUTPUT inserted.booking_id INTO @booking_ids(booking_id)
    SELECT @customer_id, @staff_id, @staff_id, house.house_id, house.address,
           COALESCE(house.latitude, company.latitude, CAST(10.7769000 AS DECIMAL(10,7))),
           COALESCE(house.longitude, company.longitude, CAST(106.7009000 AS DECIMAL(10,7))),
           house.address_note, @start_time, DATEADD(MINUTE, @duration_minutes, @start_time),
           @duration_minutes, @unit_price, 'CONFIRMED'
    FROM dbo.Houses house
    CROSS JOIN (SELECT TOP (1) latitude, longitude FROM dbo.Company_Settings ORDER BY company_id) company
    WHERE house.house_id=@house_id;

    INSERT INTO dbo.Booking_Details (booking_id, service_id, service_name_snapshot, unit_price, duration_minutes)
    SELECT booking_id, @service_id, @service_name, @unit_price, @duration_minutes
    FROM @booking_ids;
END;
