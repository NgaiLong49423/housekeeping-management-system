SET XACT_ABORT ON;

UPDATE dbo.Booking_Delay_Compensation_Tiers
SET discount_percent = 30
WHERE discount_percent > 30;
