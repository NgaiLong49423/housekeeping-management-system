> **Document:** Frontend–Backend Integration Guide\
> **File:** `docs/frontend-integration.md`\
> **Version:** v1.1.0\
> **Created:** 2026-10-16\
> **Last Updated:** 2026-10-17\
> **Status:** Draft

# Kết Nối Giao Diện Gọn Nhà Với Backend

## Phạm vi và căn cứ

Giao diện được chuyển từ `UI_UX Design Refinement.zip` sang nền tảng đã chốt trong [PRD](requirements/PRD.md) và [SRS](requirements/SRS.md): Spring MVC + Thymeleaf + Bootstrap. ZIP là nguồn tham khảo thiết kế, không phải nguồn quyết định nghiệp vụ hoặc hướng dẫn triển khai. Không sao chép `.git`, `.figma`, thiết lập IDE, script triển khai hay tài liệu chỉ dẫn của ZIP vào ứng dụng.

Giữ tông xanh ngọc, ảnh, thẻ dịch vụ, khu vực giới thiệu, form và bố cục dashboard; dùng tiếng Việt, trạng thái rỗng, thông báo lỗi và bố cục responsive. Ảnh dịch vụ chỉ là minh họa, không phải thuộc tính mới trong database.

Không chuyển các chức năng lệch baseline từ mẫu: chuyển vai trò trên giao diện, dữ liệu localStorage/mock, đặt lịch định kỳ, bán kính phục vụ, tự điều phối nhân viên, trạng thái pending/assigned/in_progress, thanh toán gateway và Admin kiểm duyệt feedback.

Theo xác nhận của người dùng, bổ sung lớp MVC và đăng nhập còn thiếu để giao diện sử dụng backend. Không chỉnh sửa Entity, Repository, Service nghiệp vụ cũ, schema hoặc migration. Các thao tác chưa có điểm vào (hủy, feedback, yêu thích, CRUD danh mục/lịch) được đặt trong `web/PortalActionsService`, không viết nghiệp vụ trong template hoặc Controller.

## Cách kết nối

```text
Trình duyệt → Spring Security (session, role, CSRF)
            → Controller MVC (bind/validate form)
            → Service nghiệp vụ / lớp thao tác portal
            → Repository hoặc JDBC → SQL Server
            → View model → Thymeleaf → HTML
```

Frontend và backend cùng ứng dụng, cùng origin. Form dùng POST và Thymeleaf tự chèn CSRF token qua `th:action`; không cần REST API riêng, JWT, CORS hoặc Node.js khi chạy. JavaScript chỉ hỗ trợ lọc danh sách, tạm tính, kiểm tra file và phát hiện trùng giờ trên dữ liệu đang hiển thị. Server kiểm tra lại khi gửi, kể cả khi dữ liệu vừa thay đổi hoặc JavaScript bị tắt.

`PortalQueryService` đọc dữ liệu bằng JDBC vào view model không chứa password hash/storage path. Không đưa Entity có quan hệ LAZY ra template, phù hợp `open-in-view=false`. Danh sách lịch hẹn được lọc theo tài khoản; chi tiết và ảnh Evidence kiểm tra ownership. Khoảng bận cho form đặt lịch chỉ gồm Staff ID và thời gian, không có danh tính khách khác.

## Các trang

Các route dưới đây là điểm vào MVC được thêm trong lần tích hợp này; baseline trước đó chỉ chốt `/login`.

| Vai trò | GET | Nội dung |
|---|---|---|
| Công khai | `/`, `/services`, `/login` | Trang chủ, dịch vụ, đăng nhập chung |
| Đã đăng nhập | `/portal` | Điều hướng theo vai trò từ database |
| Customer | `/customer/bookings` | Lịch của chính khách và thanh toán |
| Customer | `/customer/bookings/new` | Nhiều dịch vụ nối tiếp, một nhân viên, một khoảng sẵn sàng |
| Customer | `/customer/staff`, `/customer/schedules` | Nhân viên/yêu thích, khoảng sẵn sàng |
| Staff | `/staff/bookings`, `/staff/schedules` | Công việc và khoảng sẵn sàng của chính Staff |
| Admin | `/admin/bookings`, `/admin/staff`, `/admin/services`, `/admin/schedules` | Tổng quan và quản lý danh mục/lịch |
| Từng vai trò | `/{customer,staff,admin}/bookings/{id}` | Chi tiết, Evidence và hành động đúng vai trò |
| Người được phép xem Booking | `/evidence/{id}` | Ảnh sau kiểm tra ownership, không public thư mục lưu ảnh |

