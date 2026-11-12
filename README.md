> **Document:** Housekeeping Management System README\
> **File:** `README.md`\
> **Version:** v2.5.0\
> **Created:** 2026-06-14\
> **Last Updated:** 2026-11-12\
> **Status:** Draft\

# Housekeeping Management System

Dự án HSF302: Ứng dụng web quản lý và đặt dịch vụ dọn dẹp nhà ở. Java 21, Spring Boot 4.1.1, Spring MVC + Thymeleaf, Microsoft SQL Server, Spring Security, Bootstrap.

Tài liệu chuẩn gồm [PRD](docs/requirements/PRD.md), [SRS](docs/requirements/SRS.md), [Từ điển dữ liệu SQL Server](docs/requirements/SQL_SERVER_DATA_DICTIONARY.md), [Thuật ngữ nghiệp vụ (GLOSSARY)](GLOSSARY.md) và [Cẩm nang vấn đáp bảo vệ HSF302](docs/HSF302_DEFENSE_QA.md).

---

## Phân luồng phụ trách (HSF302)

| Vai trò / Thành viên | Phân luồng nghiệp vụ phụ trách | Màn hình & Controller chính |
|---|---|---|
| **Customer** | Đăng ký/đăng nhập, quản lý nhà/căn hộ, quy trình wizard đặt lịch 4 bước, chọn nhân viên yêu thích, tra cứu khoảng cách VietMap, theo dõi tiến độ công việc, nghiệm thu Before/After, thanh toán sau hoàn thành (Tiền mặt / VNPay), gửi đánh giá & báo cáo sự cố hư hại. | `BookingController`, `HouseController`, `PaymentController`, `AuthController` |
| **Staff** | Quản lý thông tin cá nhân, xem ca làm việc hàng ngày, xác nhận có mặt tại nhà khách (Arrival), bấm giờ thực hiện dịch vụ, tải ảnh nghiệm thu hoàn thành, xác nhận đã thu tiền mặt từ khách, tạo đơn xin nghỉ phép. | `StaffController`, `StaffLeaveController`, `StaffProfileController` |
| **Admin** | Quản lý danh mục gói dịch vụ, cấu hình giờ mở cửa công ty, xét duyệt đơn nghỉ phép của nhân viên, điều phối và giám sát toàn bộ lịch hẹn, giải quyết khiếu nại & hòa giải sự cố khách hàng báo. | `AdminBookingController`, `AdminFeedbackController`, `AdminLeaveController`, `AdminServiceController`, `AdminCompanySettingsController` |
| **Manager (SuperAdmin)** | Toàn quyền quản trị cấp cao nhất: tạo tài khoản nhân viên, tạo tài khoản Admin, phân quyền, quản lý trạng thái kích hoạt/khóa tài khoản Admin (Admin thường không được tự khóa Admin khác). | `AdminAccountController`, `AdminStaffController`, `PortalAdminController` |

---

## Chạy nhanh dự án (3 Bước)

Yêu cầu môi trường: **JDK 21** và **Microsoft SQL Server 2022+** (bật TCP/IP, cổng 1433, Mixed Mode Authentication).

### Bước 1: Khởi động Database
Tạo database tên `Housekeeping_Database` trên SQL Server local (bằng SSMS hoặc DBeaver). Bạn có thể chạy trước file tạo bảng [`database/schema.sql`](database/schema.sql).

### Bước 2: Cấu hình tài khoản Database
Sao chép file mẫu [`config/application-local.properties.example`](config/application-local.properties.example) hoặc [`.env.example`](app/housekeeping-management-system/.env.example) thành `.env` bên trong thư mục `app/housekeeping-management-system/` và điền mật khẩu sa máy bạn:
```properties
DB_URL=jdbc:sqlserver://localhost:1433;databaseName=Housekeeping_Database;encrypt=true;trustServerCertificate=true
DB_USERNAME=sa
DB_PASSWORD=YourPasswordHere
```

### Bước 3: Khởi chạy ứng dụng
Từ thư mục `app/housekeeping-management-system/`, chạy lệnh:
```powershell
mvn spring-boot:run
# hoặc dùng Maven Wrapper có sẵn:
.\mvnw.cmd spring-boot:run
```
Mở trình duyệt truy cập: **`http://localhost:8080`**

---

## Tài khoản kiểm thử chuẩn hệ thống

Hệ thống sử dụng mật khẩu chung chuẩn cho toàn bộ tài khoản thử nghiệm: **`Password123!`**

