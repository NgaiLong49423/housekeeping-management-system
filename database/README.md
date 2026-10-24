> **Document:** Database Guide\
> **File:** `database/README.md`\
> **Version:** v2.2.0\
> **Created:** 2026-06-14\
> **Last Updated:** 2026-10-25\
> **Status:** Draft\

# Hướng dẫn database

Microsoft SQL Server là database chính thức. Schema đích gồm đúng 15 bảng nghiệp vụ theo [Từ điển dữ liệu SQL Server](../docs/requirements/SQL_SERVER_DATA_DICTIONARY.md). Hibernate dùng `ddl-auto=validate`; chỉ Flyway quản lý DDL.

## Database demo tách biệt

1. Chỉ dùng môi trường SQL Server local dành cho demo/dev.
2. Chạy [`setup-demo.sql`](setup-demo.sql) bằng SSMS để tạo database mới `home_cleaning_demo` nếu chưa tồn tại. Script không tạo login hoặc chứa password.
3. Chạy ứng dụng với profile `demo`; Flyway áp dụng baseline mới trong `classpath:db/refactor`.

Tạo SQL login riêng và map vào database demo; cấp quyền DDL cần thiết để Flyway tạo schema. Lưu credentials trong `.env` cục bộ, không commit. Script không xóa database, không tạo hoặc reset login, không tạo schema history thủ công và không sửa database cũ. Nếu `home_cleaning_demo` đã có schema hoặc Flyway history không phù hợp, dừng lại và dùng database dev mới; không chạy `clean`, không drop và không sửa thủ công history.

## Flyway và lịch sử schema

- `app/housekeeping-management-system/src/main/resources/db/refactor/` là migration baseline được ứng dụng hiện tại sử dụng cho mô hình 15 bảng SQL Server.
- `db/migration/V1__create_initial_schema.sql`, `V2__add_momo_payment_attempts.sql`, `V3__replace_momo_with_payos.sql` là lịch sử của mô hình cũ. Giữ nguyên để bảo toàn lịch sử; không chỉnh nội dung migration đã áp dụng.
- Database cũ dùng schema/model trước đây không tự động chuyển đổi. Chiến lược bảo toàn/migration dữ liệu cũ vẫn là TBD. Ứng dụng không được reset hoặc migrate database đó trong quá trình triển khai baseline mới.
- `database/schema.sql` chỉ còn guard báo lỗi cho đường dẫn schema cũ; không dùng làm DDL. Migration Flyway tại `db/refactor` là nguồn DDL mới chính thức.
- Flyway `flyway_schema_history` là bảng tooling, không nằm trong 15 bảng nghiệp vụ.

## Cấu hình riêng

Trong `.env` ở thư mục Maven module, đặt `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` cho database development mới. Không dùng credentials production và không commit `.env`. Profile demo trỏ cố định tới `home_cleaning_demo`, không lấy URL từ `.env`.

## Kiểm chứng

GitHub Actions chạy `bash ./mvnw -B verify` trên JDK 21 và Docker; job này bao gồm nhóm Testcontainers SQL Server. Chỉ coi migration, Hibernate mapping và concurrency đã được xác minh khi job kiểm thử ứng dụng hoàn tất thành công.

```powershell
# Unit/MVC, không cần SQL Server
mvn "-DexcludedGroups=sqlserver" verify

# Flyway, Hibernate mapping và concurrency trên SQL Server Testcontainers; cần Docker
mvn verify
```

Không kết luận migration hoặc concurrency đã được SQL Server xác minh nếu Testcontainers/SQL Server tests không chạy. Xem [README](../README.md) để chạy ứng dụng.
