> **Document:** Database Guide\
> **File:** `database/README.md`\
> **Version:** v2.6.0\
> **Created:** 2026-06-14\
> **Last Updated:** 2026-11-09\
> **Status:** Draft\

# Hướng dẫn database

Microsoft SQL Server là database chính thức. Schema đích gồm đúng 15 bảng nghiệp vụ theo [Từ điển dữ liệu SQL Server](../docs/requirements/SQL_SERVER_DATA_DICTIONARY.md). Hibernate dùng `ddl-auto=validate`; chỉ Flyway quản lý DDL.

## Opt-in automatic local reset

`DB_RESET_ON_STARTUP` defaults to `false`, preserving existing database contents. When explicitly set to `true`, before creating the datasource the application connects to `master`, disconnects existing users from the selected supported local database, drops it if present and creates it again. Flyway then rebuilds the schema. **All previous data and both old and new Flyway history tables in that database are deleted.** A failed migration does not restore the database deleted by the initializer.

Only `Housekeeping_Database` and `home_cleaning_demo` on `localhost`, `127.0.0.1` or `[::1]` can be reset. The SQL login needs ALTER/DROP/CREATE DATABASE permissions. Set `DB_RESET_ON_STARTUP=true` in the module `.env` only for an intentional disposable local reset; leave it false to preserve data or connect to a remote/shared database. The historical setup and manual recovery instructions below apply when automatic reset is disabled; no manual setup/reset script is required when it is enabled.

## Database demo tách biệt

1. Chỉ dùng môi trường SQL Server local dành cho demo/dev.
2. Chạy [`setup-demo.sql`](setup-demo.sql) bằng SSMS để tạo database mới `home_cleaning_demo` nếu chưa tồn tại. Script không tạo login hoặc chứa password.
3. Chạy ứng dụng với profile `demo`; Flyway áp dụng schema và dữ liệu mẫu từ `classpath:db/refactor` cùng migration dùng chung trong `classpath:db/shared`. Tài khoản, nhà và dịch vụ mẫu được ghi bởi V3; migration demo-only V7 tạo thêm một Booking để thử xác nhận đến. Booking bắt đầu khoảng 10 phút sau khi seed, hai bên có thể xác nhận từ 5 phút trước giờ bắt đầu. Đây là dữ liệu migration, không được tạo bởi lớp Java lúc khởi động.

Tạo SQL login riêng và map vào database demo; cấp quyền DDL cần thiết để Flyway tạo schema. Lưu credentials trong `.env` cục bộ, không commit. Script không xóa database, không tạo hoặc reset login, không tạo schema history thủ công và không sửa database cũ. Nếu `home_cleaning_demo` đã có schema hoặc Flyway history không phù hợp, dừng lại và dùng database dev mới; không chạy `clean`, không drop và không sửa thủ công history.

## Flyway và lịch sử schema

- `app/housekeeping-management-system/src/main/resources/db/primary/` là baseline của database chính `Housekeeping_Database`; Flyway ghi nhận lịch sử mới trong `flyway_primary_schema_history` để không sửa hay repair lịch sử cũ. Flyway tạo baseline version `0` rồi áp dụng V1; migration V1 chỉ cho phép database chính/acceptance rỗng (ngoại trừ hai bảng lịch sử Flyway).
- `app/housekeeping-management-system/src/main/resources/db/refactor/` giữ baseline cho profile demo và bộ kiểm thử của profile đó; V7 chỉ seed Booking thử trong `home_cleaning_demo`.
- `app/housekeeping-management-system/src/main/resources/db/shared/` chứa migration version dùng chung sau baseline; cả profile mặc định và `demo` nạp migration V2, V5, V6 và V8 từ đây. V5 thêm ngày hiệu lực cho phiên bản giờ hoạt động.
- Migration dùng chung V6 thêm thời điểm Staff xác nhận đến và báo sự cố trên `Bookings`; schema vẫn có 15 bảng nghiệp vụ. Báo sự cố chỉ được ghi trước giờ bắt đầu.
- Migration V8 thêm ETA, phản hồi đồng thuận của Customer, giờ Staff bắt đầu việc và thông tin giảm trừ do đến trễ trên `Bookings`; không thêm bảng nghiệp vụ. Từ chối sự cố hủy miễn phí trước khi bắt đầu; chưa phản hồi thì Staff chưa thể bắt đầu. Mức giảm 5%/10%/20% theo ngưỡng phút đã được phê duyệt, thanh toán vẫn diễn ra sau dịch vụ.
- Cửa sổ xác nhận đến mặc định là 0 phút (từ giờ bắt đầu); profile `demo` đặt thành 5 phút. Ngưỡng được kiểm tra ở cả giao diện và cập nhật có điều kiện trong database; thời điểm lưu vẫn là lúc người dùng bấm xác nhận.
- `db/migration/V1__create_initial_schema.sql`, `V2__add_momo_payment_attempts.sql`, `V3__replace_momo_with_payos.sql` là lịch sử của mô hình cũ. Giữ nguyên để bảo toàn lịch sử; không chỉnh nội dung migration đã áp dụng.
- Database cũ dùng schema/model trước đây không tự động chuyển đổi. Nếu chủ sở hữu xác nhận bỏ dữ liệu cũ trên database chính, chạy [`recreate-primary-schema.sql`](recreate-primary-schema.sql) một lần trong đúng `Housekeeping_Database`; script giữ nguyên lịch sử `flyway_schema_history` cũ, xóa toàn bộ bảng ứng dụng và dữ liệu (kể cả lịch sử Flyway mới nếu có), đồng thời kiểm tra đúng tên database. Sau đó chạy ứng dụng mặc định để baseline mới tạo 15 bảng. Không chạy script ở môi trường có dữ liệu cần giữ.
- `database/schema.sql` chỉ còn guard báo lỗi cho đường dẫn schema cũ; không dùng làm DDL. Baseline ở `db/primary` cùng migration tại `db/shared` là nguồn DDL cho ứng dụng mặc định.
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
