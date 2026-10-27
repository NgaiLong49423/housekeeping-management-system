ALTER TABLE dbo.Houses DROP CONSTRAINT CK_Houses_Latitude, CK_Houses_Longitude;
ALTER TABLE dbo.Bookings DROP CONSTRAINT CK_Bookings_Latitude, CK_Bookings_Longitude;
ALTER TABLE dbo.Houses ALTER COLUMN latitude DECIMAL(10,7) NULL;
ALTER TABLE dbo.Houses ALTER COLUMN longitude DECIMAL(10,7) NULL;
ALTER TABLE dbo.Bookings ALTER COLUMN latitude_snapshot DECIMAL(10,7) NULL;
ALTER TABLE dbo.Bookings ALTER COLUMN longitude_snapshot DECIMAL(10,7) NULL;
ALTER TABLE dbo.Houses ADD
    CONSTRAINT CK_Houses_Latitude CHECK (latitude BETWEEN -90 AND 90),
    CONSTRAINT CK_Houses_Longitude CHECK (longitude BETWEEN -180 AND 180);
ALTER TABLE dbo.Bookings ADD
    CONSTRAINT CK_Bookings_Latitude CHECK (latitude_snapshot BETWEEN -90 AND 90),
    CONSTRAINT CK_Bookings_Longitude CHECK (longitude_snapshot BETWEEN -180 AND 180);
GO

ALTER TABLE dbo.Houses ADD
    recipient_name NVARCHAR(150) NULL,
    recipient_phone NVARCHAR(20) NULL,
    province_city NVARCHAR(100) NULL,
    ward_commune NVARCHAR(100) NULL,
    address_detail NVARCHAR(500) NULL,
    address_type VARCHAR(20) NOT NULL CONSTRAINT DF_Houses_Address_Type DEFAULT ('HOME'),
    is_default BIT NOT NULL CONSTRAINT DF_Houses_Is_Default DEFAULT (0);
GO

UPDATE house
SET recipient_name = customer.full_name,
    recipient_phone = customer.phone,
    address_detail = house.address
FROM dbo.Houses house
JOIN dbo.Users customer ON customer.user_id = house.customer_id;
GO

ALTER TABLE dbo.Houses ALTER COLUMN recipient_name NVARCHAR(150) NOT NULL;
ALTER TABLE dbo.Houses ALTER COLUMN recipient_phone NVARCHAR(20) NOT NULL;
ALTER TABLE dbo.Houses ALTER COLUMN address_detail NVARCHAR(500) NOT NULL;
ALTER TABLE dbo.Houses ADD CONSTRAINT CK_Houses_Address_Type CHECK (address_type IN ('HOME', 'OFFICE'));

CREATE UNIQUE INDEX UX_Houses_One_Default_Per_Customer
    ON dbo.Houses(customer_id)
    WHERE is_default = 1 AND is_deleted = 0;
