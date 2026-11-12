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

| Vai trò | Email | Mật khẩu mặc định |
|---|---|---|
| Customer | `customer@gonnha.vn` (hoặc `customer@demo.gonnha.local`) | Mật khẩu đã tạo khi đăng ký / `Demo@12345` |
| Staff | `mai.nguyen@gonnha.vn` (hoặc `staff@demo.gonnha.local`) | `Demo@12345` |
| Staff | `nam.tran@gonnha.vn` (hoặc `staff.fulltime@demo.gonnha.local`) | `Demo@12345` |
| Admin | `admin@gonnha.vn` (hoặc `admin@demo.gonnha.local`) | `Demo@12345` |
| Manager | `manager@demo.gonnha.local` | `Demo@12345` |

## Giới hạn xác minh

SQL Server local và Docker/Testcontainers cần khả dụng để xác minh DDL, Flyway, Hibernate mapping và transaction. Nếu các môi trường đó chưa chạy, chỉ báo kết quả compile/unit test đã thực sự có; không khẳng định baseline mới đã chạy trên SQL Server.
