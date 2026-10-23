ALTER TABLE Payment DROP CONSTRAINT CK_Payment_Method;
ALTER TABLE Payment ADD CONSTRAINT CK_Payment_Method
    CHECK (payment_method IS NULL OR payment_method IN ('CASH', 'BANK_TRANSFER', 'MOMO'));

CREATE TABLE Payment_Attempt (
    attempt_id BIGINT IDENTITY(1,1) NOT NULL,
    payment_id BIGINT NOT NULL,
    provider_order_id VARCHAR(64) NOT NULL,
    request_id VARCHAR(64) NOT NULL,
    provider_transaction_id BIGINT NULL,
    amount DECIMAL(18,0) NOT NULL,
    attempt_status VARCHAR(12) NOT NULL,
    checkout_url NVARCHAR(2000) NULL,
    created_at DATETIMEOFFSET(0) NOT NULL,
    updated_at DATETIMEOFFSET(0) NOT NULL,
    CONSTRAINT PK_Payment_Attempt PRIMARY KEY (attempt_id),
    CONSTRAINT FK_Payment_Attempt_Payment FOREIGN KEY (payment_id) REFERENCES Payment(payment_id)
        ON DELETE NO ACTION ON UPDATE NO ACTION,
    CONSTRAINT UQ_Payment_Attempt_Order UNIQUE (provider_order_id),
    CONSTRAINT UQ_Payment_Attempt_Request UNIQUE (request_id),
    CONSTRAINT CK_Payment_Attempt_Status CHECK (attempt_status IN ('INITIATING', 'PENDING', 'FAILED', 'SUCCEEDED')),
    CONSTRAINT CK_Payment_Attempt_Amount CHECK (amount > 0)
);

CREATE INDEX IX_Payment_Attempt_Payment_Created
    ON Payment_Attempt(payment_id, created_at DESC, attempt_id DESC);
