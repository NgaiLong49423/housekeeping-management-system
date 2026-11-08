SET XACT_ABORT ON;

IF COL_LENGTH('dbo.Company_Operating_Hours', 'effective_from') IS NULL
BEGIN
    ALTER TABLE dbo.Company_Operating_Hours
        ADD effective_from DATE NOT NULL
            CONSTRAINT DF_Company_Operating_Hours_Effective_From DEFAULT ('19000101');
END;

IF EXISTS (SELECT 1 FROM sys.indexes
           WHERE object_id = OBJECT_ID('dbo.Company_Operating_Hours')
             AND name = 'UX_Company_Operating_Hours_Company_Day_Period')
    DROP INDEX UX_Company_Operating_Hours_Company_Day_Period ON dbo.Company_Operating_Hours;

IF NOT EXISTS (SELECT 1 FROM sys.indexes
               WHERE object_id = OBJECT_ID('dbo.Company_Operating_Hours')
                 AND name = 'UX_Company_Operating_Hours_Company_Effective_Day_Period')
    CREATE UNIQUE INDEX UX_Company_Operating_Hours_Company_Effective_Day_Period
        ON dbo.Company_Operating_Hours(company_id, effective_from, day_of_week, period_no);

IF NOT EXISTS (SELECT 1 FROM sys.indexes
               WHERE object_id = OBJECT_ID('dbo.Company_Operating_Hours')
                 AND name = 'IX_Company_Operating_Hours_Effective_From')
    CREATE INDEX IX_Company_Operating_Hours_Effective_From
        ON dbo.Company_Operating_Hours(company_id, effective_from DESC, day_of_week, period_no);
