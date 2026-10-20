> **Document:** Shared Demo Accounts\
> **File:** `docs/demo-setup.md`\
> **Version:** v1.1.0\
> **Created:** 2026-10-17\
> **Last Updated:** 2026-10-17\
> **Status:** Draft

# Clone Project Và Đăng Nhập Bằng Tài Khoản Demo

Profile **`demo`** tự tạo ba tài khoản ứng dụng dưới đây sau khi Flyway khởi tạo schema. Đây là thông tin mẫu công khai để các thành viên clone từ GitHub và chạy trên máy của mình; không phải thông tin đăng nhập SQL Server.

**Quy trình mặc định: chạy [setup-demo.sql](../database/setup-demo.sql) trong SSMS → Run `Housekeeping Demo` trong IntelliJ → đăng nhập. Không cần tạo `.env`, nhập SQL password trong IntelliJ hoặc baseline Flyway.**

| Vai trò | Email | Mật khẩu demo |
|---|---|---|
| Admin | `admin@demo.gonnha.local` | `Demo@123456` |
| Customer | `customer@demo.gonnha.local` | `Demo@123456` |
| Staff | `staff@demo.gonnha.local` | `Demo@123456` |

## Thông tin nằm ở đâu

Các đường dẫn dưới đây tính từ thư mục gốc repository. Khi mở project bằng IntelliJ, có thể mở trực tiếp các file này để xem hoặc chỉnh cấu hình.

| Thông tin cần tìm | File |
|---|---|
| Điểm bắt đầu cho người mới clone | [README.md](../README.md) |
| SQL tạo database và SQL login demo dùng sẵn | [database/setup-demo.sql](../database/setup-demo.sql) |
| Tài khoản ba vai trò và các bước chạy | [docs/demo-setup.md](demo-setup.md) — tài liệu này |
| Mẫu kết nối SQL Server để sao chép thành `.env` | [app/housekeeping-management-system/.env.example](../app/housekeeping-management-system/.env.example) |
| Cấu hình Run demo trong IntelliJ | [Housekeeping Demo - Reset DB.run.xml](<../.run/Housekeeping Demo - Reset DB.run.xml>) |
| Cấu hình profile `demo` và đọc `.env` | [application-demo.properties](../app/housekeeping-management-system/src/main/resources/application-demo.properties) |
| Migration nạp tài khoản, dịch vụ và booking mẫu | [R__demo_data.sql](../app/housekeeping-management-system/src/main/resources/db/migration/R__demo_data.sql) |
| Mã tự tạo ba tài khoản demo | [DemoAccountSeeder.java](../app/housekeeping-management-system/src/main/java/com/housekeeping/management/demo/DemoAccountSeeder.java) |
| Hướng dẫn schema, migration và baseline database | [database/README.md](../database/README.md) |

File `.env` chỉ cần khi dùng kết nối khác mặc định; đặt tại `app/housekeeping-management-system/.env` và không đưa lên GitHub. Email/mật khẩu ở bảng trên dùng để đăng nhập website, còn SQL login bên dưới dùng để ứng dụng kết nối SQL Server.

## Setup trên máy mới