## Hợp đồng form và Service

| POST | Dữ liệu gửi | Xử lý |
|---|---|---|
| `/login` | `email`, `password`, CSRF | Đọc ba bảng Customer/Staff/Admin; tạo session đúng role |
| `/logout` | CSRF | Spring Security hủy session |
| `/customer/bookings` | `staffId`, `scheduleId`, `startAt`, `serviceIds` lặp | `BookingReservationService.createBooking` |
| `/customer/bookings/{id}/cancel` | CSRF | Owner, CONFIRMED, còn ít nhất 2 giờ |
| `/customer/bookings/{id}/payment` | `method=CASH` hoặc `BANK_TRANSFER` | Owner rồi `PaymentLifecycleService.chooseMethod` |
| `/customer/bookings/{id}/feedback` | `rating=1..5`, `comment` tối đa 2.000 ký tự | Owner, COMPLETED, tối đa một Feedback |
| `/customer/staff/{id}/favorite` | `favorite=true/false` | Thêm/bỏ cặp Customer–Staff, không thêm trùng |
| `/staff/details/{id}/evidence` | Multipart `image` | Ownership rồi `EvidenceStorageService.upload` |
| `/staff/bookings/{id}/complete` | CSRF | `BookingCompletionService.completeBooking` |
| `/admin/bookings/{id}/paid` | `receiptVerified=true` | `PaymentLifecycleService.confirmReceivedByAdmin` |
| `/admin/services`, `/admin/services/{id}` | `name`, `description`, `duration`, `price` | Tạo/cập nhật theo Entity hiện có |
| `/admin/staff` | `name`, `email`, `password`, `phone` | `GlobalEmailUniquenessService`, BCrypt trước khi lưu |
| `/admin/staff/{id}` | `name`, `phone` | Sửa thông tin; email/mật khẩu không trong form sửa |
| `/admin/{services,staff}/{id}/delete` | CSRF | Hard delete; FK NO ACTION từ chối khi có phụ thuộc |
| `/admin/schedules`, `/admin/schedules/{id}` | `staffId`, `startAt`, `endAt` | Khoảng linh hoạt, kết thúc sau bắt đầu |

Customer ID, Staff thực hiện và Admin quản lý lấy từ session tương ứng. Giá và thời lượng do Service đọc từ database. `startAt`/`endAt` trong form là local datetime theo UTC+7, chuyển sang `OffsetDateTime` trước khi gọi Service. Các dịch vụ được thực hiện theo thứ tự trong danh mục form.

Form chọn một khoảng sẵn sàng đủ dài cho toàn bộ dịch vụ. Service cũ vẫn có khả năng gán `workScheduleId` riêng từng detail; UI hiện chưa chỉnh từng detail qua nhiều khoảng sẵn sàng. Không thêm địa chỉ, phụ phí hoặc ghi chú đặt lịch từ mẫu vì Entity Booking hiện tại không có các trường này.

Khi cập nhật khoảng sẵn sàng, nhân viên được giữ nguyên và khoảng mới phải tiếp tục bao phủ từng Booking_Detail đã tham chiếu đến nó. Có thể mở rộng hoặc thu hẹp nếu không làm sai thời gian đã đặt; không triển khai di chuyển lịch hẹn sang nhân viên khác. Kiểm tra này dùng cùng khóa Staff với Service đặt lịch để tránh chạy đua giữa cập nhật lịch và đặt mới; hành vi transaction cần nghiệm thu trên SQL Server.

## Cấu hình chạy thật

Môi trường trên máy hiện tại đã được setup và nghiệm thu luồng HTTP với SQL Server ngày 2026-10-16; xem [hướng dẫn local và kết quả thực tế](local-setup.md). Các ghi chú chưa xác minh ở cuối tài liệu mô tả thời điểm trước lần setup này.

Từ `app/housekeeping-management-system/`, dùng JDK 21 và Maven. Cấu hình theo `application.properties`:

