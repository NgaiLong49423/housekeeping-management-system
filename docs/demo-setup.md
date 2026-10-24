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
3. Tạo SQL login riêng, map vào database demo và cấp quyền DDL để Flyway tạo schema. Sao chép `.env.example` thành `.env` trong thư mục Maven module rồi tự đặt `DB_USERNAME`, `DB_PASSWORD` và `DEMO_ACCOUNT_PASSWORD`. Không commit `.env`.
4. Nếu muốn chọn địa chỉ trên Google Maps, cấu hình `GOOGLE_MAPS_BROWSER_KEY` trong `.env` bằng browser key đã giới hạn HTTP referrer và bật Maps JavaScript API/Places API (New) theo dự án Google Cloud của nhóm. Không dùng server key trong trình duyệt. Khi chưa có key, form cho phép nhập tọa độ thủ công. Xem [hướng dẫn tạo Maps JavaScript API key](https://developers.google.com/maps/documentation/javascript/get-api-key) và [Place Autocomplete widget](https://developers.google.com/maps/documentation/javascript/place-autocomplete-new).
5. Mở `app/housekeeping-management-system/pom.xml` bằng IntelliJ, chọn JDK 21 và đợi Maven import.
6. Chạy cấu hình IntelliJ `Housekeeping Demo` hoặc chạy:

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

Seeder tạo bốn tài khoản website trong bảng `Users`; hai tài khoản nhân viên có bản ghi Staff với loại part-time và full-time tương ứng. Mật khẩu được cấu hình qua `DEMO_ACCOUNT_PASSWORD`; ứng dụng không dùng mật khẩu mẫu mặc định. Khi thiếu dữ liệu nền, seeder bổ sung một cấu hình công ty mô phỏng, giờ hoạt động 08:00–18:00, ba dịch vụ, một nhà mẫu của customer và một lịch rảnh part-time vào ngày kế tiếp. Các bản ghi cấu hình/dịch vụ/nhà/lịch đã tồn tại được giữ nguyên; chỉ bổ sung những mục còn thiếu. Seeder không thay thế quy trình xác minh trên SQL Server.

| Vai trò | Email |
|---|---|
| Customer | `customer@demo.gonnha.local` |
| Staff part-time | `staff@demo.gonnha.local` |
| Staff full-time | `staff.fulltime@demo.gonnha.local` |
| Admin | `admin@demo.gonnha.local` |

Lịch part-time được tạo cho ngày mai theo múi giờ Việt Nam. Đặt lịch cần trước ít nhất 120 phút và trong vòng 30 ngày; nếu lịch được tạo sau giờ hoạt động ngày mai, hãy chọn ngày kế tiếp còn nằm trong cửa sổ cho phép hoặc cập nhật availability qua giao diện Staff.

## Cấu hình database development

Trong `app/housekeeping-management-system/.env`, cấu hình `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` để trỏ tới một database SQL Server development mới, riêng biệt. Không dùng database production hoặc database cũ đang chứa dữ liệu cần giữ. Không commit `.env`.

`application-demo.properties` cố định datasource demo, không đọc kết nối riêng từ `.env`. Schema cũ và các migration `db/migration/V1` đến `V3` được giữ nguyên; không chạy `flyway clean`, không xóa `flyway_schema_history`, không sửa database cũ để làm nó khớp baseline mới.

## Giới hạn xác minh

SQL Server local và Docker/Testcontainers cần khả dụng để xác minh DDL, Flyway, Hibernate mapping và transaction. Nếu các môi trường đó chưa chạy, chỉ báo kết quả compile/unit test đã thực sự có; không khẳng định baseline mới đã chạy trên SQL Server.