| Vai trò | Email đăng nhập | Mật khẩu chuẩn | Tên hiển thị | Chức năng kiểm thử chính |
|---|---|---|---|---|
| **Customer (Khách hàng)** | `customer@gonnha.vn` | `Password123!` | Cao Kỳ Nguyễn Hà | Đặt lịch dịch vụ, chọn nhân viên yêu thích, xem chi tiết lịch, thanh toán, gửi đánh giá |
| **Customer (Phụ)** | `nguyenhacaoky@gmail.com` | `Password123!` | Cao Kỳ Nguyễn Hà | Đặt lịch kiểm thử, kiểm tra thông tin địa chỉ nhà |
| **Manager (Quản lý cấp cao)** | `manager@gonnha.vn` | `Password123!` | Quản lý cấp cao | Quản trị tài khoản Admin, phê duyệt bồi thường sự cố cấp cao & quyết toán tài chính |
| **Admin (Quản trị viên)** | `admin@gonnha.vn` | `Password123!` | Quản trị viên hệ thống | Duyệt lịch hẹn, điều phối nhân viên, xử lý phản ánh & cấu hình công ty |
| **Staff (Yêu thích - Toàn thời gian)** | `mai.nguyen@gonnha.vn` | `Password123!` | Nguyễn Thị Mai | Nhân viên yêu thích của khách hàng, check-in ca làm, chụp ảnh nghiệm thu |
| **Staff (Bán thời gian)** | `nam.tran@gonnha.vn` | `Password123!` | Trần Văn Nam | Nhân viên bán thời gian, xem ca nhận việc, xác nhận thu tiền mặt |
| **Staff (Khác)** | `lan.hoang@gonnha.vn` | `Password123!` | Hoàng Thị Lan | Thử nghiệm đặt lịch với danh sách nhân viên mở rộng |

---

## Thiết lập Database & Tự động kết nối cho mọi máy

Mã nguồn đã được cấu hình mặc định sẵn tài khoản SQL Server kết nối tự động. Khi thành viên nhóm pull code về:
1. Mở SQL Server Management Studio (SSMS), mở file [`database/setup-sqlserver-login.sql`](database/setup-sqlserver-login.sql) và bấm **Execute (F5)** để tạo Database `Housekeeping_Database` và cấp quyền tự động cho Login `hsf302_housekeeping_local`.
2. Mở file [`database/sample-data.sql`](database/sample-data.sql) và bấm **Execute (F5)** để nạp toàn bộ danh mục 10 dịch vụ, 10 nhân viên kèm lịch làm việc và tài khoản thử nghiệm.
3. Chạy lệnh:
   ```powershell
   .\mvnw.cmd spring-boot:run
   ```
   Ứng dụng sẽ tự động kết nối thành công mà không cần tạo thêm file `.env` thủ công.

---

## Công nghệ và kiến trúc

| Phần | Công nghệ |
|---|---|
| Backend | Java 21, Spring Boot 4.1.1, Spring MVC |
| Giao diện | Thymeleaf, Bootstrap |
| Lưu trữ | Spring Data JPA/Hibernate, Microsoft SQL Server |
| Migration | Flyway |
| Xác thực | Spring Security, HTTP Session, BCrypt |
| Cấu trúc | Chuẩn Layered Architecture 3 lớp: Controller -> Service -> Repository -> Entity |

Agent và thành viên nhóm tuân theo [hướng dẫn cấu trúc package Java](docs/architecture/java-package-structure.md): Controller phụ thuộc service interface; implementation nằm trong `service/impl/` và gọi repository.

---

## Kiểm tra (Testing)

Từ thư mục chứa `pom.xml`:

```powershell
# Chạy Unit/MVC tests (không cần Docker hoặc SQL Server):
mvn "-DexcludedGroups=sqlserver" verify

# Bao gồm migration, mapping và concurrency tests trên SQL Server Testcontainers (cần Docker):
mvn verify
```

---

## Cấu trúc mã nguồn

```text
├── config/
│   └── application-local.properties.example # Cấu hình mẫu môi trường cá nhân
├── database/
│   ├── schema.sql                           # Script DDL toàn bộ bảng
│   └── README.md
├── docs/
│   ├── HSF302_DEFENSE_QA.md                 # Cẩm nang vấn đáp bảo vệ HSF302
│   ├── requirements/ (PRD, SRS, Data Dict)
│   └── vnpay-payment.md
├── GLOSSARY.md                              # Bảng thuật ngữ nghiệp vụ song ngữ
└── app/housekeeping-management-system/      # Maven module ứng dụng
    ├── pom.xml
    └── src/
        ├── main/java/com/housekeeping/management/
        │   ├── config/                      # Cấu hình Spring Security, Web, Async
        │   ├── controller/                  # Các Controller tách theo màn hình/role
        │   ├── dto/                         # Request Form và Response View
        │   ├── entity/                      # Các JPA Entity
        │   ├── enums/                       # Toàn bộ Enum trạng thái hệ thống
        │   ├── exception/                   # Ngoại lệ nghiệp vụ tùy chỉnh
        │   ├── repository/                  # Spring Data JPA Repositories
        │   └── service/                     # Service Interfaces & service/impl/
        └── main/resources/
            ├── templates/                   # Thymeleaf HTML Templates
            ├── static/                      # CSS, JS, Images
            └── db/                          # Flyway Migration Scripts
```
