> **Document:** Hướng dẫn môi trường demo\
> **File:** `docs/demo-setup.md`\
> **Version:** v2.3.0\
> **Created:** 2026-10-17\
> **Last Updated:** 2026-11-12\
> **Status:** Draft\

# Chạy môi trường demo

Tài liệu này mô tả cách chạy ứng dụng trên SQL Server local. Profile `demo` dùng database riêng `home_cleaning_demo`; Flyway áp dụng baseline trong `classpath:db/refactor` và các nâng cấp dùng chung trong `classpath:db/shared`. Đây là baseline mới của mô hình Home Cleaning, không phải migration dữ liệu từ database cũ.

## Chuẩn bị SQL Server

1. Cài JDK 21, Maven và SQL Server local có TCP/IP tại cổng 1433.
2. Mở `database/setup-demo.sql` trong SSMS và chạy trên instance demo/dev. Script chỉ tạo database `home_cleaning_demo` nếu chưa tồn tại; không tạo login hay chứa password.
3. Tạo SQL login riêng, map vào database demo và cấp quyền DDL để Flyway tạo schema. Sao chép `.env.example` thành `.env` trong thư mục Maven module rồi tự đặt `DB_USERNAME` và `DB_PASSWORD`. Không commit `.env`.
4. Địa chỉ nhà được nhập trực tiếp qua biểu mẫu người nhận và địa chỉ; demo không cần Google Maps API key.
5. Mở `app/housekeeping-management-system/pom.xml` bằng IntelliJ, chọn JDK 21 và đợi Maven import.
6. Chạy cấu hình IntelliJ `Housekeeping Demo` hoặc chạy:

```powershell
cd app/housekeeping-management-system
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=demo"
```

Mở `http://localhost:8080`. Profile demo cố định URL tới `home_cleaning_demo`, không dùng `DB_URL` để trỏ sang database khác; username/password lấy từ `.env`. Database được giữ nguyên khi khởi động theo mặc định. Chỉ đặt `DB_RESET_ON_STARTUP=true` nếu chủ động muốn xóa và tạo lại database demo local.

## Kết nối mẫu

Thông tin database dành cho môi trường demo local:

| Thuộc tính | Giá trị |
|---|---|
| Database | `home_cleaning_demo` |
| Website | `http://localhost:8080` |

Flyway migration `V3__seed_demo_data.sql` ghi dữ liệu mẫu trực tiếp vào database `home_cleaning_demo`; ứng dụng không còn lớp Java tự chèn dữ liệu mẫu khi khởi động. Migration thêm bốn tài khoản website, hai hồ sơ nhân viên, cấu hình công ty, giờ hoạt động, ba dịch vụ, một địa chỉ nhà và lịch rảnh cho nhân viên part-time. V11 thêm tài khoản Manager riêng cho database demo; V15 chuyển tài khoản đó sang role `MANAGER`, có quyền Admin toàn hệ thống và quản lý tài khoản Admin. Tài khoản demo mới có mật khẩu ban đầu `Demo@12345`; chỉ dùng trên database demo local. Dữ liệu và mật khẩu của tài khoản đã có không bị ghi đè. Migration không thay thế quy trình xác minh trên SQL Server.

V32 đổi họ tên năm tài khoản demo ban đầu từ tên tạm "… Demo" sang họ tên tiếng Việt (chỉ khi tên tạm còn nguyên, không ghi đè tên đã sửa qua giao diện). Danh sách và chi tiết Booking đọc tên Staff/Customer từ `Users`, nên Booking cũ cũng hiển thị tên mới. V33 thêm năm Staff (kèm hồ sơ giới thiệu ngắn) và năm Customer (mỗi người một địa chỉ nhà mặc định có tọa độ, cách vị trí công ty khoảng 1–2 km). Cả hai migration chỉ chạy trong `home_cleaning_demo` và có thể chạy lại mà không tạo trùng.

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

Tất cả tài khoản trên dùng mật khẩu ban đầu `Demo@12345`. Staff part-time mới chưa có lịch rảnh; tạo lịch qua giao diện Staff trước khi đặt Booking cho họ.

Lịch part-time được tạo cho ngày mai theo múi giờ Việt Nam. Đặt lịch cần trước ít nhất 120 phút và trong vòng 30 ngày; nếu lịch được tạo sau giờ hoạt động ngày mai, hãy chọn ngày kế tiếp còn nằm trong cửa sổ cho phép hoặc cập nhật availability qua giao diện Staff.

## Cấu hình database development

Trong `app/housekeeping-management-system/.env`, cấu hình `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` để trỏ tới một database SQL Server development mới, riêng biệt. Không dùng database production hoặc database cũ đang chứa dữ liệu cần giữ. Không commit `.env`.

`application-demo.properties` cố định datasource demo, không đọc kết nối riêng từ `.env`. Schema cũ và các migration `db/migration/V1` đến `V3` được giữ nguyên; không chạy `flyway clean`, không xóa `flyway_schema_history`, không sửa database cũ để làm nó khớp baseline mới.

## Giới hạn xác minh

SQL Server local và Docker/Testcontainers cần khả dụng để xác minh DDL, Flyway, Hibernate mapping và transaction. Nếu các môi trường đó chưa chạy, chỉ báo kết quả compile/unit test đã thực sự có; không khẳng định baseline mới đã chạy trên SQL Server.
