SET XACT_ABORT ON;

CREATE TABLE dbo.Booking_Delay_Compensation_Tiers (
    tier_id INT IDENTITY(1,1) NOT NULL CONSTRAINT PK_Booking_Delay_Compensation_Tiers PRIMARY KEY,
    min_delay_minutes INT NOT NULL,
    max_delay_minutes INT NULL,
    discount_percent INT NOT NULL,
    CONSTRAINT UQ_Booking_Delay_Compensation_Tiers_Min UNIQUE (min_delay_minutes),
    CONSTRAINT CK_Booking_Delay_Compensation_Tiers_Range CHECK (
        min_delay_minutes >= 1 AND (max_delay_minutes IS NULL OR max_delay_minutes >= min_delay_minutes)
    ),
    CONSTRAINT CK_Booking_Delay_Compensation_Tiers_Percent CHECK (discount_percent BETWEEN 0 AND 100)
);

CREATE UNIQUE INDEX UX_Booking_Delay_Compensation_Tiers_OpenEnded
    ON dbo.Booking_Delay_Compensation_Tiers(max_delay_minutes)
    WHERE max_delay_minutes IS NULL;

INSERT INTO dbo.Booking_Delay_Compensation_Tiers(min_delay_minutes, max_delay_minutes, discount_percent)
VALUES (1, 15, 5), (16, 30, 10), (31, NULL, 20);

ALTER TABLE dbo.Bookings DROP CONSTRAINT CK_Bookings_Delay_Discount_Percent;
ALTER TABLE dbo.Bookings ADD CONSTRAINT CK_Bookings_Delay_Discount_Percent
    CHECK (delay_discount_percent BETWEEN 0 AND 100);
