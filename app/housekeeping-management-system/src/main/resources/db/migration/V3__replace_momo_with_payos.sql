ALTER TABLE Payment DROP CONSTRAINT CK_Payment_Method;
ALTER TABLE Payment ADD CONSTRAINT CK_Payment_Method
    CHECK (payment_method IS NULL OR payment_method IN ('CASH', 'BANK_TRANSFER', 'PAYOS', 'MOMO'));

ALTER TABLE Payment_Attempt ALTER COLUMN provider_transaction_id VARCHAR(100) NULL;
ALTER TABLE Payment_Attempt ADD provider_payment_link_id VARCHAR(100) NULL;

CREATE SEQUENCE dbo.PayOS_Order_Code_Seq
    AS BIGINT
    START WITH 100000000
    INCREMENT BY 1
    MINVALUE 1
    MAXVALUE 2147483647
    NO CYCLE;
