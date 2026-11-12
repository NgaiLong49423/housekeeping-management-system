> **Document:** Housekeeping Management System README\
> **File:** `README.md`\
> **Version:** v2.5.0\
> **Created:** 2026-06-14\
> **Last Updated:** 2026-11-10\
> **Status:** Draft\

# Housekeeping Management System

Ứng dụng web đặt dịch vụ dọn dẹp nhà cho một công ty. Customer quản lý nhà và đặt lịch; Staff thực hiện công việc; Admin vận hành dưới quyền một Manager duy nhất, người có toàn quyền Admin và quản lý tài khoản Admin. Tài liệu chuẩn gồm [PRD](docs/requirements/PRD.md), [SRS](docs/requirements/SRS.md) và [Từ điển dữ liệu SQL Server](docs/requirements/SQL_SERVER_DATA_DICTIONARY.md).

## Công nghệ và kiến trúc

| Phần | Công nghệ |
|---|---|
| Backend | Java 21, Spring Boot 4.1.1, Spring MVC |
| Giao diện | Thymeleaf, Bootstrap |
| Lưu trữ | Spring Data JPA/Hibernate, Microsoft SQL Server |
| Migration | Flyway |
| Xác thực | Spring Security, HTTP Session, BCrypt |
| Cấu trúc | Một Maven project, MVC/N-Layer đơn giản |

Agent và thành viên nhóm cần theo [hướng dẫn cấu trúc package Java](docs/architecture/java-package-structure.md): Controller phụ thuộc service interface; implementation nằm trong `service/impl/` và gọi repository thuộc domain sở hữu. Spring Data repository là interface; custom JDBC repository có thể là class. Mỗi JPA entity/embeddable có DTO converter hai chiều; service xác thực và gán entity quan hệ.

## Các module

Authentication; Service Management; Staff Profile; Follow Staff; House Management; Booking Management; Staff Availability; Leave Management; Job Execution; Payment; Feedback; System Configuration.

Có bốn role `CUSTOMER`, `STAFF`, `ADMIN`, `MANAGER`; Manager kế thừa quyền vận hành Admin và quyết định các bước được giao riêng. Schema hiện tại có 26 bảng nghiệp vụ theo Data Dictionary. Booking dùng các trạng thái `CONFIRMED`, `AWAITING_PAYMENT`, `COMPLETED`, `CANCELLED`. Staff được giao phải tự xác nhận đã đến trước khi bắt đầu; giờ Staff khai là mốc tính giảm trễ, trừ khi Admin xác minh/chỉnh theo báo cáo Customer trước khi hoàn tất/thanh toán. Customer report chỉ để theo dõi và không tự chặn/hủy/đổi tiền. Payment `CASH` do Staff được giao xác nhận; Payment `ONLINE` qua VNPay sandbox chỉ được xác nhận khi VNPay báo thành công (tra cứu QueryDR, IPN có chữ ký, hoặc — khi QueryDR không dùng được — dữ liệu trả về có chữ ký HMAC hợp lệ và đúng số tiền); giao dịch sandbox được đánh dấu mô phỏng và loại khỏi dòng tiền thật.

## Chạy database chính

Yêu cầu JDK 21 và SQL Server 2022+ bật TCP/IP, Mixed Mode, cổng 1433.

1. Start local SQL Server. The application connects to `Housekeeping_Database` by default and preserves its data. Set `DB_RESET_ON_STARTUP=true` only when you intentionally want to delete and recreate a supported local database, including all data and Flyway history; the SQL login then needs permission to alter, drop and create that database. Keep this value `false` for shared or existing databases.
2. Sao chép [`.env.example`](app/housekeeping-management-system/.env.example) thành `.env` trong thư mục Maven module, rồi đặt `DB_URL`, `DB_USERNAME` và `DB_PASSWORD` cho SQL Server chính. Nếu bật nhận bồi thường chuyển khoản, tạo và đặt khóa AES-256 riêng vào `PAYOUT_ENCRYPTION_KEY` theo hướng dẫn trong file mẫu; không commit `.env`. Địa chỉ nhà được nhập trực tiếp, không cần Google Maps API key.
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
    config/ controller/ dto/entity/ dto/request/ dto/response/
    entity/ repository/ service/ service/impl/ domain/
  src/main/resources/
    templates/ static/ db/primary/ db/refactor/ db/shared/
```

## Tài liệu liên quan

- [PRD](docs/requirements/PRD.md), [SRS](docs/requirements/SRS.md), [SQL Server Data Dictionary](docs/requirements/SQL_SERVER_DATA_DICTIONARY.md)
- [Kiến trúc package](docs/architecture/java-package-structure.md)
- [Hướng dẫn cài đặt/demo](docs/demo-setup.md), [hướng dẫn database](database/README.md), [hướng dẫn thanh toán VNPay](docs/vnpay-payment.md)
- [Quy trình đóng góp](CONTRIBUTING.md)
