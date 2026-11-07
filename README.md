> **Document:** Housekeeping Management System README\
> **File:** `README.md`\
> **Version:** v2.3.0\
> **Created:** 2026-06-14\
> **Last Updated:** 2026-10-27\
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

## Chạy database chính

Yêu cầu JDK 21 và SQL Server 2022+ bật TCP/IP, Mixed Mode, cổng 1433.

1. Start local SQL Server. The application connects to `Housekeeping_Database` by default and preserves its data. Set `DB_RESET_ON_STARTUP=true` only when you intentionally want to delete and recreate a supported local database, including all data and Flyway history; the SQL login then needs permission to alter, drop and create that database. Keep this value `false` for shared or existing databases.
2. Sao chép [`.env.example`](app/housekeeping-management-system/.env.example) thành `.env` trong thư mục Maven module, rồi đặt `DB_URL`, `DB_USERNAME` và `DB_PASSWORD` cho SQL Server chính. Địa chỉ nhà được nhập trực tiếp, không cần Google Maps API key. Không commit `.env`.
3. Từ `app/housekeeping-management-system/`, chạy:

   ```powershell
   mvn spring-boot:run
   ```

4. On startup, Flyway applies pending migrations to the selected database. The default profile uses `Housekeeping_Database` and the primary schema migrations; the `demo` profile uses `home_cleaning_demo` and its demo schema and seed migrations.

Flyway `clean` remains disabled. An intentional automatic reset only accepts `localhost`, `127.0.0.1` or `[::1]` and the database names `Housekeeping_Database` or `home_cleaning_demo`. Reset is off by default and applies to application launches through `main` (IntelliJ, Maven and the executable JAR), not ordinary unit tests.

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
    templates/ static/ db/primary/ db/refactor/ db/shared/
```

## Tài liệu liên quan

- [PRD](docs/requirements/PRD.md), [SRS](docs/requirements/SRS.md), [SQL Server Data Dictionary](docs/requirements/SQL_SERVER_DATA_DICTIONARY.md)
- [Kiến trúc package](docs/architecture/java-package-structure.md)
- [Hướng dẫn cài đặt/demo](docs/demo-setup.md), [hướng dẫn database](database/README.md), [hướng dẫn PayOS](docs/payos-payment.md)
- [Quy trình đóng góp](CONTRIBUTING.md)
