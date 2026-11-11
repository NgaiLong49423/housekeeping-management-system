SET XACT_ABORT ON;

ALTER TABLE dbo.Feedbacks
    ADD rating TINYINT NULL,
        topics NVARCHAR(300) NOT NULL
            CONSTRAINT DF_Feedbacks_Topics DEFAULT (N'');

ALTER TABLE dbo.Feedbacks
    ALTER COLUMN content NVARCHAR(MAX) NULL;

-- Preserve old replies and expose them as awaiting resolution after upgrade.
ALTER TABLE dbo.Feedbacks DROP CONSTRAINT CK_Feedbacks_Status;
UPDATE dbo.Feedbacks
SET status='RESPONDED'
WHERE admin_reply IS NOT NULL AND LEN(LTRIM(RTRIM(admin_reply))) > 0
  AND status='OPEN';
ALTER TABLE dbo.Feedbacks ADD CONSTRAINT CK_Feedbacks_Status
    CHECK (status IN ('OPEN', 'RESPONDED', 'RESOLVED'));

-- Compile constraints in a later dynamic batch. SQL Server can bind column references
-- before earlier ALTER TABLE statements in the same migration batch have added them.
EXEC sys.sp_executesql N'
    ALTER TABLE dbo.Feedbacks ADD CONSTRAINT CK_Feedbacks_Rating
        CHECK (rating IS NULL OR rating BETWEEN 1 AND 5);
    ALTER TABLE dbo.Feedbacks ADD CONSTRAINT CK_Feedbacks_Required_Content
        CHECK (rating IS NULL OR rating >= 4 OR (content IS NOT NULL AND LEN(LTRIM(RTRIM(content))) > 0));
    ALTER TABLE dbo.Feedbacks ADD CONSTRAINT CK_Feedbacks_Reply_Status
        CHECK ((status=''OPEN'' AND (admin_reply IS NULL OR LEN(LTRIM(RTRIM(admin_reply)))=0))
            OR (status=''RESPONDED'' AND admin_reply IS NOT NULL AND LEN(LTRIM(RTRIM(admin_reply))) > 0)
            OR (status=''RESOLVED'' AND (rating IS NULL OR (admin_reply IS NOT NULL AND LEN(LTRIM(RTRIM(admin_reply))) > 0))));
';
