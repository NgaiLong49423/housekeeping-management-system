SET XACT_ABORT ON;

-- Make the original demo customer's untouched placeholder house usable in the booking flow.
-- This migration is isolated to home_cleaning_demo and never overwrites an edited address.
IF UPPER(DB_NAME()) = N'HOME_CLEANING_DEMO'
BEGIN
    IF (SELECT COUNT(*) FROM dbo.Company_Settings) <> 1
        THROW 51380, 'Demo database must have exactly one company setting to locate the demo house.', 1;

    DECLARE @company_latitude DECIMAL(10,7);
    DECLARE @company_longitude DECIMAL(10,7);
    DECLARE @service_radius_km DECIMAL(7,2);

    SELECT @company_latitude = latitude,
           @company_longitude = longitude,
           @service_radius_km = service_radius_km
    FROM dbo.Company_Settings;

    IF @company_latitude IS NULL OR @company_longitude IS NULL
       OR @service_radius_km IS NULL OR @service_radius_km <= 0
        THROW 51381, 'Demo company location and service radius must be configured before locating the demo house.', 1;

    DECLARE @offset_scale DECIMAL(10,7) =
        CASE WHEN @service_radius_km >= 5 THEN 1
             ELSE @service_radius_km / 5 END;

    UPDATE house
    SET address = N'12/3 Nguyễn Trãi, Phường Bến Nghé, Thành phố Hồ Chí Minh',
        latitude = CAST(@company_latitude + 0.0060000 * @offset_scale AS DECIMAL(10,7)),
        longitude = CAST(@company_longitude - 0.0040000 * @offset_scale AS DECIMAL(10,7)),
        province_city = N'Thành phố Hồ Chí Minh',
        ward_commune = N'Phường Bến Nghé',
        address_detail = N'12/3 Nguyễn Trãi',
        address_note = N'Địa chỉ mô phỏng chỉ dùng cho môi trường demo.'
    FROM dbo.Houses house
    JOIN dbo.Users customer ON customer.user_id = house.customer_id
    WHERE customer.email = N'customer@demo.gonnha.local'
      AND customer.role = 'CUSTOMER'
      AND house.is_default = 1
      AND house.is_deleted = 0
      AND house.address_detail = N'Địa chỉ mẫu, Quận 1'
      AND house.latitude IS NULL
      AND house.longitude IS NULL;
END;