1. Chuẩn bị **JDK 21**, SQL Server và SSMS. SQL Server phải bật **SQL Server and Windows Authentication mode** (Mixed Mode) và **TCP/IP cổng 1433**. Với SQL Express, cũng phải bật TCP/IP cho đúng instance `SQLEXPRESS`; xem phần xử lý lỗi bên dưới.
2. Trong SSMS, kết nối đúng instance bằng tài khoản quản trị (ví dụ Windows Authentication), mở [database/setup-demo.sql](../database/setup-demo.sql) và **Execute / F5 toàn bộ file**. Script tạo database riêng `housekeeping_demo`, SQL login `housekeeping_demo_app` và quyền cần để chạy Flyway. Có thể chạy lại; không xóa dữ liệu hoặc đặt lại password đã có.
3. Mở `app/housekeeping-management-system/pom.xml` bằng IntelliJ dưới dạng Maven project, chọn Project SDK **Java 21** và chờ Maven tải dependencies/import hoàn tất.
4. Mở root repository trong IntelliJ và chọn cấu hình **Housekeeping Demo - Reset DB**. Cấu hình này dùng working directory của module để nạp `.env`, bật profile `demo`, đặt `DB_RESET_ON_STARTUP=true` và mở trang chủ sau khi build. Mỗi lần chạy, ứng dụng tạo database nếu chưa tồn tại rồi xóa schema cấu hình để Flyway tạo lại bảng và dữ liệu demo. Profile `demo` có kết nối mặc định khớp script SQL nếu `.env` không ghi đè. Flyway nạp dịch vụ, tài khoản và booking mẫu từ `R__demo_data.sql`; không chạy `schema.sql` hoặc script tạo bảng thủ công trước bước này.
5. Khi log báo ứng dụng đã khởi động, mở [đăng nhập](http://localhost:8080/login). Admin tới `/admin/bookings`, Customer tới `/customer/bookings`, Staff tới `/staff/bookings`. IntelliJ có bước tự mở trình duyệt; nếu trình duyệt mở trước khi server sẵn sàng, tải lại trang.

### Kết nối mặc định đã chuẩn bị sẵn

| Thuộc tính | Giá trị demo công khai |
|---|---|
| SQL Server | `localhost:1433` |
| Database | `housekeeping_demo` |
| SQL login | `housekeeping_demo_app` |
| SQL password | `HousekeepingDemo@2026` |
| Website | `http://localhost:8080/` |

Database demo riêng giúp giữ nguyên database `hsf302` hiện có. SQL login chỉ có quyền kết nối, đọc/ghi và DDL trong database demo; không được cấp `sysadmin`. Thông tin mẫu chỉ dành cho demo local.

### Chỉ chỉnh khi máy dùng SQL khác mặc định

Nếu cần dùng cổng khác, SQL Server ở máy khác hoặc database/login riêng: sao chép `.env.example` thành `.env` trong module rồi điền đủ `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`. Không cần `.env` khi dùng script và cổng mặc định. Giá trị `DB_*` trong biến môi trường hoặc `.env` sẽ thay thế mặc định demo; kiểm tra chúng nếu ứng dụng kết nối nhầm database.

### Nếu SQL Server chưa bật đăng nhập SQL hoặc TCP

- **Mixed Mode:** SSMS → nhấp phải server → Properties → Security → chọn **SQL Server and Windows Authentication mode** → OK → khởi động lại dịch vụ SQL Server, rồi chạy lại script.
- **TCP:** SQL Server Configuration Manager → SQL Server Network Configuration → Protocols for đúng instance → bật **TCP/IP**. Trong TCP/IP Properties → IP Addresses → IPAll, xóa `TCP Dynamic Ports`, đặt `TCP Port = 1433`, bảo đảm cổng này chưa được instance khác dùng, rồi khởi động lại dịch vụ SQL Server. Nếu máy có nhiều instance, chọn cổng riêng và sửa `DB_URL` trong `.env`.
- **Connection refused / timeout:** kiểm tra đúng instance đang chạy, TCP và cổng; việc SSMS kết nối được bằng Windows Authentication chưa xác nhận kết nối TCP của Java hoạt động.
- **Login failed:** kiểm tra đã chạy script trên instance đang nghe cổng 1433, Mixed Mode đã bật, và không có `.env`/`DB_*` cũ ghi đè.
- **Port 8080 already in use:** dừng project khác đang dùng cổng, hoặc thêm `--server.port=8082` vào Program arguments rồi mở cổng mới.
- **Không thấy Housekeeping Demo:** mở đúng module Maven; có thể tạo Application configuration với main class `com.housekeeping.management.HousekeepingManagementSystemApplication`, module classpath `housekeeping-management-system`, working directory là thư mục chứa `pom.xml`, Program arguments `--spring.profiles.active=demo`.

Chạy bằng Maven Wrapper từ thư mục `app/housekeeping-management-system/`:

```powershell
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=demo"
```

CLI phục vụ cùng giao diện tại `http://localhost:8080/`; bước tự mở trình duyệt thuộc cấu hình Run của IntelliJ.

Nếu database đã có bảng tạo thủ công từ đúng schema V1 nhưng chưa có `flyway_schema_history`, làm bước baseline một lần theo [hướng dẫn database](../database/README.md). Database đã được Flyway quản lý thì không bật lại baseline.

## Cách tạo và giữ tài khoản

- `R__demo_data.sql` chỉ nạp tài khoản và dữ liệu mẫu khi bật profile `demo`; profile thông thường không nhận dữ liệu demo. `DemoAccountSeeder` tiếp tục kiểm tra và bổ sung tài khoản nếu thiếu.
- Chỉ tạo tài khoản còn thiếu, mã hóa mật khẩu bằng PasswordEncoder BCrypt của ứng dụng và tái sử dụng khóa kiểm tra email duy nhất giữa ba bảng.
- Khi không bật reset, chạy lại không tạo trùng và không đổi mật khẩu hoặc thông tin của tài khoản đã có. Nếu bật `DB_RESET_ON_STARTUP=true`, schema và dữ liệu bị xóa trước, rồi seeder tạo lại ba tài khoản mẫu với mật khẩu công khai.
- Nếu email demo đã thuộc vai trò khác hoặc trùng giữa nhiều bảng, ứng dụng báo lỗi thay vì tự cấp/chuyển vai trò.
- Không thay Entity, schema hay migration V1; seeder không tạo SQL login hoặc seed lịch đặt/thanh toán. Admin có thể thêm dịch vụ và khoảng sẵn sàng qua giao diện hiện có. Tùy chọn `DB_RESET_ON_STARTUP=true` xóa dữ liệu trong schema trước khi khởi động.

Chỉ bật profile này cho môi trường demo của nhóm. Mật khẩu website và SQL mẫu ở trên là dữ liệu công khai, không dùng cho môi trường triển khai thật. Thông tin SQL Server riêng, `.env`, `.runtime/`, log và file build riêng không đưa lên GitHub. Những file cần chia sẻ gồm `database/setup-demo.sql`, source seeder, `application.properties`, `application-demo.properties`, `.env.example`, cấu hình `.run/Housekeeping Demo.run.xml` và tài liệu này.

Trên máy đã setup, **Housekeeping Full Stack** có thể bật `local,demo` để dùng kết nối riêng trong `.runtime/` và thêm bộ tài khoản công khai này. Bộ tài khoản local cũ `*@gonnha.local` vẫn được giữ nguyên; người clone dùng bộ `*@demo.gonnha.local` ở trên.

## Xác minh ngày 2026-10-17

- `mvn -o -B -DexcludedGroups=sqlserver verify` bằng JDK 21: 23 test pass, gồm 5 test seeder và 18 test web/identity/ownership.
- Quy trình setup mới: `setup-demo.sql` chạy thành công lần đầu và chạy lại trên SQL Server; đăng nhập bằng SQL login mẫu qua TCP 1433 thành công. JAR profile `demo` chạy không `.env`, không `DB_*`, Flyway V1 thành công trên database `housekeeping_demo`; cả ba tài khoản đăng nhập HTTP 200 và Customer bị chặn HTTP 403 khi vào trang Admin. Dùng cổng 8081 cho kiểm tra vì 8080 đang có ứng dụng khác.
- Chạy lại script sau khi Flyway đã migrate và khởi động lại ứng dụng: SQL xác nhận vẫn đúng ba tài khoản, hash mật khẩu không thay đổi; đăng nhập lại cả ba vai trò thành công. Phiên server kiểm tra 8081 đã dừng, database `housekeeping_demo` được giữ sẵn để dùng lần sau; ứng dụng khác trên 8080 được giữ nguyên.
- Cấu hình Run `Housekeeping Demo` bật cùng profile `demo` như JAR đã kiểm tra; lượt setup này chưa bấm Run lại trong giao diện IntelliJ.
- Chưa chạy nhóm kiểm thử Docker/Testcontainers trong lượt này; chưa commit hoặc push GitHub.
