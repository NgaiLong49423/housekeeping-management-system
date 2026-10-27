> **Document:** Changelog\
> **File:** `CHANGELOG.md`\
> **Version:** v1.0.1\
> **Created:** 2026-06-14\
> **Last Updated:** 2026-10-27\
> **Status:** Template\

# Changelog

Ghi các thay đổi đáng chú ý theo thứ tự mới nhất trước. File này mô tả thay đổi của dự án, không thay thế lịch sử Git hoặc hồ sơ phát hành.

## [Unreleased]

### Added
- PayOS payment-link checkout with persistent attempts, signed/idempotent webhook confirmation, Customer checkout status, and integration setup documentation; legacy MoMo payment rows remain readable but are no longer an active checkout method.
- `database/setup-demo.sql` tạo database/login demo riêng, chạy lại không xóa dữ liệu; profile `demo` có sẵn kết nối tương ứng để người clone chỉ cần chạy SQL rồi Run `Housekeeping Demo`, không bắt buộc tạo `.env`.
- Profile `demo` nạp dữ liệu mẫu vào database SQL Server riêng bằng Flyway migration, không dùng lớp Java seeder; hướng dẫn tài khoản demo và `.env.example` được đồng bộ.
- Tự mở trang chủ trong trình duyệt mặc định khi Run `Housekeeping Full Stack` trong IntelliJ; đã kiểm tra Chrome mở frontend sau lượt dừng/chạy lại server.
- File SQL tạo database `hsf302` và đủ 11 bảng từ schema V1, kèm kiểm tra database rỗng, transaction và hướng dẫn Flyway tiếp nhận schema đã tạo thủ công.
- Cấu hình `Housekeeping Full Stack` và hướng dẫn mở đúng Maven project, chọn JDK 21, chạy giao diện/backend trong IntelliJ.
- Launcher chạy/dừng local bằng JDK 21, cấu hình ngoài JAR, migration dữ liệu demo và script kiểm tra luồng HTTP–SQL Server; hướng dẫn chạy trên máy đã setup.
- Giao diện Gọn Nhà bằng Thymeleaf/Bootstrap, chuyển từ thiết kế ZIP và điều chỉnh theo PRD/SRS cho Customer, Staff, Admin.
- Lớp MVC và đăng nhập session theo xác nhận của người dùng; phân quyền, CSRF, ownership và form gọi Service đặt lịch, Evidence, hoàn thành, thanh toán hiện có.
- Thao tác portal cho hủy lịch, Feedback, Favorite và quản lý danh mục/khoảng sẵn sàng; không thay schema hoặc Service nghiệp vụ cũ.
- Kiểm thử render/binding/session/quyền truy cập và hướng dẫn kết nối frontend–backend; ảnh và Bootstrap được phục vụ cục bộ.
- Bổ sung policy, skills, hướng dẫn khởi tạo và workflow review dùng chung cho Agent.
- Bổ sung kiểm tra repository trung lập với framework bằng GitHub Actions.

### Changed
- Share the post-baseline address migration between primary and demo Flyway locations to avoid maintaining duplicate DDL.
- Recreate the approved local SQL Server database on every application launch by default, with an explicit data-loss warning, local database restrictions and a `DB_RESET_ON_STARTUP=false` opt-out.
- Chuyển tài khoản và dữ liệu mẫu khỏi Java seeder sang Flyway migration chỉ áp dụng cho database `home_cleaning_demo`.
- Chuẩn hóa thư mục ứng dụng thành `app/` và cập nhật các mẫu dự án.
- Tổng quát hóa tài liệu và hướng dẫn repository để tái sử dụng cho các dự án Java web.
- Staff có thể hoàn thành Booking `CONFIRMED` bất cứ lúc nào khi đủ Evidence, kể cả trước giờ bắt đầu dự kiến; booking mẫu đang xác nhận chưa tạo Payment cho tới khi hoàn thành.

### Fixed
- Separate SQL Server batches in the manual customer address migration so newly added columns are available during backfill; register the database initializer before datasource startup and cover reset safeguards with unit tests.
- Đồng bộ các bản Run Configuration dùng SDK `ms-21` và thư mục làm việc theo module Maven, sửa lỗi không tìm thấy `file:./.runtime/` khi chạy từ thư mục project khác.
- Sửa lỗi IntelliJ không tìm thấy main class: import đúng module Maven, chọn classpath `housekeeping-management-system`, đặt SDK 21 và bật annotation processing Lombok. Đã chạy trực tiếp trong IDE và kiểm tra đăng nhập ba vai trò cùng trang dịch vụ qua SQL Server.
- Sửa cấu hình local trỏ tới database `hsf302` hiện có, map SQL login riêng vào database, baseline schema thủ công V1 và xác minh đăng nhập đủ ba vai trò qua SQL Server thật.
- Cấu hình Hibernate giữ tên bảng tường minh để validate đúng schema SQL Server được Flyway tạo.
- Sửa các đường dẫn repository cũ và liên kết chỉ dùng được trên máy local.
