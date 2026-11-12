SET XACT_ABORT ON;

ALTER TABLE dbo.Bookings ADD
    admin_no_show_verified_at DATETIME2(0) NULL,
    admin_no_show_verified_by BIGINT NULL,
    admin_no_show_note NVARCHAR(500) NULL,
    customer_waited_at DATETIME2(0) NULL;

ALTER TABLE dbo.Bookings ADD CONSTRAINT FK_Bookings_Admin_No_Show_Verified_By
    FOREIGN KEY (admin_no_show_verified_by) REFERENCES dbo.Users(user_id);

ALTER TABLE dbo.Bookings ADD CONSTRAINT CK_Bookings_Admin_No_Show_Verification
    CHECK ((admin_no_show_verified_at IS NULL AND admin_no_show_verified_by IS NULL AND admin_no_show_note IS NULL)
        OR (admin_no_show_verified_at IS NOT NULL AND admin_no_show_verified_by IS NOT NULL
            AND admin_no_show_note IS NOT NULL AND LEN(LTRIM(RTRIM(admin_no_show_note))) BETWEEN 1 AND 500
            AND customer_delay_reported_at IS NOT NULL));

ALTER TABLE dbo.Bookings ADD CONSTRAINT CK_Bookings_Customer_Waited_After_Delay_Review
    CHECK (customer_waited_at IS NULL OR (customer_delay_reported_at IS NOT NULL
        AND (admin_no_show_verified_at IS NOT NULL OR arrival_verified_at > start_time)));
