SET XACT_ABORT ON;

-- Bản sao lưu database/Housekeeping_Database.bak chứa 6 tài khoản Staff (lan.hoang, tuan.vu, hoa.do, huy.nguyen,
-- thu.le, phong.tran) có mã băm mật khẩu bị cắt mất đoạn đầu "$2a$10$Y85jiA", nên không đăng nhập được.
-- Migration này chỉ sửa đúng các dòng mang mã băm hỏng đó, trả về mã băm của mật khẩu mẫu Admin@12345
-- (cùng giá trị với database/sample-data.sql). Database không có dòng hỏng thì không bị thay đổi gì.
DECLARE @broken_hash NVARCHAR(255) = N'{bcrypt}/BKR2TZlb/1kHPWeL79WzLTeWZgv4jiNZHsAWWDjl/qWFoO';
DECLARE @sample_hash NVARCHAR(255) = N'{bcrypt}$2a$10$Y85jiA/BKR2TZlb/1kHPWeL79WzLTeWZgv4jiNZHsAWWDjl/qWFoO';

UPDATE dbo.Users
SET password_hash = @sample_hash
WHERE password_hash = @broken_hash;
