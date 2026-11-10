> **Document:** Database Guide\
> **File:** `database/README.md`\
> **Version:** v3.0.0\
> **Created:** 2026-06-14\
> **Last Updated:** 2026-11-10\
> **Status:** Draft\

# Hướng dẫn database

Microsoft SQL Server là database chính thức. Baseline tạo 15 bảng; các migration V10, V14 và V16 bổ sung 6 bảng, schema hiện tại gồm 21 bảng nghiệp vụ theo [Từ điển dữ liệu SQL Server](../docs/requirements/SQL_SERVER_DATA_DICTIONARY.md). Hibernate dùng `ddl-auto=validate`; chỉ Flyway quản lý DDL.

## Opt-in automatic local reset

`DB_RESET_ON_STARTUP` defaults to `false`, preserving existing database contents. When explicitly set to `true`, before creating the datasource the application connects to `master`, disconnects existing users from the selected supported local database, drops it if present and creates it again. Flyway then rebuilds the schema. **All previous data and both old and new Flyway history tables in that database are deleted.** A failed migration does not restore the database deleted by the initializer.

Only `Housekeeping_Database` and `home_cleaning_demo` on `localhost`, `127.0.0.1` or `[::1]` can be reset. The SQL login needs ALTER/DROP/CREATE DATABASE permissions. Set `DB_RESET_ON_STARTUP=true` in the module `.env` only for an intentional disposable local reset; leave it false to preserve data or connect to a remote/shared database. The historical setup and manual recovery instructions below apply when automatic reset is disabled; no manual setup/reset script is required when it is enabled.

## Database demo tách biệt

1. Chỉ dùng môi trường SQL Server local dành cho demo/dev.
2. Chạy [`setup-demo.sql`](setup-demo.sql) bằng SSMS để tạo database mới `home_cleaning_demo` nếu chưa tồn tại. Script không tạo login hoặc chứa password.
3. Chạy ứng dụng với profile `demo`; Flyway áp dụng schema và dữ liệu mẫu từ `classpath:db/refactor` cùng migration dùng chung trong `classpath:db/shared`. Tài khoản, nhà và dịch vụ mẫu được ghi bởi V3; migration demo-only V7 tạo thêm một Booking để thử xác nhận đến. Migration V11 seed `manager@demo.gonnha.local` trong riêng `home_cleaning_demo`; migration V15 chuyển tài khoản đó thành vai trò MANAGER duy nhất để duyệt độc lập với Admin đề xuất và quản lý tài khoản Admin. Booking bắt đầu khoảng 10 phút sau khi seed; Staff và Customer có thể xác nhận đã đến bất cứ lúc nào khi lịch còn CONFIRMED. Đây là dữ liệu migration, không được tạo bởi lớp Java lúc khởi động.

Tạo SQL login riêng và map vào database demo; cấp quyền DDL cần thiết để Flyway tạo schema. Lưu credentials trong `.env` cục bộ, không commit. Script không xóa database, không tạo hoặc reset login, không tạo schema history thủ công và không sửa database cũ. Nếu `home_cleaning_demo` đã có schema hoặc Flyway history không phù hợp, dừng lại và dùng database dev mới; không chạy `clean`, không drop và không sửa thủ công history.

## Flyway và lịch sử schema

