-- ====================================================================================
-- SCRIPT THIẾT LẬP DATABASE VÀ TÀI KHOẢN KẾT NỐI CHO SQL SERVER
-- Mục đích: Giúp mọi máy thành viên khi pull code về chỉ cần chạy 1 lần trong SSMS
-- là ứng dụng Spring Boot tự động kết nối và chạy thành công 100%.
-- ====================================================================================
USE [master];
GO

-- 1. Tạo Database Housekeeping_Database nếu chưa có
IF NOT EXISTS (SELECT name FROM sys.databases WHERE name = N'Housekeeping_Database')
BEGIN
    CREATE DATABASE [Housekeeping_Database];
    PRINT N'Đã tạo Database Housekeeping_Database.';
END
ELSE
BEGIN
    PRINT N'Database Housekeeping_Database đã tồn tại.';
END;
GO

-- 2. Tạo Login SQL Server hsf302_housekeeping_local
IF NOT EXISTS (SELECT name FROM sys.server_principals WHERE name = N'hsf302_housekeeping_local')
BEGIN
    CREATE LOGIN [hsf302_housekeeping_local]
    WITH PASSWORD = N'692E9A74A018766638F530029A13B142350FE42298FD55D9aA1!',
    DEFAULT_DATABASE = [Housekeeping_Database],
    CHECK_EXPIRATION = OFF,
    CHECK_POLICY = OFF;
    PRINT N'Đã tạo Login hsf302_housekeeping_local.';
END
ELSE
BEGIN
    ALTER LOGIN [hsf302_housekeeping_local]
    WITH PASSWORD = N'692E9A74A018766638F530029A13B142350FE42298FD55D9aA1!';
    PRINT N'Đã cập nhật mật khẩu Login hsf302_housekeeping_local.';
END;
GO

-- 3. Tạo User và cấp quyền toàn quyền (db_owner) trên Housekeeping_Database
USE [Housekeeping_Database];
GO

IF NOT EXISTS (SELECT name FROM sys.database_principals WHERE name = N'hsf302_housekeeping_local')
BEGIN
    CREATE USER [hsf302_housekeeping_local] FOR LOGIN [hsf302_housekeeping_local];
    PRINT N'Đã tạo User hsf302_housekeeping_local trong Housekeeping_Database.';
END;
GO

ALTER ROLE db_owner ADD MEMBER [hsf302_housekeeping_local];
GO

PRINT N'=============================================================================';
PRINT N'Thành công! Mọi máy khi pull code về có thể chạy ứng dụng mà không cần sửa cấu hình.';
PRINT N'=============================================================================';
