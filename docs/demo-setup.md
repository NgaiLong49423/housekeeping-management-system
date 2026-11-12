> **Document:** Hướng dẫn môi trường chạy ứng dụng\
> **File:** `docs/demo-setup.md`\
> **Version:** v2.3.0\
> **Created:** 2026-10-17\
> **Last Updated:** 2026-11-12\
> **Status:** Active\

# Môi trường chạy ứng dụng

Hệ thống đã thống nhất sử dụng database chính thức là `Housekeeping_Database`. Flyway áp dụng baseline trong `classpath:db/primary` và các migration dùng chung trong `classpath:db/shared`.

## Chuẩn bị SQL Server

1. Cài JDK 21, Maven và SQL Server local có TCP/IP tại cổng 1433.
2. Đảm bảo database `Housekeeping_Database` đã được tạo trên SQL Server.
3. Cấu hình file `.env` với thông tin kết nối SQL Server của bạn (`DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `PAYOUT_ENCRYPTION_KEY`).
4. Chạy ứng dụng bằng lệnh Maven:

```powershell
cd app/housekeeping-management-system
mvn spring-boot:run
```

5. Mở trình duyệt tại `http://localhost:8080`.

## Tài khoản hệ thống

Tất cả tài khoản hệ thống chuẩn sử dụng chung mật khẩu mặc định: **`Admin@12345`**

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

## Giới hạn xác minh

SQL Server local và Docker/Testcontainers cần khả dụng để xác minh DDL, Flyway, Hibernate mapping và transaction. Nếu các môi trường đó chưa chạy, chỉ báo kết quả compile/unit test đã thực sự có; không khẳng định baseline mới đã chạy trên SQL Server.
