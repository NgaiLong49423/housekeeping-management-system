SET XACT_ABORT ON;

ALTER TABLE dbo.Bookings
    ADD work_started_at DATETIME2(0) NULL,
        incident_expected_arrival_at DATETIME2(0) NULL,
        incident_customer_response VARCHAR(10) NULL,
        incident_customer_responded_at DATETIME2(0) NULL,
        delay_minutes INT NULL,
        delay_discount_percent DECIMAL(5,2) NOT NULL
            CONSTRAINT DF_Bookings_Delay_Discount_Percent DEFAULT (0),
        delay_discount_amount DECIMAL(12,2) NOT NULL
            CONSTRAINT DF_Bookings_Delay_Discount_Amount DEFAULT (0);

EXEC sys.sp_executesql N'
    ALTER TABLE dbo.Bookings ADD CONSTRAINT CK_Bookings_Incident_Customer_Response
        CHECK ((incident_customer_response IS NULL AND incident_customer_responded_at IS NULL)
            OR (incident_customer_response IN (''ACCEPTED'', ''DECLINED'') AND incident_customer_responded_at IS NOT NULL));
    ALTER TABLE dbo.Bookings ADD CONSTRAINT CK_Bookings_Incident_Response_Requires_Report
        CHECK (incident_customer_response IS NULL OR (incident_reported_at IS NOT NULL AND incident_note IS NOT NULL));
    ALTER TABLE dbo.Bookings ADD CONSTRAINT CK_Bookings_Delay_Minutes
        CHECK (delay_minutes IS NULL OR delay_minutes >= 0);
    ALTER TABLE dbo.Bookings ADD CONSTRAINT CK_Bookings_Delay_Discount_Percent
        CHECK (delay_discount_percent IN (0, 5, 10, 20));
    ALTER TABLE dbo.Bookings ADD CONSTRAINT CK_Bookings_Delay_Discount_Amount
        CHECK (delay_discount_amount >= 0 AND delay_discount_amount <= total_amount);
    ALTER TABLE dbo.Bookings ADD CONSTRAINT CK_Bookings_Work_Start
        CHECK (work_started_at IS NULL OR (staff_arrived_at IS NOT NULL AND work_started_at >= start_time));
';
