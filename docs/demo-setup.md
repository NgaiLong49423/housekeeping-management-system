> **Document:** Hướng dẫn môi trường chạy ứng dụng\
> **File:** `docs/demo-setup.md`\
> **Version:** v2.2.0\
> **Created:** 2026-10-17\
> **Last Updated:** 2026-11-11\
> **Status:** Active\

# Môi trường chạy ứng dụng

Hệ thống đã thống nhất sử dụng duy nhất một database chính thức là `Housekeeping_Database` (không sử dụng database demo riêng `home_cleaning_demo`).

## Chuẩn bị SQL Server

1. Cài JDK 21, Maven và SQL Server local có TCP/IP tại cổng 1433.
2. Đảm bảo database `Housekeeping_Database` đã được tạo trên SQL Server.
3. Cấu hình file `.env` hoặc file `.runtime/application-local.properties` với thông tin kết nối SQL Server của bạn.
4. Chạy ứng dụng qua cấu hình `Housekeeping Full Stack` trong IntelliJ hoặc bằng lệnh Maven:

```powershell
cd app/housekeeping-management-system
mvn spring-boot:run
```

5. Mở trình duyệt tại `http://localhost:8080`.

## Tài khoản hệ thống

| Vai trò | Email |
|---|---|
| Customer | `customer@gonnha.vn` |
| Staff | `mai.nguyen@gonnha.vn` |
| Staff | `nam.tran@gonnha.vn` |
| Admin | `admin@gonnha.vn` |
