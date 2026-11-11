SET XACT_ABORT ON;

ALTER TABLE dbo.Bookings ADD
    arrival_verified_at datetime2 NULL,
    arrival_verification_recorded_at datetime2 NULL,
    arrival_verified_by bigint NULL,
    arrival_verification_source varchar(10) NULL,
    arrival_verification_note nvarchar(500) NULL,
    CONSTRAINT CK_Bookings_ArrivalVerificationSource
        CHECK (arrival_verification_source IS NULL OR arrival_verification_source IN ('CUSTOMER', 'ADMIN')),
    CONSTRAINT CK_Bookings_ArrivalVerificationAudit
        CHECK ((arrival_verified_at IS NULL AND arrival_verification_recorded_at IS NULL
                AND arrival_verified_by IS NULL AND arrival_verification_source IS NULL
                AND arrival_verification_note IS NULL)
            OR (arrival_verified_at IS NOT NULL AND arrival_verification_recorded_at IS NOT NULL
                AND arrival_verified_by IS NOT NULL AND arrival_verification_source IS NOT NULL)),
    CONSTRAINT FK_Bookings_ArrivalVerifiedBy_Users
        FOREIGN KEY (arrival_verified_by) REFERENCES dbo.Users(user_id);
