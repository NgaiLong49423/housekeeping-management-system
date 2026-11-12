SET XACT_ABORT ON;

ALTER TABLE dbo.Feedback_Cases ADD customer_requested_resolution NVARCHAR(1000) NULL;
ALTER TABLE dbo.Feedback_Cases DROP CONSTRAINT CK_Feedback_Cases_Status;
ALTER TABLE dbo.Feedback_Cases DROP CONSTRAINT CK_Feedback_Cases_Resolution;
DROP INDEX IX_Feedback_Cases_Status ON dbo.Feedback_Cases;
ALTER TABLE dbo.Feedback_Cases ALTER COLUMN case_status VARCHAR(40) NOT NULL;
ALTER TABLE dbo.Feedback_Cases ADD CONSTRAINT CK_Feedback_Cases_Status CHECK (case_status IN
    ('RECEIVED','INVESTIGATING','SEARCHING_ITEM','WAITING_CUSTOMER','WAITING_MANAGER','WAITING_PAYOUT',
     'WAITING_CUSTOMER_CONFIRMATION','VERIFIED','NOT_SUBSTANTIATED','RESOLVED'));
ALTER TABLE dbo.Feedback_Cases ADD CONSTRAINT CK_Feedback_Cases_Resolution CHECK
    ((case_status='RESOLVED' AND resolved_at IS NOT NULL) OR (case_status<>'RESOLVED' AND resolved_at IS NULL));
CREATE INDEX IX_Feedback_Cases_Status ON dbo.Feedback_Cases(case_status,created_at);

ALTER TABLE dbo.Financial_Payout_Responses DROP CONSTRAINT CK_Financial_Payout_Responses_Status;
ALTER TABLE dbo.Financial_Payout_Responses DROP CONSTRAINT CK_Financial_Payout_Responses_Choice;
ALTER TABLE dbo.Financial_Payout_Responses ADD customer_confirmed_at DATETIME2(0) NULL;
ALTER TABLE dbo.Financial_Payout_Responses ADD amount_agreed_at DATETIME2(0) NULL;
ALTER TABLE dbo.Financial_Payout_Responses ADD payout_method_selected_at DATETIME2(0) NULL;
ALTER TABLE dbo.Financial_Payout_Responses ADD CONSTRAINT CK_Financial_Payout_Responses_Status
    CHECK (response_status IN ('AGREED_AMOUNT','ACCEPTED','NEEDS_DISCUSSION'));
ALTER TABLE dbo.Financial_Payout_Responses ADD CONSTRAINT CK_Financial_Payout_Responses_Choice CHECK (
    (response_status='AGREED_AMOUNT' AND payout_method IS NULL AND encrypted_account_details IS NULL)
 OR (response_status='NEEDS_DISCUSSION' AND payout_method IS NULL AND encrypted_account_details IS NULL)
 OR (response_status='ACCEPTED' AND payout_method='CASH' AND encrypted_account_details IS NULL)
 OR (response_status='ACCEPTED' AND payout_method='BANK_TRANSFER' AND encrypted_account_details IS NOT NULL)
);

IF COL_LENGTH('dbo.Booking_Financial_Entries','resolution_action_id') IS NOT NULL
BEGIN
    ALTER TABLE dbo.Booking_Financial_Entries DROP CONSTRAINT FK_Booking_Financial_Entries_Resolution_Action;
    ALTER TABLE dbo.Booking_Financial_Entries DROP COLUMN resolution_action_id;
END;
DROP TABLE dbo.Feedback_Work_Orders;
DROP TABLE dbo.Feedback_Resolution_Actions;
DROP TABLE dbo.Feedback_Resolution_Options;
DROP TABLE dbo.Feedback_Resolution_Plans;
ALTER TABLE dbo.Feedback_Cases DROP CONSTRAINT DF_Feedback_Cases_Resolution_Workflow;
ALTER TABLE dbo.Feedback_Cases DROP COLUMN resolution_workflow_required;

