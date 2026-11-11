SET XACT_ABORT ON;

ALTER TABLE dbo.Bookings DROP CONSTRAINT CK_Bookings_Work_Start;

ALTER TABLE dbo.Bookings ADD CONSTRAINT CK_Bookings_Work_Start
    CHECK (work_started_at IS NULL OR staff_arrived_at IS NOT NULL);