| Biến / thuộc tính | Ý nghĩa |
|---|---|
| `DB_URL` | JDBC URL SQL Server và database của bạn |
| `DB_USERNAME` | Tài khoản database |
| `DB_PASSWORD` | Mật khẩu database; không commit |
| `app.evidence.storage-root` | Thư mục ảnh local, mặc định `./var/evidence` |
| `spring.servlet.multipart.max-file-size` | 5 MB mỗi ảnh |
| `spring.servlet.multipart.max-request-size` | 6 MB gồm overhead multipart; UI gửi một ảnh/lần |

```powershell
mvn spring-boot:run
```

Mở [trang chủ](http://localhost:8080/) hoặc [đăng nhập](http://localhost:8080/login). Chạy thông thường cần tài khoản đã được cấp trong database; không có form đăng ký hoặc Admin mặc định. Profile `demo` bổ sung ba tài khoản mẫu công khai theo [hướng dẫn demo](demo-setup.md), tách khỏi schema/migration. `password_hash` hỗ trợ BCrypt chuẩn `$2a$...`/`$2b$...` và `{bcrypt}...`; không hỗ trợ plaintext hoặc placeholder như `integration-test-hash` trong fixture test cũ. Email được trim/lowercase; nếu trùng email giữa nhiều role thì từ chối đăng nhập. Không sửa migration baseline để nhúng tài khoản hay mật khẩu.

Đổi thư mục ảnh khi chạy:

```powershell
mvn spring-boot:run "-Dspring-boot.run.arguments=--app.evidence.storage-root=./var/evidence"
```

Thư mục này cần quyền ghi. Evidence luôn qua `/evidence/{id}`; không thêm static resource mapping công khai đến `var/`. Khi triển khai HTTPS, cấu hình cookie Secure và kết nối SQL Server/certificate phù hợp môi trường.

## Kiểm tra và nghiệm thu

```powershell
# Render, binding, session, CSRF, role và ownership; không cần database
mvn "-DexcludedGroups=sqlserver" verify

# Bao gồm migration/concurrency trên Testcontainers SQL Server; cần Docker
mvn verify
```

Surefire dùng Mockito javaagent từ dependency Maven, không phụ thuộc khả năng tự attach của JDK. Xuất HTML test bằng `mvn "-Dtest=PortalMvcTest" "-Dportal.preview=true" test`; kết quả trong `target/ui-preview/` dùng fixture test, không phải ứng dụng chạy với database. Không commit các file này.

Quy trình nghiệm thu với SQL Server:

1. Chuẩn bị tài khoản ba vai trò; kiểm tra đăng nhập/đăng xuất và route sai role bị từ chối.
2. Admin tạo Staff, Service và khoảng sẵn sàng; email trùng bị từ chối; xóa bản ghi có phụ thuộc không làm mất lịch sử.
3. Customer đặt lịch thành `CONFIRMED`; đặt trùng bị từ chối kể cả hai cửa sổ đồng thời; hủy theo mốc 2 giờ và kiểm tra giải phóng lịch.
4. Staff chỉ thấy việc của mình; tải JPEG/PNG vào từng detail; file hỏng, quá 5 MB và vượt 5 ảnh bị từ chối. Không hoàn thành khi thiếu ảnh hoặc chưa tới giờ bắt đầu.
5. Hoàn thành tạo một Payment `UNPAID`; Customer chọn phương thức; Admin xác minh thực tế trước khi đổi `PAID`.
6. Kiểm tra feedback một lần, yêu thích và sửa URL sang Booking/Evidence của tài khoản khác bị từ chối.

Bằng chứng ban đầu ngày 2026-10-16: build/test web chạy bằng JDK 26.0.1, biên dịch `release 21`; khi đó SQL Server local chưa khả dụng. Sau đó đã setup JDK 21, migrate SQL Server thật, Hibernate validate và kiểm tra thành công luồng MVC/Evidence/Payment/Feedback. Chi tiết và giới hạn tại [kết quả local](local-setup.md); concurrency dùng Docker/Testcontainers vẫn chưa nghiệm thu.

Tham khảo: [Spring Security Form Login](https://docs.spring.io/spring-security/reference/servlet/authentication/passwords/form.html) và [Bootstrap 5.3](https://getbootstrap.com/docs/5.3/getting-started/introduction/).
