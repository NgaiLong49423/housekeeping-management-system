> **Document:** Hướng dẫn môi trường chạy ứng dụng\
> **File:** `docs/demo-setup.md`\
> **Version:** v3.6.0\
> **Created:** 2026-10-17\
> **Last Updated:** 2026-11-12\
> **Status:** Active\

# Môi trường chạy ứng dụng

Hệ thống hỗ trợ 2 môi trường chạy trên Microsoft SQL Server local:
1. **Môi trường chính thức (`Housekeeping_Database`):** Dùng cho kiểm thử toàn diện tính năng, thanh toán VNPay và bảo vệ đồ án HSF302.
2. **Profile Demo (`home_cleaning_demo`):** Sử dụng các file Flyway seed demo tự động (`--spring.profiles.active=demo`).

## 1. Môi trường chính thức (`Housekeeping_Database`)

### Chuẩn bị SQL Server
1. Cài JDK 21, Maven và SQL Server local có TCP/IP tại cổng 1433.
2. Đảm bảo database `Housekeeping_Database` đã được tạo trên SQL Server.
3. Cấu hình file `.env` với thông tin kết nối SQL Server của bạn (`DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `PAYOUT_ENCRYPTION_KEY`).
4. Chạy ứng dụng bằng lệnh Maven:

```powershell
cd app/housekeeping-management-system
mvn spring-boot:run
```

5. Mở trình duyệt tại `http://localhost:8080`.

### Danh sách tài khoản hệ thống chuẩn
Tất cả tài khoản hệ thống chuẩn trên `Housekeeping_Database` sử dụng chung mật khẩu mặc định: **`Admin@12345`**

| Vai trò | Email đăng nhập | Mật khẩu chuẩn | Tên hiển thị | Ghi chú vai trò |
|---|---|---|---|---|
| **Manager (Quản lý cấp cao)** | `manager@gonnha.vn` | `Admin@12345` | Quản lý cấp cao | Quản lý tài khoản Admin, duyệt bồi thường cấp cao |
| **Admin (Quản trị viên)** | `admin@gonnha.vn` | `Admin@12345` | Quản trị viên hệ thống | Quản trị vận hành đặt lịch, nhân viên, dịch vụ |
| **Customer (Khách hàng)** | `customer@gonnha.vn` | `Admin@12345` | Cao Kỳ Nguyễn Hà | Đặt lịch, thanh toán VNPay, quản lý nhà |
| **Customer (Khách hàng phụ)** | `nguyenhacaoky@gmail.com` | `Admin@12345` | Cao Kỳ Nguyễn Hà | Kiểm thử tài khoản cá nhân |
| **Staff (Toàn thời gian)** | `mai.nguyen@gonnha.vn` | `Admin@12345` | Nguyễn Thị Mai | Nhân viên yêu thích, check-in, nhận ca |
| **Staff (Bán thời gian)** | `nam.tran@gonnha.vn` | `Admin@12345` | Trần Văn Nam | Nhân viên bán thời gian, đăng ký lịch sẵn sàng |
| **Staff (Toàn thời gian)** | `cuong.le@gonnha.vn` | `Admin@12345` | Lê Hoàng Cường | Nhân viên kỹ thuật chuyên sâu |
| **Staff (Bán thời gian)** | `dung.pham@gonnha.vn` | `Admin@12345` | Phạm Thị Dung | Nhân viên dọn dẹp linh hoạt |

---

## 2. Môi trường Demo (`home_cleaning_demo`)

Khi khởi động với profile `demo`:
```powershell
cd app/housekeeping-management-system
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=demo"
```

Flyway migration `V3__seed_demo_data.sql`, `V32__rename_demo_accounts_to_realistic_names.sql`, `V33__seed_additional_demo_staff_and_customers.sql` áp dụng trực tiếp vào database `home_cleaning_demo`.

| Vai trò | Email | Họ tên | Số điện thoại |
|---|---|---|---|
| Customer | `customer@demo.gonnha.local` | Trần Thị Thu Hà | 0900000001 |
| Customer | `customer2@demo.gonnha.local` | Đỗ Ngọc Hân | 0938502002 |
| Customer | `customer3@demo.gonnha.local` | Bùi Quang Huy | 0938502003 |
| Customer | `customer4@demo.gonnha.local` | Ngô Thị Kim Oanh | 0938502004 |
| Customer | `customer5@demo.gonnha.local` | Vũ Đức Thắng | 0938502005 |
| Customer | `customer6@demo.gonnha.local` | Lý Gia Hân | 0938502006 |
| Staff part-time | `staff@demo.gonnha.local` | Lê Thị Hồng Nhung | 0900000002 |
| Staff full-time | `staff.fulltime@demo.gonnha.local` | Phạm Văn Hùng | 0900000003 |
| Staff full-time | `staff3@demo.gonnha.local` | Nguyễn Thị Mai Lan | 0912401003 |
| Staff part-time | `staff4@demo.gonnha.local` | Trần Văn Khoa | 0912401004 |
| Staff full-time | `staff5@demo.gonnha.local` | Lê Thị Bích Ngọc | 0912401005 |
| Staff part-time | `staff6@demo.gonnha.local` | Hoàng Minh Tuấn | 0912401006 |
| Staff full-time | `staff7@demo.gonnha.local` | Phan Thị Thanh Thảo | 0912401007 |
| Admin | `admin@demo.gonnha.local` | Võ Thanh Tùng | 0900000004 |
| Manager hệ thống | `manager@demo.gonnha.local` | Đặng Quốc Bảo | 0900000005 |

Tất cả tài khoản demo trên dùng mật khẩu ban đầu `Demo@12345`.

## Giới hạn xác minh

SQL Server local và Docker/Testcontainers cần khả dụng để xác minh DDL, Flyway, Hibernate mapping và transaction. Nếu các môi trường đó chưa chạy, chỉ báo kết quả compile/unit test đã thực sự có; không khẳng định baseline mới đã chạy trên SQL Server.