IF UPPER(DB_NAME()) = N'HOME_CLEANING_DEMO'
BEGIN
    DECLARE @DemoCustomer BIGINT = (SELECT user_id FROM dbo.Users WHERE email=N'customer@demo.gonnha.local' AND role='CUSTOMER');
    DECLARE @DemoAdmin BIGINT = (SELECT user_id FROM dbo.Users WHERE email=N'admin@demo.gonnha.local' AND role='ADMIN');

    -- SQL Server may compile all statements in a migration batch before applying the
    -- preceding ALTER TABLE. Execute statements that reference the new column dynamically.
    EXEC sys.sp_executesql N'
        UPDATE c SET case_status=''INVESTIGATING'', customer_requested_resolution=@desired,
            internal_finding=NULL, reviewed_by=NULL, reviewed_at=NULL, remedy_type=NULL, remedy_completed=0
        FROM dbo.Feedback_Cases c JOIN dbo.Feedbacks f ON f.feedback_id=c.feedback_id
        WHERE f.customer_id=@customer AND c.incident_types=N''CLEANLINESS'' AND f.content=@content;',
        N'@desired NVARCHAR(1000), @customer BIGINT, @content NVARCHAR(MAX)',
        @desired=N'Mong công ty kiểm tra và cho biết có thể xử lý khu vực chưa sạch như thế nào.',
        @customer=@DemoCustomer,
        @content=N'Một số khu vực vẫn còn bụi sau khi dọn. Tôi muốn công ty kiểm tra và đề xuất cách khắc phục.';

    EXEC sys.sp_executesql N'
        UPDATE c SET case_status=''WAITING_CUSTOMER'', customer_requested_resolution=@desired,
            internal_finding=N''Đang xác minh tình trạng và căn cứ giá trị; chưa có số tiền được thống nhất hoặc duyệt.'',
            reviewed_by=@admin, reviewed_at=SYSDATETIME()
        FROM dbo.Feedback_Cases c JOIN dbo.Feedbacks f ON f.feedback_id=c.feedback_id
        WHERE f.customer_id=@customer AND c.incident_types=N''PROPERTY_DAMAGE'' AND f.content=@content;',
        N'@desired NVARCHAR(1000), @customer BIGINT, @admin BIGINT, @content NVARCHAR(MAX)',
        @desired=N'Ưu tiên tìm và trả lại bình gốm. Nếu không tìm được, mong công ty trao đổi phương án bồi thường dựa trên bằng chứng.',
        @customer=@DemoCustomer,
        @admin=@DemoAdmin,
        @content=N'Món đồ trang trí bị nứt trong lúc làm dịch vụ. Đây là thông tin ban đầu để công ty xác minh.';

    UPDATE m SET message_text=N'Công ty đang kiểm tra thông tin và bằng chứng. Chúng tôi sẽ trao đổi với bạn trước khi chốt hướng xử lý.'
    FROM dbo.Feedback_Case_Messages m JOIN dbo.Feedback_Cases c ON c.case_id=m.case_id
    JOIN dbo.Feedbacks f ON f.feedback_id=c.feedback_id
    WHERE m.sender_role='ADMIN' AND f.customer_id=@DemoCustomer AND c.incident_types IN (N'CLEANLINESS',N'PROPERTY_DAMAGE');

    UPDATE v SET case_status='INVESTIGATING', internal_finding=N'Đang kiểm tra phản ánh và bằng chứng; chưa chốt phương án.'
    FROM dbo.Feedback_Case_Verifications v JOIN dbo.Feedback_Cases c ON c.case_id=v.case_id
    JOIN dbo.Feedbacks f ON f.feedback_id=c.feedback_id
    WHERE f.customer_id=@DemoCustomer AND c.incident_types=N'CLEANLINESS';

    UPDATE v SET case_status='VERIFIED', internal_finding=N'Đã xem phản ánh ban đầu; cần xác minh tình trạng và căn cứ giá trị trước khi đề xuất.'
    FROM dbo.Feedback_Case_Verifications v JOIN dbo.Feedback_Cases c ON c.case_id=v.case_id
    JOIN dbo.Feedbacks f ON f.feedback_id=c.feedback_id
    WHERE f.customer_id=@DemoCustomer AND c.incident_types=N'PROPERTY_DAMAGE';

    INSERT INTO dbo.Booking_Financial_Entries(booking_id,case_id,entry_type,amount,currency,reason,status,is_simulated,created_by,created_at)
    SELECT f.booking_id,c.case_id,'COMPENSATION',120000,'VND',N'Đề xuất demo cần khách xác nhận; số tiền mẫu, chưa được duyệt hoặc chi.',
           'PROPOSED',0,@DemoAdmin,SYSDATETIME()
    FROM dbo.Feedback_Cases c JOIN dbo.Feedbacks f ON f.feedback_id=c.feedback_id
    WHERE f.customer_id=@DemoCustomer AND c.incident_types=N'PROPERTY_DAMAGE'
      AND f.content=N'Món đồ trang trí bị nứt trong lúc làm dịch vụ. Đây là thông tin ban đầu để công ty xác minh.'
      AND NOT EXISTS(SELECT 1 FROM dbo.Booking_Financial_Entries e WHERE e.case_id=c.case_id AND e.entry_type='COMPENSATION');

    UPDATE f SET status='RESPONDED', admin_reply=N'Công ty đang xác minh căn cứ giá trị và gửi đề xuất bồi thường để bạn xem xét; chưa được Manager duyệt hoặc chi.'
    FROM dbo.Feedbacks f JOIN dbo.Feedback_Cases c ON c.feedback_id=f.feedback_id
    WHERE f.customer_id=@DemoCustomer AND c.incident_types=N'PROPERTY_DAMAGE'
      AND f.content=N'Món đồ trang trí bị nứt trong lúc làm dịch vụ. Đây là thông tin ban đầu để công ty xác minh.';
END;
