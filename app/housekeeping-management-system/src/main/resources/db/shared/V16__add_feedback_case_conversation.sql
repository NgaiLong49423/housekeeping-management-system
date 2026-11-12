CREATE TABLE dbo.Feedback_Case_Messages (
    message_id BIGINT IDENTITY(1,1) NOT NULL CONSTRAINT PK_Feedback_Case_Messages PRIMARY KEY,
    case_id BIGINT NOT NULL CONSTRAINT FK_Feedback_Case_Messages_Case REFERENCES dbo.Feedback_Cases(case_id),
    sender_user_id BIGINT NULL CONSTRAINT FK_Feedback_Case_Messages_Sender REFERENCES dbo.Users(user_id),
    sender_role VARCHAR(20) NOT NULL CONSTRAINT CK_Feedback_Case_Messages_Role CHECK (sender_role IN ('CUSTOMER','ADMIN')),
    message_text NVARCHAR(MAX) NULL,
    sent_at DATETIME2(0) NOT NULL CONSTRAINT DF_Feedback_Case_Messages_Sent DEFAULT SYSDATETIME(),
    legacy_imported BIT NOT NULL CONSTRAINT DF_Feedback_Case_Messages_Legacy DEFAULT (0),
    legacy_timestamp_unknown BIT NOT NULL CONSTRAINT DF_Feedback_Case_Messages_LegacyTime DEFAULT (0),
    CONSTRAINT CK_Feedback_Case_Messages_Text CHECK (message_text IS NULL OR (LEN(LTRIM(RTRIM(message_text)))>0 AND (legacy_imported=1 OR LEN(message_text)<=4000)))
);

CREATE INDEX IX_Feedback_Case_Messages_CaseTime
    ON dbo.Feedback_Case_Messages(case_id,sent_at,message_id);

ALTER TABLE dbo.Feedback_Case_Evidence
    ADD message_id BIGINT NULL;

ALTER TABLE dbo.Feedback_Case_Evidence
    ADD CONSTRAINT FK_Feedback_Case_Evidence_Message
        FOREIGN KEY (message_id) REFERENCES dbo.Feedback_Case_Messages(message_id);

/* Keep the original Customer report as the first message for each case. */
INSERT INTO dbo.Feedback_Case_Messages(case_id,sender_user_id,sender_role,message_text,sent_at,legacy_imported)
SELECT c.case_id,f.customer_id,'CUSTOMER',c.customer_description,c.created_at,1
FROM dbo.Feedback_Cases c
JOIN dbo.Feedbacks f ON f.feedback_id=c.feedback_id;

/* Feedbacks did not record who/when authored the reply. Import it with an explicit legacy marker. */
INSERT INTO dbo.Feedback_Case_Messages(case_id,sender_user_id,sender_role,message_text,sent_at,legacy_imported,legacy_timestamp_unknown)
SELECT c.case_id,NULL,'ADMIN',f.admin_reply,SYSDATETIME(),1,1
FROM dbo.Feedback_Cases c
JOIN dbo.Feedbacks f ON f.feedback_id=c.feedback_id
WHERE f.admin_reply IS NOT NULL AND LEN(LTRIM(RTRIM(f.admin_reply)))>0;

/* Existing evidence belongs to the original Customer report. */
UPDATE e
SET message_id=first_message.message_id
FROM dbo.Feedback_Case_Evidence e
CROSS APPLY (
    SELECT TOP (1) m.message_id
    FROM dbo.Feedback_Case_Messages m
    WHERE m.case_id=e.case_id AND m.sender_role='CUSTOMER'
    ORDER BY m.message_id
) first_message;
