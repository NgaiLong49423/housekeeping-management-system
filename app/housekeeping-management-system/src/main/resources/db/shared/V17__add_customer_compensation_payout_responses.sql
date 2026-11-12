CREATE TABLE dbo.Financial_Payout_Responses (
    payout_response_id BIGINT IDENTITY(1,1) NOT NULL
        CONSTRAINT PK_Financial_Payout_Responses PRIMARY KEY,
    entry_id BIGINT NOT NULL
        CONSTRAINT UQ_Financial_Payout_Responses_Entry UNIQUE
        CONSTRAINT FK_Financial_Payout_Responses_Entry REFERENCES dbo.Booking_Financial_Entries(entry_id),
    customer_id BIGINT NOT NULL
        CONSTRAINT FK_Financial_Payout_Responses_Customer REFERENCES dbo.Users(user_id),
    response_status VARCHAR(24) NOT NULL
        CONSTRAINT CK_Financial_Payout_Responses_Status CHECK (response_status IN ('ACCEPTED','NEEDS_DISCUSSION')),
    payout_method VARCHAR(20) NULL
        CONSTRAINT CK_Financial_Payout_Responses_Method CHECK (payout_method IS NULL OR payout_method IN ('BANK_TRANSFER','CASH')),
    encrypted_account_details NVARCHAR(MAX) NULL,
    responded_at DATETIME2(0) NOT NULL CONSTRAINT DF_Financial_Payout_Responses_Responded DEFAULT SYSDATETIME(),
    CONSTRAINT CK_Financial_Payout_Responses_Choice CHECK (
        (response_status='NEEDS_DISCUSSION' AND payout_method IS NULL AND encrypted_account_details IS NULL)
        OR (response_status='ACCEPTED' AND payout_method='CASH' AND encrypted_account_details IS NULL)
        OR (response_status='ACCEPTED' AND payout_method='BANK_TRANSFER' AND encrypted_account_details IS NOT NULL)
    )
);
