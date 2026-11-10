SET XACT_ABORT ON;

ALTER TABLE dbo.Bookings
    ADD staff_arrived_at DATETIME2(0) NULL,
        incident_reported_at DATETIME2(0) NULL,
        incident_note NVARCHAR(500) NULL;

EXEC sys.sp_executesql N'
    ALTER TABLE dbo.Bookings
        ADD CONSTRAINT CK_Bookings_Incident_Report
            CHECK ((incident_reported_at IS NULL AND incident_note IS NULL)
                OR (incident_reported_at IS NOT NULL AND incident_note IS NOT NULL AND LEN(LTRIM(RTRIM(incident_note))) > 0));
';
