> **Document:** Database Guide\
> **File:** `database/README.md`\
> **Version:** v3.5.0\
> **Created:** 2026-06-14\
> **Last Updated:** 2026-11-12\
> **Status:** Active\

# Hướng dẫn database

Microsoft SQL Server là database chính thức (`Housekeeping_Database`). Hệ thống sử dụng Flyway để quản lý DDL với `ddl-auto=validate`. Schema hiện tại có 25 bảng nghiệp vụ theo [Từ điển dữ liệu SQL Server](../docs/requirements/SQL_SERVER_DATA_DICTIONARY.md). V18 thêm thông tin món đồ Customer khai báo; V19 từng thêm phương án/phiếu việc; V25 giữ lịch sử migration nhưng loại bỏ bốn bảng phương án/phiếu việc và rút gọn luồng sự cố; V26 thêm mốc Staff hoàn tất để thực thi hạn phản ánh 24 giờ; V27 tách trạng thái đã quyết toán để cho phép xóa ciphertext tài khoản ngân hàng; V28 bổ sung quy trình Staff giải trình/kháng nghị và theo dõi thu hồi theo từng lần thực nhận; V29 đồng bộ nội dung và ảnh minh họa cho các phản ánh demo.

## Opt-in automatic local reset

`DB_RESET_ON_STARTUP` mặc định là `false`, giữ nguyên dữ liệu hiện có trong database. Khi được chỉ định rõ ràng là `true`, trước khi khởi tạo datasource, ứng dụng sẽ kết nối tới `master`, ngắt kết nối người dùng hiện tại khỏi database local được hỗ trợ (`Housekeeping_Database`), drop nếu đã có và tạo lại mới. Flyway sau đó sẽ tái tạo schema. **Toàn bộ dữ liệu trước đó và bảng lịch sử Flyway trong database đó sẽ bị xóa.**

Chỉ `Housekeeping_Database` trên `localhost`, `127.0.0.1` hoặc `[::1]` mới được phép reset. SQL login cần quyền ALTER/DROP/CREATE DATABASE. Chỉ đặt `DB_RESET_ON_STARTUP=true` trong file `.env` khi thực sự muốn reset trắng database local; hãy để `false` khi muốn giữ lại dữ liệu.

## Flyway và lịch sử schema

- `app/housekeeping-management-system/src/main/resources/db/primary/` là baseline của database chính thức `Housekeeping_Database`; Flyway ghi nhận lịch sử trong `flyway_primary_schema_history`. Flyway tạo baseline version `0` rồi áp dụng V1; migration V1 chỉ cho phép database chính/acceptance rỗng (ngoại trừ bảng lịch sử Flyway).
- `app/housekeeping-management-system/src/main/resources/db/shared/` chứa migration version dùng chung sau baseline; ứng dụng nạp migration dùng chung liên tục từ V2 đến V29.
- Migration V6 thêm thời điểm Staff xác nhận đến và báo sự cố trên `Bookings`.
- Migration V8 thêm ETA, phản hồi đồng thuận của Customer, giờ Staff bắt đầu việc và thông tin giảm trừ do đến trễ trên `Bookings`.
- Migration V14 thêm bảng `Booking_Delay_Compensation_Tiers` cấu hình giảm giá khi trễ.
- Migration V15 chuyển Admin quản lý hiện tại thành vai trò `MANAGER`.
- Migration V16 thêm bảng `Feedback_Case_Messages` và liên kết bằng chứng hiện có qua `message_id`.
- Migration V17 thêm `Financial_Payout_Responses`.
- Migration V25 rút gọn luồng sự cố còn 23 bảng nghiệp vụ; V26 thêm mốc hoàn tất Booking; V27 ghi trạng thái `SETTLED`; V28 thêm bằng chứng giải trình Staff và các lần Staff thực trả, đưa schema lên 25 bảng nghiệp vụ. V29 gắn ảnh minh họa cho dữ liệu phản ánh demo.
- Cửa sổ xác nhận đến mặc định là 0 phút (từ giờ bắt đầu), môi trường local có thể cấu hình thông qua `application.properties`.
- Nếu muốn xóa dữ liệu cũ và khởi tạo lại database sạch sẽ, có thể chạy [`recreate-primary-schema.sql`](recreate-primary-schema.sql) một lần trong đúng `Housekeeping_Database`.

### Khởi tạo Manager hệ thống
Database demo seed tài khoản Manager riêng ở V11 và chuyển vai trò từ cờ cũ trong V15. Với database chính, nếu V15 không tìm thấy Admin đang có cờ quản lý để chuyển đổi, DBA cần gán vai trò cho đúng một tài khoản Admin đã xác minh (thay placeholder trước khi chạy):
```sql
UPDATE dbo.Users SET role='MANAGER'
WHERE email=N'<manager-admin-email>' AND role='ADMIN' AND status='ACTIVE';
```
Filtered unique index từ V15 ngăn tạo Manager thứ hai. Manager có thể tạo/khoá Admin tại `/admin/accounts`; Admin không được tự cấp quyền hoặc mở trang này.

## Cấu hình kết nối

Trong file `.env` ở thư mục dự án hoặc module, đặt thông tin kết nối tới `Housekeeping_Database`:
- `DB_URL`: `jdbc:sqlserver://localhost:1433;databaseName=Housekeeping_Database;encrypt=true;trustServerCertificate=true`
- `DB_USERNAME`: tài khoản SQL Server của bạn (ví dụ `hsf302_housekeeping_local` hoặc `sa`)
- `DB_PASSWORD`: mật khẩu SQL Server
- `PAYOUT_ENCRYPTION_KEY`: chuỗi Base64 32 byte (AES-256) dùng để mã hóa thông tin tài khoản chuyển khoản bồi thường.

## Dữ liệu mẫu (Sample Data) & Khôi phục nhanh

Có 2 cách nạp dữ liệu mẫu sạch và đồng bộ:

### Cách 1: Chạy file script SQL (Khuyên dùng)
Để tránh lỗi hiển thị font tiếng Việt (Mojibake), hãy sử dụng cờ `-f 65001` khi dùng `sqlcmd`:
```powershell
sqlcmd -S localhost -d Housekeeping_Database -U hsf302_housekeeping_local -P "692E9A74A018766638F530029A13B142350FE42298FD55D9aA1!" -f 65001 -i database\sample-data.sql
```
Hoặc mở và thực thi trực tiếp bằng SSMS (SQL Server Management Studio) / DBeaver / Azure Data Studio.

### Cách 2: Khôi phục trực tiếp từ file Backup (`.bak`)
File backup nguyên bản chuẩn `database/Housekeeping_Database.bak` (6 MB) đã được đóng gói sẵn. Có thể restore 1-click qua SSMS hoặc lệnh:
```powershell
sqlcmd -S localhost -d master -E -Q "RESTORE DATABASE Housekeeping_Database FROM DISK = '$(Get-Location)\database\Housekeeping_Database.bak' WITH REPLACE;"
```

## Kiểm chứng

```powershell
# Unit/MVC test, không cần SQL Server
mvn "-DexcludedGroups=sqlserver" verify

# Flyway, Hibernate mapping và concurrency trên SQL Server Testcontainers (yêu cầu Docker)
mvn verify
```

Không kết luận migration hoặc concurrency đã được SQL Server xác minh nếu Testcontainers/SQL Server tests không chạy. Xem [README](../README.md) để chạy ứng dụng.
