> **Document:** Database Guide\
> **File:** `database/README.md`\
> **Version:** v2.4.0\
> **Created:** 2026-06-14\
> **Last Updated:** 2026-10-27\
> **Status:** Draft\

# Hướng dẫn database

Microsoft SQL Server là database chính thức. Schema đích gồm đúng 15 bảng nghiệp vụ theo [Từ điển dữ liệu SQL Server](../docs/requirements/SQL_SERVER_DATA_DICTIONARY.md). Hibernate dùng `ddl-auto=validate`; chỉ Flyway quản lý DDL.

## Automatic local reset on every application launch

`DB_RESET_ON_STARTUP` defaults to `true`. Before creating the datasource, the application connects to `master`, disconnects existing users from the selected local database, drops it if present and creates it again. Flyway then rebuilds the schema. **All previous data and both old and new Flyway history tables in that database are deleted.** A failed migration does not restore the database deleted by the initializer.

Only `Housekeeping_Database` and `home_cleaning_demo` on `localhost`, `127.0.0.1` or `[::1]` can be reset. The SQL login needs ALTER/DROP/CREATE DATABASE permissions. Set `DB_RESET_ON_STARTUP=false` in the module `.env` to preserve data or connect to a remote/shared database. The historical setup and manual recovery instructions below apply when automatic reset is disabled; no manual setup/reset script is required when it is enabled.

## Database demo tách biệt

1. Chỉ dùng môi trường SQL Server local dành cho demo/dev.
2. Chạy [`setup-demo.sql`](setup-demo.sql) bằng SSMS để tạo database mới `home_cleaning_demo` nếu chưa tồn tại. Script không tạo login hoặc chứa password.
3. Chạy ứng dụng với profile `demo`; Flyway áp dụng baseline và migration dữ liệu mẫu trong `classpath:db/refactor`. Dữ liệu mẫu được ghi vào database demo bởi migration V3, không được tạo bởi lớp Java lúc khởi động.

Tạo SQL login riêng và map vào database demo; cấp quyền DDL cần thiết để Flyway tạo schema. Lưu credentials trong `.env` cục bộ, không commit. Script không xóa database, không tạo hoặc reset login, không tạo schema history thủ công và không sửa database cũ. Nếu `home_cleaning_demo` đã có schema hoặc Flyway history không phù hợp, dừng lại và dùng database dev mới; không chạy `clean`, không drop và không sửa thủ công history.

## Flyway và lịch sử schema

- `app/housekeeping-management-system/src/main/resources/db/primary/` là baseline của database chính `Housekeeping_Database`; Flyway ghi nhận lịch sử mới trong `flyway_primary_schema_history` để không sửa hay repair lịch sử cũ. Flyway tạo baseline version `0` rồi áp dụng V1; migration V1 chỉ cho phép database chính/acceptance rỗng (ngoại trừ hai bảng lịch sử Flyway).
- `app/housekeeping-management-system/src/main/resources/db/refactor/` giữ baseline cho profile demo và bộ kiểm thử của profile đó.
- `db/migration/V1__create_initial_schema.sql`, `V2__add_momo_payment_attempts.sql`, `V3__replace_momo_with_payos.sql` là lịch sử của mô hình cũ. Giữ nguyên để bảo toàn lịch sử; không chỉnh nội dung migration đã áp dụng.
- Database cũ dùng schema/model trước đây không tự động chuyển đổi. Nếu chủ sở hữu xác nhận bỏ dữ liệu cũ trên database chính, chạy [`recreate-primary-schema.sql`](recreate-primary-schema.sql) một lần trong đúng `Housekeeping_Database`; script giữ nguyên lịch sử `flyway_schema_history` cũ, xóa toàn bộ bảng ứng dụng và dữ liệu (kể cả lịch sử Flyway mới nếu có), đồng thời kiểm tra đúng tên database. Sau đó chạy ứng dụng mặc định để baseline mới tạo 15 bảng. Không chạy script ở môi trường có dữ liệu cần giữ.
- `database/schema.sql` chỉ còn guard báo lỗi cho đường dẫn schema cũ; không dùng làm DDL. Migration Flyway tại `db/primary` là nguồn DDL chính thức cho ứng dụng mặc định.
- Flyway `flyway_schema_history` là bảng tooling, không nằm trong 15 bảng nghiệp vụ.

## Cấu hình riêng

Trong `.env` ở thư mục Maven module, đặt `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` cho `Housekeeping_Database`. Không dùng credentials production và không commit `.env`. Profile demo tùy chọn trỏ cố định tới `home_cleaning_demo`, không lấy URL từ `.env`.

## Kiểm chứng

GitHub Actions chạy `bash ./mvnw -B verify` trên JDK 21 và Docker; job này bao gồm nhóm Testcontainers SQL Server. Chỉ coi migration, Hibernate mapping và concurrency đã được xác minh khi job kiểm thử ứng dụng hoàn tất thành công.

```powershell
# Unit/MVC, không cần SQL Server
mvn "-DexcludedGroups=sqlserver" verify

# Flyway, Hibernate mapping và concurrency trên SQL Server Testcontainers; cần Docker
mvn verify
```

Không kết luận migration hoặc concurrency đã được SQL Server xác minh nếu Testcontainers/SQL Server tests không chạy. Xem [README](../README.md) để chạy ứng dụng.
