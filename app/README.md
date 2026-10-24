> **Document:** Application Directory Guide\
> **File:** `app/README.md`\
> **Version:** v1.4.0\
> **Created:** 2026-06-14\
> **Last Updated:** 2026-10-24\
> **Status:** Draft

# Ứng Dụng Housekeeping

Trên máy đã setup, dùng `run-local.cmd` ở thư mục gốc repository để tự nạp JDK 21 và cấu hình SQL Server riêng. Xem [hướng dẫn chạy local](../docs/local-setup.md).

Mã ứng dụng nằm trong [`housekeeping-management-system/`](housekeeping-management-system/). Dự án dùng Java 21, Spring Boot MVC, Thymeleaf và Bootstrap; không cần frontend React hoặc npm riêng.

Quy ước package Java và trách nhiệm từng layer được mô tả tại [Java Package Structure Guide](../docs/architecture/java-package-structure.md). Đây là cấu trúc định hướng cho code mới; xem hướng dẫn để hiểu cách áp dụng với các package `web/`, `domain/` đang tồn tại.

- `src/main/resources/templates/`: trang và fragment Thymeleaf.
- `src/main/resources/static/css/portal.css`: giao diện chuyển từ thiết kế ZIP.
- `src/main/resources/static/js/portal.js`: tạm tính, lọc danh sách và hỗ trợ nhập form.
- `src/main/resources/static/images/`: ảnh minh họa từ ZIP người dùng cung cấp.
- `src/main/resources/static/vendor/bootstrap/`: Bootstrap 5.3.8 và giấy phép MIT, phục vụ cục bộ.
- `src/main/java/com/housekeeping/management/web/`: MVC, view model, truy vấn hiển thị và lớp kết nối/xác thực.
- `src/main/java/com/housekeeping/management/service/`: các Service nghiệp vụ hiện có, được giữ nguyên.

Từ thư mục chứa `pom.xml`, cấu hình `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` cho SQL Server rồi chạy:

```powershell
mvn spring-boot:run
```

Mở [trang chủ](http://localhost:8080/) hoặc [đăng nhập](http://localhost:8080/login). Chạy thông thường cần tài khoản đã tồn tại trong database. Người mới clone muốn có sẵn ba vai trò: dùng SSMS chạy [setup-demo.sql](../database/setup-demo.sql), rồi Run **Housekeeping Demo** với JDK 21 hoặc thêm `-Dspring-boot.run.profiles=demo` vào lệnh Maven. Kết nối demo tới `localhost:1433/housekeeping_demo` đã cấu hình sẵn; không cần `.env` trừ khi đổi kết nối. Xem [tài khoản mẫu và cách clone](../docs/demo-setup.md).

**Làm mới database mỗi lần chạy (tùy chọn local):** đặt `DB_RESET_ON_STARTUP=true` trong `.env`. Ứng dụng sẽ tạo database từ `DB_URL` nếu chưa có, rồi Flyway xóa các object trong schema cấu hình và chạy lại migration. Toàn bộ dữ liệu trong schema sẽ mất mỗi lần chạy. Chỉ bật với database local dành riêng cho phát triển; tài khoản SQL cần quyền `CREATE DATABASE` nếu database chưa tồn tại. Mặc định tính năng này tắt.

Kiểm tra giao diện, đăng nhập và phân quyền không cần database:

```powershell
mvn "-DexcludedGroups=sqlserver" verify
```

Chạy toàn bộ kiểm thử tích hợp khi có Docker hoạt động:

```powershell
mvn verify
```

Xem [hướng dẫn kết nối frontend–backend](../docs/frontend-integration.md) để biết route, form, giới hạn và nghiệm thu với dữ liệu thật.
