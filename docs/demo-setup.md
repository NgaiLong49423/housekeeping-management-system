> **Document:** Hướng dẫn môi trường demo\
> **File:** `docs/demo-setup.md`\
> **Version:** v2.0.0\
> **Created:** 2026-10-17\
> **Last Updated:** 2026-10-24\
> **Status:** Draft\

# Chạy môi trường demo

Tài liệu này mô tả cách chạy ứng dụng trên SQL Server local. Profile `demo` dùng database riêng `home_cleaning_demo`; ứng dụng chỉ chạy Flyway migration trong `classpath:db/refactor`. Đây là baseline mới của mô hình Home Cleaning, không phải migration dữ liệu từ database cũ.

## Chuẩn bị SQL Server

1. Cài JDK 21, Maven và SQL Server local có TCP/IP tại cổng 1433.
2. Mở `database/setup-demo.sql` trong SSMS và chạy trên instance demo/dev. Script chỉ tạo database `home_cleaning_demo` nếu chưa tồn tại; không tạo login hay chứa password.
3. Tạo SQL login riêng, map vào database demo và cấp quyền DDL để Flyway tạo schema. Sao chép `.env.example` thành `.env` trong thư mục Maven module rồi điền `DB_USERNAME` và `DB_PASSWORD` của bạn. Không commit `.env`.
4. Mở `app/housekeeping-management-system/pom.xml` bằng IntelliJ, chọn JDK 21 và đợi Maven import.
5. Chạy cấu hình IntelliJ `Housekeeping Demo` hoặc chạy:

```powershell
cd app/housekeeping-management-system
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=demo"
```

Mở `http://localhost:8080`. Profile demo cố định URL tới `home_cleaning_demo`, không dùng `DB_URL` để trỏ sang database khác; username/password lấy từ `.env`. Ứng dụng không tự xóa database.

## Kết nối mẫu

Thông tin database dành cho môi trường demo local:

| Thuộc tính | Giá trị |
|---|---|
| Database | `home_cleaning_demo` |
| Website | `http://localhost:8080` |

Tài khoản đăng nhập website và dữ liệu seed hiện cần được đồng bộ với schema/seed mới trước khi xác nhận có thể đăng nhập. Không lấy kết quả xác minh của mô hình cũ làm bằng chứng cho baseline mới.

## Cấu hình database development

Trong `app/housekeeping-management-system/.env`, cấu hình `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` để trỏ tới một database SQL Server development mới, riêng biệt. Không dùng database production hoặc database cũ đang chứa dữ liệu cần giữ. Không commit `.env`.

`application-demo.properties` cố định datasource demo, không đọc kết nối riêng từ `.env`. Schema cũ và các migration `db/migration/V1` đến `V3` được giữ nguyên; không chạy `flyway clean`, không xóa `flyway_schema_history`, không sửa database cũ để làm nó khớp baseline mới.

## Giới hạn xác minh

SQL Server local và Docker/Testcontainers cần khả dụng để xác minh DDL, Flyway, Hibernate mapping và transaction. Nếu các môi trường đó chưa chạy, chỉ báo kết quả compile/unit test đã thực sự có; không khẳng định baseline mới đã chạy trên SQL Server.
