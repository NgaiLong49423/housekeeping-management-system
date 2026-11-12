SET XACT_ABORT ON;

-- 1. Bổ sung các cột phục vụ nợ phạt và chống bom đơn vào bảng Users
IF NOT EXISTS (SELECT * FROM sys.columns WHERE object_id = OBJECT_ID('dbo.Users') AND name = 'unpaid_penalty_debt')
BEGIN
    ALTER TABLE dbo.Users ADD
        unpaid_penalty_debt decimal(12, 2) NOT NULL CONSTRAINT DF_Users_unpaid_penalty_debt DEFAULT 0.00,
        allow_cod bit NOT NULL CONSTRAINT DF_Users_allow_cod DEFAULT 1,
        suspended_until datetime2 NULL;
END;

-- 2. Bổ sung trạng thái và số lần dời lịch vào bảng Bookings
IF NOT EXISTS (SELECT * FROM sys.columns WHERE object_id = OBJECT_ID('dbo.Bookings') AND name = 'reschedule_count')
BEGIN
    ALTER TABLE dbo.Bookings ADD
        reschedule_count int NOT NULL CONSTRAINT DF_Bookings_reschedule_count DEFAULT 0,
        cancellation_fee decimal(12, 2) NOT NULL CONSTRAINT DF_Bookings_cancellation_fee DEFAULT 0.00,
        refund_amount decimal(12, 2) NOT NULL CONSTRAINT DF_Bookings_refund_amount DEFAULT 0.00;
END;

-- 3. Bảng Ví người dùng (Customer & Staff)
IF NOT EXISTS (SELECT * FROM sys.tables WHERE name = 'UserWallets')
BEGIN
    CREATE TABLE dbo.UserWallets (
        wallet_id bigint IDENTITY(1,1) PRIMARY KEY,
        user_id bigint NOT NULL UNIQUE,
        balance decimal(12, 2) NOT NULL CONSTRAINT DF_UserWallets_balance DEFAULT 0.00,
        updated_at datetime2 NOT NULL CONSTRAINT DF_UserWallets_updated_at DEFAULT SYSUTCDATETIME(),
        CONSTRAINT CK_UserWallets_balance CHECK (balance >= 0),
        CONSTRAINT FK_UserWallets_Users FOREIGN KEY (user_id) REFERENCES dbo.Users(user_id)
    );
END;

-- 4. Bảng Lịch sử Giao dịch Ví (Audit Trail)
IF NOT EXISTS (SELECT * FROM sys.tables WHERE name = 'WalletTransactions')
BEGIN
    CREATE TABLE dbo.WalletTransactions (
        transaction_id bigint IDENTITY(1,1) PRIMARY KEY,
        wallet_id bigint NOT NULL,
        user_id bigint NOT NULL,
        amount decimal(12, 2) NOT NULL,
        balance_before decimal(12, 2) NOT NULL,
        balance_after decimal(12, 2) NOT NULL,
        type varchar(50) NOT NULL,
        reference_id bigint NULL,
        note nvarchar(500) NULL,
        created_at datetime2 NOT NULL CONSTRAINT DF_WalletTransactions_created_at DEFAULT SYSUTCDATETIME(),
        CONSTRAINT FK_WalletTransactions_UserWallets FOREIGN KEY (wallet_id) REFERENCES dbo.UserWallets(wallet_id),
        CONSTRAINT FK_WalletTransactions_Users FOREIGN KEY (user_id) REFERENCES dbo.Users(user_id)
    );
    CREATE INDEX IX_WalletTransactions_user_date ON dbo.WalletTransactions(user_id, created_at);
END;

-- 5. Bảng Nhật ký Hủy đơn (Cancellation Logs)
IF NOT EXISTS (SELECT * FROM sys.tables WHERE name = 'CancellationLogs')
BEGIN
    CREATE TABLE dbo.CancellationLogs (
        log_id bigint IDENTITY(1,1) PRIMARY KEY,
        booking_id bigint NOT NULL,
        customer_id bigint NOT NULL,
        worker_id bigint NULL,
        cancelled_by_type varchar(30) NOT NULL,
        reason_code varchar(50) NOT NULL,
        reason_note nvarchar(1000) NULL,
        booking_total_amount decimal(12, 2) NOT NULL,
        cancellation_fee decimal(12, 2) NOT NULL,
        worker_compensation decimal(12, 2) NOT NULL,
        platform_fee decimal(12, 2) NOT NULL,
        refund_amount decimal(12, 2) NOT NULL,
        delta_minutes_to_start bigint NOT NULL,
        is_worker_fault bit NOT NULL CONSTRAINT DF_CancellationLogs_is_worker_fault DEFAULT 0,
        created_at datetime2 NOT NULL CONSTRAINT DF_CancellationLogs_created_at DEFAULT SYSUTCDATETIME(),
        CONSTRAINT FK_CancellationLogs_Bookings FOREIGN KEY (booking_id) REFERENCES dbo.Bookings(booking_id),
        CONSTRAINT FK_CancellationLogs_Customers FOREIGN KEY (customer_id) REFERENCES dbo.Users(user_id)
    );
    CREATE INDEX IX_CancellationLogs_customer_date ON dbo.CancellationLogs(customer_id, created_at);
END;

-- 6. Tạo tài khoản Manager mặc định nếu chưa tồn tại
IF NOT EXISTS (SELECT 1 FROM dbo.Users WHERE role = 'MANAGER')
BEGIN
    INSERT INTO dbo.Users (full_name, email, password_hash, phone, role, status)
    VALUES (N'Quản lý cấp cao', N'manager@gonnha.vn',
            N'{bcrypt}$2a$10$Y85jiA/BKR2TZlb/1kHPWeL79WzLTeWZgv4jiNZHsAWWDjl/qWFoO',
            N'0900000005', 'MANAGER', 'ACTIVE');
END;
