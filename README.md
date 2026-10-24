> **Document:** Housekeeping Management System README\
> **File:** `README.md`\
> **Version:** v2.0.0\
> **Created:** 2026-06-14\
> **Last Updated:** 2026-10-24\
> **Status:** Draft\

# Housekeeping Management System

Ứng dụng web đặt dịch vụ dọn dẹp nhà cho một công ty. Customer quản lý nhà và đặt lịch; Staff thực hiện công việc; Admin quản lý nhân viên, dịch vụ, giờ hoạt động và vận hành. Tài liệu chuẩn gồm [PRD](docs/requirements/PRD.md), [SRS](docs/requirements/SRS.md) và [Từ điển dữ liệu SQL Server](docs/requirements/SQL_SERVER_DATA_DICTIONARY.md).

## Công nghệ và kiến trúc

| Phần | Công nghệ |
|---|---|
| Backend | Java 21, Spring Boot 4.1.1, Spring MVC |
| Giao diện | Thymeleaf, Bootstrap |
| Lưu trữ | Spring Data JPA/Hibernate, Microsoft SQL Server |
| Migration | Flyway |
| Xác thực | Spring Security, HTTP Session, BCrypt |
| Cấu trúc | Một Maven project, MVC/N-Layer đơn giản |

Agent và thành viên nhóm cần theo [hướng dẫn cấu trúc package Java](docs/architecture/java-package-structure.md): `controller -> service -> repository -> SQL Server`, với DTO ở ranh giới ứng dụng và Entity ánh xạ dữ liệu.

## Các module

Authentication; Service Management; Staff Profile; Follow Staff; House Management; Booking Management; Staff Availability; Leave Management; Job Execution; Payment; Feedback; System Configuration.

Ba role là `CUSTOMER`, `STAFF`, `ADMIN`. Database có đúng 15 bảng nghiệp vụ theo Data Dictionary. Booking dùng các trạng thái `CONFIRMED`, `AWAITING_PAYMENT`, `COMPLETED`, `CANCELLED`. Payment `CASH` do Staff được giao xác nhận; Payment `ONLINE` qua PayOS được xác nhận bằng webhook hợp lệ.

## Chạy môi trường demo mới

Yêu cầu JDK 21 và SQL Server 2022+ bật TCP/IP, Mixed Mode, cổng 1433.

1. Mở SSMS và chạy [`database/setup-demo.sql`](database/setup-demo.sql). Script chỉ tạo database demo mới `home_cleaning_demo` nếu chưa tồn tại; không tạo login, nhúng password, xóa hay reset database khác.
2. Tạo SQL login riêng, chỉ cấp quyền vào database demo mới. Sao chép `.env.example` thành `.env`, rồi đặt `DB_USERNAME`, `DB_PASSWORD` và `DEMO_ACCOUNT_PASSWORD`; không commit `.env`.
3. Từ `app/housekeeping-management-system/`, chạy:

   ```powershell
   mvn "-Dspring-boot.run.profiles=demo" spring-boot:run
   ```

4. Mở [http://localhost:8080/login](http://localhost:8080/login). Seeder dùng `DEMO_ACCOUNT_PASSWORD` để tạo bốn tài khoản: `customer@demo.gonnha.local`, `staff@demo.gonnha.local` (part-time), `staff.fulltime@demo.gonnha.local` (full-time) và `admin@demo.gonnha.local`; không có mật khẩu mặc định trong source. Seeder chỉ bổ sung cấu hình công ty, giờ hoạt động, dịch vụ, nhà mẫu và lịch rảnh còn thiếu; dữ liệu đã tồn tại được giữ nguyên.

Demo profile dùng riêng database demo. Không bật thao tác Flyway `clean` hoặc trỏ URL demo sang database có dữ liệu cần giữ. Nếu migration báo schema/history không phù hợp, dừng lại và tạo một database development mới thay vì reset. Tài khoản đăng nhập và dữ liệu demo cần được xác minh trên SQL Server local trước buổi trình diễn.

Muốn tự cấu hình SQL Server, sao chép [`.env.example`](app/housekeeping-management-system/.env.example) thành `.env` trong thư mục Maven module và đặt `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` cho một database development riêng. Không đưa `.env` hay thông tin kết nối riêng lên Git.

## Kiểm tra

Từ thư mục chứa `pom.xml`:

```powershell
# Unit/MVC tests; không cần Docker hoặc SQL Server
mvn "-DexcludedGroups=sqlserver" verify

# Bao gồm migration, mapping và concurrency tests trên SQL Server Testcontainers; cần Docker
mvn verify
```

Test bằng fixture không thay thế xác minh trên SQL Server thật. Kết quả cần ghi rõ test SQL Server nào chạy được.

## Cấu trúc source

```text
app/housekeeping-management-system/
  src/main/java/com/housekeeping/management/
    config/ controller/ dto/request/ dto/response/
    entity/ repository/ service/ domain/
  src/main/resources/
    templates/ static/ db/refactor/
```

## Tài liệu liên quan

- [PRD](docs/requirements/PRD.md), [SRS](docs/requirements/SRS.md), [SQL Server Data Dictionary](docs/requirements/SQL_SERVER_DATA_DICTIONARY.md)
- [Kiến trúc package](docs/architecture/java-package-structure.md)
- [Hướng dẫn cài đặt/demo](docs/demo-setup.md), [hướng dẫn database](database/README.md), [hướng dẫn PayOS](docs/payos-payment.md)
- [Quy trình đóng góp](CONTRIBUTING.md)
