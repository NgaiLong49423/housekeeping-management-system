SET XACT_ABORT ON;

IF EXISTS (SELECT 1 FROM sys.key_constraints WHERE name = 'UQ_Company_Operating_Hours_Company_Day'
           AND parent_object_id = OBJECT_ID('dbo.Company_Operating_Hours'))
    ALTER TABLE dbo.Company_Operating_Hours DROP CONSTRAINT UQ_Company_Operating_Hours_Company_Day;

ALTER TABLE dbo.Company_Operating_Hours
    ADD period_no SMALLINT NOT NULL CONSTRAINT DF_Company_Operating_Hours_Period DEFAULT (1);

EXEC sys.sp_executesql N'
    ;WITH NumberedHours AS (
        SELECT operating_hour_id,
               ROW_NUMBER() OVER (PARTITION BY company_id, day_of_week ORDER BY operating_hour_id) AS new_period_no
        FROM dbo.Company_Operating_Hours
    )
    UPDATE hours
    SET period_no = NumberedHours.new_period_no
    FROM dbo.Company_Operating_Hours AS hours
    JOIN NumberedHours ON NumberedHours.operating_hour_id = hours.operating_hour_id;

    CREATE UNIQUE INDEX UX_Company_Operating_Hours_Company_Day_Period
        ON dbo.Company_Operating_Hours(company_id, day_of_week, period_no);
';

ALTER TABLE dbo.Staff_Availability
    ADD is_active BIT NOT NULL CONSTRAINT DF_Staff_Availability_Active DEFAULT (1);

ALTER TABLE dbo.Bookings
    ADD customer_arrived_at DATETIME2(0) NULL;
