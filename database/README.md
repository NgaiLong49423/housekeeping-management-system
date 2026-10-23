> **Document:** Database Starter Guide\
> **File:** `database/README.md`\
> **Version:** v1.3.0\
> **Created:** 2026-06-14\
> **Last Updated:** 2026-10-17\
> **Status:** Draft

# Hướng dẫn database

## Người mới clone: chạy SQL rồi Run trong IntelliJ

1. Chuẩn bị SQL Server bật Mixed Mode và TCP/IP cổng 1433.
2. Trong SSMS, kết nối đúng instance bằng tài khoản quản trị, mở [setup-demo.sql](setup-demo.sql) rồi Execute toàn bộ file.
3. Mở `app/housekeeping-management-system/pom.xml` trong IntelliJ, chọn Java 21, Run **Housekeeping Demo**.
4. Flyway tạo bảng; ứng dụng tự tạo ba tài khoản website. Mặc định dữ liệu được giữ giữa các lần chạy. Xem [tài khoản và hướng dẫn đầy đủ](../docs/demo-setup.md).

Không cần tạo `.env` hoặc nhập thông tin SQL khi dùng kết nối mặc định:

| Thông tin SQL mẫu công khai | Giá trị |
|---|---|
| Server | `localhost:1433` |
| Database | `housekeeping_demo` |
| SQL login | `housekeeping_demo_app` |
| SQL password | `HousekeepingDemo@2026` |

Đây là thông tin kết nối database dành cho demo local. Tài khoản website Admin/Customer/Staff nằm trong [hướng dẫn demo](../docs/demo-setup.md), có mật khẩu khác SQL login.

Script có thể chạy lại, không xóa dữ liệu, không đổi mật khẩu login đã có. Nếu login có cùng tên nhưng mật khẩu/trạng thái khác mặc định, script dừng để người dùng xử lý cấu hình riêng. Script chỉ tạo database/login/user và cấp quyền đọc, ghi, DDL trong database demo, không cấp sysadmin cho ứng dụng. Khi khởi động ứng dụng, Flyway tạo bảng; không cần chèn user bằng SQL.

Nếu tạo database thành công nhưng bước cấp login/quyền lỗi, database có thể vẫn tồn tại. Sửa lỗi rồi chạy lại toàn bộ script. Mixed Mode hoặc TCP/IP cần cấu hình trong SQL Server/Configuration Manager và khởi động lại dịch vụ; script không tự thay cấu hình dịch vụ. Xem [cách xử lý lỗi kết nối](../docs/demo-setup.md#nếu-sql-server-chưa-bật-đăng-nhập-sql-hoặc-tcp).

## Schema và migration

- SQL Server là hệ quản trị đã chọn.
- Các migration Flyway có phiên bản là nguồn chính thức duy nhất quản lý schema: [V1](../app/housekeeping-management-system/src/main/resources/db/migration/V1__create_initial_schema.sql) tạo schema baseline; [V2](../app/housekeeping-management-system/src/main/resources/db/migration/V2__add_momo_payment_attempts.sql) bổ sung bảng theo dõi giao dịch MoMo mà không sửa V1.
- Mặc định ứng dụng không xóa dữ liệu khi khởi động. Có thể bật tùy chọn local `DB_RESET_ON_STARTUP=true` trong `.env`: ứng dụng tạo database từ `DB_URL` nếu chưa tồn tại (tài khoản kết nối cần quyền `CREATE DATABASE`), rồi Flyway xóa các object trong schema cấu hình và chạy lại migration. Toàn bộ dữ liệu trong schema sẽ bị mất; chỉ bật với database local dành riêng cho phát triển.
- [schema.sql](schema.sql) là snapshot tham khảo, không chạy để bootstrap database demo.
- [sample-data.sql](sample-data.sql) và [queries.sql](queries.sql) là file tham khảo; không bắt buộc cho quy trình demo.
- `setup-demo.sql` không sao chép DDL của V1, không tạo/chèn `flyway_schema_history`, không bật baseline. Vì thế chỉ cần chạy script rồi bật ứng dụng để Flyway quản lý từ đầu.

Database demo riêng `housekeeping_demo` không thay đổi dữ liệu trong `hsf302` hoặc kết nối private của cấu hình **Housekeeping Full Stack**. Nếu dùng database khác, sao chép [`.env.example`](../app/housekeeping-management-system/.env.example) thành `.env` trong module rồi nhập đủ ba biến `DB_*`. Mỗi người giữ thông tin kết nối riêng ngoài Git.

## Database cũ đã tạo bảng thủ công

Chỉ dành cho database đã có bảng khớp hoàn toàn V1 nhưng chưa có lịch sử Flyway; không áp dụng cho database demo rỗng. Trỏ đúng `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` tới database đó. Trong lần khởi động tiếp nhận schema duy nhất, thêm Program arguments:

```text
--spring.flyway.baseline-on-migrate=true --spring.flyway.baseline-version=1
```

Sau khi thành công, bỏ hai tham số baseline. Không tự chèn lịch sử Flyway hoặc chạy lại schema. Nếu schema không khớp V1, cần kiểm tra và xử lý sai khác trước khi baseline; baseline không xác minh cấu trúc thay cho Hibernate validate. Máy local hiện tại đã có schema history cho `hsf302`, không cần baseline lại.
