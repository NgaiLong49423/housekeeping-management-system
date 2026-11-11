SET XACT_ABORT ON;

ALTER TABLE dbo.Bookings ADD customer_delay_reported_at DATETIME2(0) NULL,
    CONSTRAINT CK_Bookings_Customer_Delay_Report
    CHECK (customer_delay_reported_at IS NULL OR customer_delay_reported_at >= start_time);
