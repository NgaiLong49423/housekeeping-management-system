SET XACT_ABORT ON;

-- Tài khoản hiện hữu giữ mặc định 0; luồng tạo Staff/Admin mới bật cờ trong ứng dụng.
ALTER TABLE dbo.Users ADD
    must_change_password bit NOT NULL
        CONSTRAINT DF_Users_must_change_password DEFAULT (0);
