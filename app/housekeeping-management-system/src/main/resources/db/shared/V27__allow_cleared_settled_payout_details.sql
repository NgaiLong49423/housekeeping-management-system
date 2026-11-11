SET XACT_ABORT ON;

ALTER TABLE dbo.Financial_Payout_Responses
    DROP CONSTRAINT CK_Financial_Payout_Responses_Status;
ALTER TABLE dbo.Financial_Payout_Responses
    DROP CONSTRAINT CK_Financial_Payout_Responses_Choice;

-- Previously settled transfers still had response_status=ACCEPTED, which made the
-- V25 choice constraint reject the required deletion of encrypted account details.
UPDATE response
   SET response_status='SETTLED',
       encrypted_account_details=NULL,
       payout_method_selected_at=COALESCE(response.payout_method_selected_at, response.responded_at)
  FROM dbo.Financial_Payout_Responses response
  JOIN dbo.Booking_Financial_Entries entry ON entry.entry_id=response.entry_id
 WHERE entry.entry_type='COMPENSATION'
   AND entry.status='PAID'
   AND response.response_status='ACCEPTED';

ALTER TABLE dbo.Financial_Payout_Responses
    ADD CONSTRAINT CK_Financial_Payout_Responses_Status
    CHECK (response_status IN ('AGREED_AMOUNT','ACCEPTED','NEEDS_DISCUSSION','SETTLED'));

ALTER TABLE dbo.Financial_Payout_Responses
    ADD CONSTRAINT CK_Financial_Payout_Responses_Choice CHECK (
        (response_status='AGREED_AMOUNT' AND payout_method IS NULL AND encrypted_account_details IS NULL)
     OR (response_status='NEEDS_DISCUSSION' AND payout_method IS NULL AND encrypted_account_details IS NULL)
     OR (response_status='ACCEPTED' AND payout_method='CASH' AND encrypted_account_details IS NULL)
     OR (response_status='ACCEPTED' AND payout_method='BANK_TRANSFER' AND encrypted_account_details IS NOT NULL)
     OR (response_status='SETTLED' AND payout_method IN ('CASH','BANK_TRANSFER')
         AND encrypted_account_details IS NULL AND payout_method_selected_at IS NOT NULL)
    );
