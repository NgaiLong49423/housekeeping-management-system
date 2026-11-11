> **Document:** Database Guide\
> **File:** `database/README.md`\
> **Version:** v2.7.0\
> **Created:** 2026-06-14\
> **Last Updated:** 2026-11-11\
> **Status:** Active\

# Hướng dẫn database

Microsoft SQL Server là database chính thức (`Housekeeping_Database`). Hệ thống sử dụng thống nhất đúng 1 database này. Schema đích gồm đúng 15 bảng nghiệp vụ theo [Từ điển dữ liệu SQL Server](../docs/requirements/SQL_SERVER_DATA_DICTIONARY.md). Hibernate dùng `ddl-auto=validate`; chỉ Flyway quản lý DDL.

## Opt-in automatic local reset

`DB_RESET_ON_STARTUP` mặc định là `false`, giữ nguyên dữ liệu hiện có trong database. Khi được chỉ định rõ ràng là `true`, trước khi khởi tạo datasource, ứng dụng sẽ kết nối tới `master`, ngắt kết nối người dùng hiện tại khỏi database local được hỗ trợ (`Housekeeping_Database`), drop nếu đã có và tạo lại mới. Flyway sau đó sẽ tái tạo schema. **Toàn bộ dữ liệu trước đó và bảng lịch sử Flyway trong database đó sẽ bị xóa.**

Chỉ `Housekeeping_Database` trên `localhost`, `127.0.0.1` hoặc `[::1]` mới được phép reset. SQL login cần quyền ALTER/DROP/CREATE DATABASE. Chỉ đặt `DB_RESET_ON_STARTUP=true` trong file `.env` khi thực sự muốn reset trắng database local; hãy để `false` khi muốn giữ lại dữ liệu.

## Flyway và lịch sử schema

- `app/housekeeping-management-system/src/main/resources/db/primary/` là baseline của database chính thức `Housekeeping_Database`; Flyway ghi nhận lịch sử trong `flyway_primary_schema_history`. Flyway tạo baseline version `0` rồi áp dụng V1; migration V1 chỉ cho phép database chính/acceptance rỗng (ngoại trừ bảng lịch sử Flyway).
- `app/housekeeping-management-system/src/main/resources/db/shared/` chứa migration version dùng chung sau baseline; ứng dụng nạp migration V2, V5, V6 và V8 từ đây.
- Migration V6 thêm thời điểm Staff xác nhận đến và báo sự cố trên `Bookings`; schema vẫn duy trì đúng 15 bảng nghiệp vụ.
- Migration V8 thêm ETA, phản hồi của Customer, giờ Staff bắt đầu việc và thông tin giảm trừ do đến trễ trên `Bookings`.
- Cửa sổ xác nhận đến mặc định là 0 phút (từ giờ bắt đầu), môi trường local có thể cấu hình lên 5 phút thông qua `application-local.properties`.
- Nếu chủ sở hữu muốn xóa dữ liệu cũ và khởi tạo lại database sạch sẽ, có thể chạy [`recreate-primary-schema.sql`](recreate-primary-schema.sql) một lần trong đúng `Housekeeping_Database`.

## Cấu hình kết nối

Trong file `.env` (hoặc cấu hình local `.runtime/application-local.properties`), đặt thông tin kết nối tới duy nhất `Housekeeping_Database`:
- `DB_URL`: `jdbc:sqlserver://localhost:1433;databaseName=Housekeeping_Database;encrypt=true;trustServerCertificate=true`
- `DB_USERNAME`: tài khoản SQL Server của bạn (ví dụ `hsf302_housekeeping_local` hoặc `sa`)
- `DB_PASSWORD`: mật khẩu SQL Server

## Kiểm chứng

```powershell
# Unit/MVC test, không cần SQL Server
mvn "-DexcludedGroups=sqlserver" verify

# Flyway, Hibernate mapping và concurrency trên SQL Server Testcontainers (yêu cầu Docker)
mvn verify
```
