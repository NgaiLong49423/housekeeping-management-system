> **Document:** Housekeeping Management System\
> **File:** `README.md`\
> **Version:** v1.3.0\
> **Created:** 2026-06-14\
> **Last Updated:** 2026-10-17\
> **Status:** Draft

# Housekeeping Management System

Ứng dụng HSF302 cho một công ty giúp việc: Customer tự chọn Staff part-time và đặt dịch vụ; Staff lưu bằng chứng, hoàn thành công việc; Admin quản lý danh mục/khoảng sẵn sàng và xác minh thanh toán thủ công. Khách hàng cũng có thể thanh toán trực tuyến qua PayOS.

**Mới clone project từ GitHub?** Bắt đầu tại [hướng dẫn setup và tài khoản demo](docs/demo-setup.md): có email/mật khẩu cho cả ba vai trò, cách kết nối SQL Server và cách chạy full stack trong IntelliJ. Xem [vị trí các file cấu hình](docs/demo-setup.md#thông-tin-nằm-ở-đâu) khi cần chỉnh lại.

| Hạng mục | Công nghệ đã chốt |
|---|---|
| Nền tảng | Java 21, Spring Boot 4.1.1, Maven |
| Giao diện | Spring MVC, Thymeleaf, Bootstrap 5.3.8 |
| Dữ liệu | SQL Server, Spring Data JPA/Hibernate, Flyway |
| Xác thực | Spring Security, HTTP Session, BCrypt |

## Chạy ứng dụng

**Người mới clone — chạy demo nhanh:**

1. Chuẩn bị JDK 21 và SQL Server bật Mixed Mode, TCP cổng **1433**.
2. Trong SSMS, dùng tài khoản quản trị chạy toàn bộ [database/setup-demo.sql](database/setup-demo.sql). Script tạo database `housekeeping_demo` và SQL login dùng sẵn.
3. Mở root repository trong IntelliJ, chọn SDK 21 và bảo đảm Maven module từ [pom.xml](app/housekeeping-management-system/pom.xml) đã được import.
4. Chọn **Housekeeping Demo - Reset DB** trên thanh Run rồi bấm Run. Cấu hình này bật profile `demo`, nạp `.env` trong module, reset schema và dữ liệu mỗi lần chạy, rồi tự mở trang chủ sau khi build.
5. Mở [trang đăng nhập](http://localhost:8080/login) và dùng tài khoản ở bảng bên dưới. Flyway tự tạo bảng và nạp dữ liệu demo; migration seed chỉ hoạt động trong profile `demo`.

Mặc định ứng dụng giữ dữ liệu giữa các lần chạy. Cấu hình IntelliJ **Housekeeping Demo - Reset DB** bật reset riêng cho lần chạy đó. Khi chạy bằng cấu hình khác, đặt `DB_RESET_ON_STARTUP=true` trong `.env` của module nếu muốn reset. Tính năng này xóa toàn bộ dữ liệu trong schema đã cấu hình; chỉ dùng với database local dành riêng cho demo.

Không cần tạo `.env` với cách chạy này. Nếu SQL dùng cổng khác hoặc chưa bật Mixed Mode/TCP, xem [hướng dẫn chi tiết và xử lý lỗi](docs/demo-setup.md). Cấu hình **Housekeeping Full Stack** bên dưới dành cho máy local đã setup riêng.

**Máy đã được setup:** xem [hướng dẫn chạy local](docs/local-setup.md). Mở [ứng dụng](http://localhost:8080/) hoặc chạy `run-local.cmd` để dùng JDK 21 và database riêng đã cấu hình.

1. Dùng JDK 21 và Maven; kiểm tra `java -version` và `mvn -version`. Máy mặc định trỏ JDK 17 cần đổi `JAVA_HOME` trước khi chạy.
2. Chuẩn bị SQL Server và database rỗng hoặc database đã áp dụng migration tương ứng. Flyway quản lý migration; `ddl-auto=validate` không tự thay đổi schema.
3. Thiết lập biến môi trường `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` theo môi trường của bạn; không ghi mật khẩu thật vào mã nguồn.
4. Mở thư mục `app/housekeeping-management-system/`, chạy `mvn spring-boot:run`.
5. Truy cập [trang chủ](http://localhost:8080/). Tài khoản Customer/Staff/Admin dùng chung [trang đăng nhập](http://localhost:8080/login).

Giao diện sử dụng dữ liệu thật từ database. Không nhập dữ liệu mock, lịch định kỳ, tự phân công nhân viên hoặc thanh toán tự động từ thiết kế tham khảo. Không cần npm/Vite hay cấu hình CORS cho frontend riêng.

## Tài khoản demo cho người clone từ GitHub

Chạy [setup-demo.sql](database/setup-demo.sql), chọn JDK 21 rồi Run **Housekeeping Demo** theo [hướng dẫn demo](docs/demo-setup.md). Kết nối SQL mẫu đã có sẵn trong profile `demo`; `.env` chỉ cần khi muốn đổi kết nối. Ứng dụng tự tạo các tài khoản dưới đây nếu còn thiếu, lưu mật khẩu BCrypt và giữ nguyên tài khoản đã tồn tại.

| Vai trò | Email | Mật khẩu demo |
|---|---|---|
| Admin | `admin@demo.gonnha.local` | `Demo@123456` |
| Customer chính | `customer@demo.gonnha.local` | `Demo@123456` |
| Customer bổ sung | `customer01@demo.gonnha.local` đến `customer07@demo.gonnha.local` | `Demo@123456` |
| Staff chính | `staff@demo.gonnha.local` | `Demo@123456` |
| Staff bổ sung | `staff01@demo.gonnha.local` đến `staff07@demo.gonnha.local` | `Demo@123456` |

Flyway nạp tổng cộng 8 Customer, 8 Staff, 8 dịch vụ và 20 booking để trình bày các trạng thái nghiệp vụ. Xem [danh sách tài khoản và hướng dẫn demo](docs/demo-setup.md) để dùng các tài khoản bổ sung.

Đây là tài khoản ứng dụng mẫu công khai, chỉ bật cho môi trường demo. SQL login mẫu là `housekeeping_demo_app` / `HousekeepingDemo@2026`, database `housekeeping_demo`; đây là tài khoản kết nối database, khác ba tài khoản website. Người clone vẫn cần SQL Server trên máy của họ; không chia sẻ `.env` hoặc `.runtime/` chứa thông tin kết nối riêng.

## Cấu trúc

```text
app/housekeeping-management-system/
  src/main/java/com/housekeeping/management/
    domain/       # Entity và trạng thái đã có
    repository/   # Repository đã có
    service/      # Nghiệp vụ đã có
    web/          # MVC, session, view model và lớp nối giao diện
  src/main/resources/
    templates/    # Giao diện Thymeleaf
    static/       # CSS, JavaScript, ảnh và Bootstrap cục bộ
    db/migration/ # Migration hiện có
```

## Kiểm tra

Từ thư mục chứa `pom.xml`:

```powershell
# Render, form, session và phân quyền; không cần SQL Server
mvn "-DexcludedGroups=sqlserver" verify

# Toàn bộ, bao gồm Testcontainers SQL Server; cần Docker
mvn verify
```

Test web dùng fixture trong test, không thay thế nghiệm thu với SQL Server thật. Xem [hướng dẫn tích hợp](docs/frontend-integration.md) để cấu hình tài khoản và kiểm tra luồng đầy đủ.

## Tài liệu

- [Clone và đăng nhập bằng ba tài khoản demo](docs/demo-setup.md)
- [Môi trường local đã setup và tài khoản demo](docs/local-setup.md)
- [Kết nối frontend–backend và nghiệm thu](docs/frontend-integration.md)
- [PRD](docs/requirements/PRD.md) và [SRS](docs/requirements/SRS.md)
- [Ứng dụng](app/README.md) và [database](database/README.md)
- [Quy trình đóng góp](CONTRIBUTING.md) và [changelog](CHANGELOG.md)
