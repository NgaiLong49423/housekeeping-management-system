> **Document:** Changelog\
> **File:** `CHANGELOG.md`\
> **Version:** v1.2.0\
> **Created:** 2026-06-14\
> **Last Updated:** 2026-11-10\
> **Status:** Template\

# Changelog

Ghi các thay đổi đáng chú ý theo thứ tự mới nhất trước. File này mô tả thay đổi của dự án, không thay thế lịch sử Git hoặc hồ sơ phát hành.

## [Unreleased]

### Added
- Tái cấu trúc và nâng cấp toàn diện Tính năng Quản lý Hủy đơn & Phí phạt (Order Cancellation & Penalty Policy) theo mô hình bTaskee:
  - **Thay đổi Schema CSDL (Flyway V32):** Mở rộng bảng `Bookings` (`cancellation_fee`, `refund_amount`, `reschedule_count`), mở rộng bảng `Users` (`unpaid_penalty_debt`, `allow_cod`, `suspended_until`, enum `SUSPENDED`), tạo mới 3 bảng tài chính `UserWallets`, `WalletTransactions` và nhật ký `CancellationLogs`.
  - **Ràng buộc Chống quỵt nợ phạt (Anti-Default / Debt Enforcement):** Khách hủy đơn tiền mặt (COD) bị ghi nhận nợ phạt vào `unpaid_penalty_debt` và bị cưỡng chế khóa quyền đặt đơn mới cho tới khi hoàn tất thanh toán.
  - **Ràng buộc Chống "bom" đơn 30 ngày (Anti-Fraud Abuse Control):** Tự động phát cảnh báo (lần 1), tước quyền thanh toán tiền mặt `allow_cod = false` (lần 2), và tạm đình chỉ tài khoản 7 ngày `status = SUSPENDED` (lần 3 trở lên).
  - **Ràng buộc Bảo vệ CTV (Worker Compensation):** Tự động trích 80% phí phạt chuyển thẳng vào ví `UserWallets` của CTV để bù đắp chi phí di chuyển và thời gian chờ ca; 20% thuộc về sàn.
  - **Ràng buộc Chống Race Condition (Concurrency Protection):** Khóa bi quan `PESSIMISTIC_WRITE` (`SELECT ... WITH (UPDLOCK, ROWLOCK)`) trên đơn hàng, chặn tuyệt đối hủy đơn khi CTV đã bấm bắt đầu làm (`workStartedAt != null`) hoặc đơn đã hoàn thành (`COMPLETED`).
  - **Ràng buộc Miễn trừ hợp lý:** Ân hạn 10 phút đầu sau đặt, miễn phí nếu đơn chưa có CTV nhận, miễn phí 100% nếu CTV đến trễ > 15 phút chưa check-in với lý do `WORKER_LATE`.
  - **REST API Endpoints:** `GET /api/bookings/{id}/cancellation-preview` (xem trước mức phạt, tiền hoàn, quyền dời lịch) và `POST /api/bookings/{id}/cancel` (hủy đơn có mã lý do).
  - **Unit Test Suite:** Bổ sung `BookingCancellationPolicyServiceTest` với 8 kịch bản kiểm thử, nâng tổng số test cases đạt chuẩn lên 306 bài kiểm thử thành công (`BUILD SUCCESS`).
- Tinh chỉnh giao diện đặt lịch và danh mục dịch vụ:
  - Trang chủ (`/`): Giới hạn hiển thị 4 dịch vụ tiêu biểu kèm nút "Xem tất cả dịch vụ" chuyển hướng sang `/services`.
  - Trang đặt lịch Bước 01 (`/customer/bookings/new`): Thu gọn danh sách dịch vụ với 4 mục ban đầu kèm nút bo tròn "Xem thêm dịch vụ khác / Thu gọn" linh hoạt.
  - Trang đặt lịch Bước 03 & 04: Bộ lọc nghiêm ngặt nhân viên yêu thích (`favorite == true`), tự động chốt ca và ẩn hoàn toàn "Bước 2: Chọn nhân viên" ở Bước 04 khi khách hàng đã chọn người yêu thích.
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
- Chuẩn hóa hướng dẫn Java cho Agent và người đóng góp: service interface/implementation, repository theo domain, DTO hai chiều cho toàn bộ entity, xử lý quan hệ qua service và contract thanh toán trực tuyến dùng tên chung.
- VNPay sandbox verified end to end with the NCB test card: payments are confirmed from the signed return data when QueryDR is unavailable (the new terminal answers QueryDR with 99), a still-open earlier checkout is no longer reopened because VNPay rejects it, and online order codes combine the creation time with the sequence so a database reset or another team machine never resends a `vnp_TxnRef` VNPay already saw that day.
- Online payment now uses the VNPay sandbox instead of PayOS: signed checkout URLs, confirmation only from VNPay itself (QueryDR lookup on return or signed IPN) so it works on every machine without a public URL, a repeat checkout asks VNPay about the previous attempt to avoid double charges, and a clear message when VNPay is not configured. The PayOS integration and its Postman collection are removed; database and booking statuses are unchanged.
- Share the post-baseline address migration between primary and demo Flyway locations to avoid maintaining duplicate DDL.
- Exclude only the two guarded primary/demo baseline scripts from Sonar duplication measurement while retaining the rest of their analysis.
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