- `app/housekeeping-management-system/src/main/resources/db/primary/` là baseline của database chính `Housekeeping_Database`; Flyway ghi nhận lịch sử mới trong `flyway_primary_schema_history` để không sửa hay repair lịch sử cũ. Flyway tạo baseline version `0` rồi áp dụng V1; migration V1 chỉ cho phép database chính/acceptance rỗng (ngoại trừ hai bảng lịch sử Flyway).
- `app/housekeeping-management-system/src/main/resources/db/refactor/` giữ baseline cho profile demo và bộ kiểm thử của profile đó; V7 chỉ seed Booking thử trong `home_cleaning_demo`.
- `app/housekeeping-management-system/src/main/resources/db/shared/` chứa migration version dùng chung sau baseline; cả profile mặc định và `demo` nạp migration V2, V5, V6, V8, V9, V10, V11, V12, V13, V14, V15 và V16 từ đây. V5 thêm ngày hiệu lực cho phiên bản giờ hoạt động.
- Migration dùng chung V6 thêm thời điểm Staff xác nhận đến và báo sự cố trên `Bookings`; schema vẫn có 15 bảng nghiệp vụ. Báo sự cố chỉ được ghi trước giờ bắt đầu.
- Migration V8 thêm ETA, phản hồi đồng thuận của Customer, giờ Staff bắt đầu việc và thông tin giảm trừ do đến trễ trên `Bookings`; không thêm bảng nghiệp vụ. Báo trễ là tùy chọn; nếu Staff đã gửi báo cáo thì cần Customer đồng ý trước khi bắt đầu. Migration V12 cho phép Staff bắt đầu sau khi xác nhận đã đến, kể cả trước giờ hẹn; nếu không báo trễ thì không cần Customer đồng thuận. Từ chối sự cố hủy miễn phí trước khi bắt đầu. Mức giảm 5%/10%/20% theo ngưỡng phút đã được phê duyệt, thanh toán vẫn diễn ra sau dịch vụ.
- Migration dùng chung V9 thêm rating và chủ đề cho `Feedbacks`, cho phép lời nhắn rỗng ở rating 4–5, giữ rating lịch sử là NULL và thêm trạng thái đã phản hồi trước khi resolve; không thêm bảng nghiệp vụ.
- Migration dùng chung V10 thêm hồ sơ khiếu nại, ảnh bằng chứng, lịch sử quyết định xử lý Staff và sổ khoản tiền; thêm cờ quản lý tạm thời và đánh dấu Payments ONLINE sandbox là mô phỏng. Tổng số bảng nghiệp vụ tăng từ 15 lên 19. Khoản tiền phải được Manager duyệt, khác người lập, và có file hóa đơn/biên nhận trước khi quyết toán.
- Migration dùng chung V11 chỉ seed Admin quản lý demo khi database hiện tại là `home_cleaning_demo`; không thay đổi dữ liệu hay schema của database chính.
- Migration dùng chung V12 thay `CK_Bookings_Work_Start`: work_started_at có thể trước start_time nhưng bắt buộc staff_arrived_at đã được ghi nhận. V12 không thêm bảng nghiệp vụ và không sửa migration V8 đã áp dụng.
- Migration dùng chung V13 thêm thời điểm Customer báo Staff chưa đến; chỉ Customer sở hữu Booking và Admin xem được. Báo cáo không tự chặn bắt đầu, hủy lịch hay đổi tiền và không thêm bảng nghiệp vụ.
- Migration dùng chung V14 thêm bảng `Booking_Delay_Compensation_Tiers` với các mức mặc định 1–15 phút/5%, 16–30/10%, trên 30/20%; Admin chỉnh ngưỡng và phần trăm tại `/admin/settings`. Tier phải liên tiếp từ phút 1 và mức cuối không giới hạn. Booking đã ghi nhận mức giảm giữ nguyên giá trị đã lưu. Bỏ giới hạn phần trăm cũ trong `Bookings`; sau V14 có 20 bảng nghiệp vụ.
- Migration dùng chung V15 chuyển Admin quản lý hiện tại thành role `MANAGER`, thay cờ V10 bằng vai trò hệ thống và filtered unique index để chỉ có một Manager. Manager kế thừa toàn quyền Admin, quản lý tài khoản Admin tại `/admin/accounts`, và là người duy nhất duyệt khoản tiền cùng quyết định nội bộ Staff. V15 giữ nguyên các bảng nghiệp vụ.
- `db/migration/V1__create_initial_schema.sql`, `V2__add_momo_payment_attempts.sql`, `V3__replace_momo_with_payos.sql` là lịch sử của mô hình cũ. Giữ nguyên để bảo toàn lịch sử; không chỉnh nội dung migration đã áp dụng.
- Database cũ dùng schema/model trước đây không tự động chuyển đổi. Nếu chủ sở hữu xác nhận bỏ dữ liệu cũ trên database chính, chạy [`recreate-primary-schema.sql`](recreate-primary-schema.sql) một lần trong đúng `Housekeeping_Database`; script giữ nguyên lịch sử `flyway_schema_history` cũ, xóa toàn bộ bảng ứng dụng và dữ liệu (kể cả lịch sử Flyway mới nếu có), đồng thời kiểm tra đúng tên database. Sau đó chạy ứng dụng mặc định để baseline tạo 15 bảng và migration áp dụng tiếp để đạt schema mới nhất theo Flyway (hiện 21 bảng nghiệp vụ). Không chạy script ở môi trường có dữ liệu cần giữ.
- `database/schema.sql` chỉ còn guard báo lỗi cho đường dẫn schema cũ; không dùng làm DDL. Baseline ở `db/primary` cùng migration tại `db/shared` là nguồn DDL cho ứng dụng mặc định.
- Flyway `flyway_schema_history` là bảng tooling, không nằm trong 21 bảng nghiệp vụ.

### Khởi tạo Manager hệ thống
Database demo seed tài khoản Manager riêng ở V11 và chuyển vai trò từ cờ cũ trong V15. Với database chính, nếu V15 không tìm thấy Admin đang có cờ quản lý để chuyển đổi, DBA cần gán vai trò cho đúng một tài khoản Admin đã xác minh (thay placeholder trước khi chạy):
```sql
UPDATE dbo.Users SET role='MANAGER'
WHERE email=N'<manager-admin-email>' AND role='ADMIN' AND status='ACTIVE';
```
Filtered unique index từ V15 ngăn tạo Manager thứ hai. Manager có thể tạo/khoá Admin tại `/admin/accounts`; Admin không được tự cấp quyền hoặc mở trang này. Nếu cần khôi phục Manager sau này, DBA phải chuyển tài khoản Admin đã xác minh bằng thao tác có kiểm soát.

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

- Migration dùng chung V16 thêm bảng `Feedback_Case_Messages` và liên kết bằng chứng hiện có qua `message_id`; schema có 21 bảng nghiệp vụ. Mô tả sự cố và reply cũ được nhập vào timeline; thời điểm reply cũ được đánh dấu không chính xác. Customer sở hữu booking và Admin/Manager xem/gửi tin, Staff và Customer khác không có quyền. Tin nhắn/ảnh append-only, hồ sơ đã giải quyết chỉ đọc.
