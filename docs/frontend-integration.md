> **Document:** Frontend–Backend Integration Guide\
> **File:** `docs/frontend-integration.md`\
> **Version:** v2.4.0\
> **Created:** 2026-10-16\
> **Last Updated:** 2026-11-08\
> **Status:** Draft

# Kết Nối Giao Diện Gọn Nhà Với Backend

## Phạm vi và căn cứ

Giao diện được chuyển từ `UI_UX Design Refinement.zip` sang nền tảng đã chốt trong [PRD](requirements/PRD.md) và [SRS](requirements/SRS.md): Spring MVC + Thymeleaf + Bootstrap. ZIP là nguồn tham khảo thiết kế, không phải nguồn quyết định nghiệp vụ hoặc hướng dẫn triển khai. Không sao chép `.git`, `.figma`, thiết lập IDE, script triển khai hay tài liệu chỉ dẫn của ZIP vào ứng dụng.

Giữ tông xanh ngọc, ảnh, thẻ dịch vụ, khu vực giới thiệu, form và bố cục dashboard; dùng tiếng Việt, trạng thái rỗng, thông báo lỗi và bố cục responsive. Ảnh dịch vụ chỉ là minh họa, không phải thuộc tính mới trong database.

Không sao chép các chức năng ngoài baseline từ mẫu: tự chuyển vai trò trên giao diện, dữ liệu localStorage/mock, đặt lịch định kỳ, tự điều phối nhân viên và các trạng thái pending/assigned/in_progress. Customer chọn địa chỉ từ VietMap; frontend chỉ gọi endpoint backend, tuyệt đối không nhận Services API key. Admin được trả lời và giải quyết Feedback dạng văn bản; không dùng quy trình kiểm duyệt Feedback của mẫu.

Ứng dụng hiện tổ chức theo MVC–N-Layer: Controller nhận request và dựng view model; Service xử lý use case, phân quyền sở hữu và transaction; Repository/JDBC truy vấn SQL Server; Entity ánh xạ 15 bảng của baseline mới. Các luồng được chia theo nghiệp vụ (Booking, Payment, Staff, House, Feedback, Company Settings); không đặt quy tắc nghiệp vụ trong template hoặc Controller. Cấu trúc package mặc định được mô tả trong [hướng dẫn Java](architecture/java-package-structure.md).

## Cách kết nối

```text
Trình duyệt → Spring Security (session, role, CSRF)
            → Controller MVC (bind/validate form)
            → Service nghiệp vụ / lớp thao tác portal
            → Repository hoặc JDBC → SQL Server
            → View model → Thymeleaf → HTML
```

Frontend và backend cùng ứng dụng, cùng origin. Form dùng POST và Thymeleaf tự chèn CSRF token qua `th:action`; không cần REST API riêng, JWT, CORS hoặc Node.js khi chạy. JavaScript hỗ trợ lọc danh sách, tạm tính, kiểm tra file và ghép ngày với giờ được chọn theo bước 15 phút. Server kiểm tra lại khi gửi, kể cả khi dữ liệu vừa thay đổi.

Trang `/admin/settings` lưu vị trí/bán kính riêng tại `/admin/settings/location`; phiên bản lịch tuần được lưu tại `/admin/settings/schedule` với `effectiveFrom` và danh sách `operatingHours`. GET `/admin/settings?effectiveFrom=YYYY-MM-DD` chọn lịch để xem/sửa; ngày mới nạp lịch gần nhất trước ngày chọn. Phiên bản tương lai có thể hủy qua `/admin/settings/schedule/cancel`; backend chặn sửa/hủy nếu khoảng hiệu lực có booking `CONFIRMED` hoặc `AWAITING_PAYMENT`. Staff nhận tất cả mốc hiệu lực để giao diện hiển thị khung áp dụng theo ngày; server kiểm tra lại cùng phiên bản khi đăng ký availability. Booking tạo mới và Admin đổi Staff dùng truy vấn giờ theo ngày từ cùng bảng.

`PortalQueryService` đọc dữ liệu bằng JDBC vào view model không chứa password hash/storage path. Không đưa Entity có quan hệ LAZY ra template, phù hợp `open-in-view=false`. Danh sách lịch hẹn được lọc theo tài khoản; chi tiết và ảnh Evidence kiểm tra ownership. Khoảng bận cho form đặt lịch chỉ gồm Staff ID và thời gian, không có danh tính khách khác.

## Các trang

Các route dưới đây là điểm vào MVC của ứng dụng.

| Vai trò | GET | Nội dung |
|---|---|---|
| Công khai | `/`, `/services`, `/login`, `/register` | Trang chủ, dịch vụ, đăng nhập và đăng ký Customer |
| Đã đăng nhập | `/portal` | Điều hướng theo vai trò từ database |
| Customer | `/customer/bookings` | Lịch của chính khách và thanh toán |
| Customer | `/customer/bookings/new` | Nhiều dịch vụ nối tiếp, một nhân viên, một khoảng sẵn sàng |
| Customer | `/customer/houses` | Danh sách, tạo, sửa và ẩn địa chỉ nhà của chính khách |
| Customer | `/customer/staff`, `/customer/schedules` | Nhân viên/yêu thích, khoảng sẵn sàng |
| Staff | `/staff/bookings`, `/staff/schedules`, `/staff/leaves`, `/staff/profile` | Công việc, khoảng sẵn sàng, đơn nghỉ và hồ sơ của chính Staff |
| Admin | `/admin/bookings`, `/admin/staff`, `/admin/services`, `/admin/schedules`, `/admin/leaves`, `/admin/settings`, `/admin/feedback` | Quản lý lịch, nhân viên, dịch vụ, giờ hoạt động, đơn nghỉ và phản hồi |
| Từng vai trò | `/{customer,staff,admin}/bookings/{id}` | Chi tiết, Evidence và hành động đúng vai trò |
| Người được phép xem Booking | `/evidence/{id}` | Ảnh sau kiểm tra ownership, không public thư mục lưu ảnh |

## Hợp đồng form và Service

| POST | Dữ liệu gửi | Xử lý |
|---|---|---|
| `/login` | `email`, `password`, CSRF | Xác thực tài khoản dùng chung trong `Users`; tạo session theo role |
| `/register` | `name`, `email`, `phone`, `password`, `confirmPassword`, CSRF | Đăng ký Customer; chuẩn hóa và kiểm tra email duy nhất toàn hệ thống |
| `/logout` | CSRF | Spring Security hủy session |
| `/customer/houses/autocomplete?q=...` | Chuỗi tìm kiếm từ 3 ký tự | Backend gọi VietMap Autocomplete; trả suggestion `refId` và display, không lộ key |
| `/customer/houses` | `recipientName`, `recipientPhone`, `vietmapRefId`, `addressType`, `defaultAddress`, `addressNote` | Backend phân giải Place và lưu address/tọa độ từ kết quả VietMap |
| `/customer/houses/{id}` | Các trường House như trên | Cập nhật House thuộc Customer sau khi chọn lại kết quả VietMap |
| `/customer/houses/{id}/delete` | CSRF | Ẩn House nếu không có Booking CONFIRMED/AWAITING_PAYMENT; giữ bản ghi lịch sử |
| `/customer/bookings` | `staffId`, `houseId`, `startAt`, `serviceIds` lặp, `areaNote` | Tạo một booking qua `BookingReservationService.createBooking` (luồng tương thích đơn lẻ) |
| `/customer/bookings/multiple` | `houseId`, `serviceIds` lặp, `workDates`, `staffIds`, `startTimes` theo cùng chỉ số, `areaNote` | Tạo một Booking riêng cho từng ngày; giờ và Staff chọn độc lập. Ngày lỗi được báo riêng, ngày hợp lệ khác vẫn được tạo |
| `/customer/bookings/availability-options` | `workDate`, `houseId`, `serviceIds` lặp, tùy chọn `staffId`, `startTime` | Customer phải sở hữu House; trả giờ/Staff phù hợp. Khi gửi `startTime`, kiểm tra tuyến theo tọa độ House và trả lý do riêng nếu trùng giờ, thiếu thời gian trước/sau hoặc VietMap không xác minh được |
| `/customer/bookings/availability-dates` | `serviceIds` lặp, tùy chọn `staffId` | Chỉ trả ngày trong 30 ngày tới còn ít nhất một mốc và Staff phù hợp; ngày hiện tại áp dụng lead time 120 phút |
| `/customer/bookings/house-service-area` | `houseId` | Chỉ Customer đang đăng nhập; backend xác minh quyền sở hữu, tọa độ và bán kính công ty trước khi form cho gửi booking |
| `/customer/bookings/{id}/cancel` | CSRF | Owner, CONFIRMED, còn ít nhất 2 giờ |
| `/customer/bookings/{id}/arrived` | CSRF | Owner ghi nhận Staff đã đến từ giờ bắt đầu; timestamp độc lập, không đổi trạng thái |
| `/customer/bookings/{id}/payment` | CSRF | Owner; tạo hoặc mở lại checkout ONLINE qua VNPay sandbox (hỏi VNPay về lần trước còn chờ để tránh thu hai lần) |
| `/customer/bookings/{id}/feedback` | `content` dạng văn bản | Customer sở hữu Booking đã hoàn thành, tối đa một Feedback; không có chấm điểm |
| `/customer/staff/{id}/favorite` | `favorite=true/false` | Thêm/bỏ cặp Customer–Staff, không thêm trùng |
| `/staff/bookings/{id}/evidence` | Multipart `image` | Ownership rồi `EvidenceStorageService.upload` |
| `/staff/bookings/{id}/complete` | CSRF | `BookingCompletionService.completeBooking` |
| `/staff/bookings/{id}/cash-payment` | CSRF | Nhân viên được phân công xác nhận đã nhận CASH |
| `/payments/vnpay/return` | Browser redirect GET từ VNPay; public | Kiểm tra chữ ký rồi tra cứu QueryDR; xác nhận khi VNPay báo thành công (nếu QueryDR không dùng được thì dựa vào dữ liệu trả về có chữ ký, trạng thái `00` và đúng số tiền), sau đó về trang chi tiết booking kèm thông báo |
| `/payments/vnpay/ipn` | GET server-to-server từ VNPay; public, không dùng session | Kiểm tra chữ ký, đơn và số tiền, xử lý idempotent; trả JSON `RspCode`/`Message` theo quy ước VNPay |
| `/admin/services`, `/admin/services/{id}` | `name`, `description`, `duration`, `price` | Tạo/cập nhật theo Entity hiện có |
| `/admin/services/{id}/activate`, `/admin/staff/{id}/activate` | CSRF | Kích hoạt lại dịch vụ hoặc tài khoản Staff đã ngừng hoạt động |
| `/admin/staff` | `name`, `email`, `password`, `phone` | `GlobalEmailUniquenessService`, BCrypt trước khi lưu |
| `/admin/staff/{id}` | `name`, `phone` | Sửa thông tin; email/mật khẩu không trong form sửa |
| `/admin/{services,staff}/{id}/delete` | CSRF | Ngừng hoạt động (soft delete); giữ lịch sử đặt dịch vụ |
| `/admin/settings` | Vị trí công ty, bán kính phục vụ và nhiều khung giờ/ngày | Lưu khi không làm mất hiệu lực Booking CONFIRMED tương lai |
| `/admin/feedback/{id}/reply` | `reply`, CSRF | Admin lưu nội dung trả lời Feedback |
| `/admin/feedback/{id}/resolve` | CSRF | Admin đánh dấu Feedback đã giải quyết |
| `/admin/bookings/{id}/cancel` | `reason`, CSRF | Admin hủy Booking CONFIRMED, lưu người hủy/thời điểm/lý do |
| `/admin/bookings/{id}/reassign` | `staffId`, CSRF | Đổi Staff cho Booking CONFIRMED sau khi kiểm tra availability, nghỉ và xung đột |
| `/staff/schedules`, `/staff/schedules/{id}/delete` | `workDate`, `startTime`, `endTime` | Staff PART_TIME tạo khoảng trong khung Admin theo mốc 15 phút hoặc deactivate; không xóa cứng |
| `/staff/profile` | `avatarUrl`, `bio`, CSRF | Staff tự cập nhật hồ sơ; chỉ nhận URL avatar HTTPS |
| `/staff/leaves` | `startDate`, `endDate`, `reason` | Staff gửi đơn nghỉ theo ngày nguyên; trạng thái ban đầu PENDING |
| `/admin/leaves/{id}/approve`, `/admin/leaves/{id}/reject` | CSRF | Admin chỉ xét đơn PENDING; ghi reviewer và thời điểm xét duyệt |

Customer ID và Staff ID lấy từ session, không tin ID vai trò do trình duyệt gửi. Giá và thời lượng do Service đọc từ SQL Server. `startAt` là local datetime theo UTC+7. Backend lưu snapshot cho từng dịch vụ và tổng hợp giá/thời lượng; schema không lưu thứ tự thực hiện dịch vụ.

Mỗi Booking chọn một Staff, House đã chọn qua VietMap và thời điểm bắt đầu. Form kiểm tra quyền sở hữu và bán kính ngay khi chọn nhà; backend kiểm tra lại lúc lưu. Biểu mẫu Customer hỗ trợ tick nhiều ngày, chọn giờ và Staff riêng cho mỗi ngày, rồi gửi một lần; mỗi ngày được lưu như Booking riêng. Nếu ngày thất bại ở bước lưu cuối, ngày đó cùng dịch vụ, nhà, ghi chú, giờ và Staff được giữ để khách sửa/thử lại; ngày đã lưu không gửi lại. Backend tự tính thời lượng/giá từ Services; kiểm tra phút :00/:15/:30/:45, toàn bộ interval trong cùng khung công ty và availability (nếu PART_TIME), lead time 120 phút, horizon 30 ngày, bán kính từ vị trí công ty, booking chồng giờ, giới hạn quãng đường tuyến giữa hai booking cùng Staff không vượt `serviceRadiusKm` đang cấu hình, và khoảng trống cùng Staff `ETA tuyến xe máy VietMap + 15 phút`. Chặng giữa hai booking dùng địa chỉ trước→sau, giờ xuất phát là lúc booking trước kết thúc. Chặng đầu ngày đi từ tâm công ty; hệ thống tính lùi giờ xuất phát từ giờ booking theo ETA tuyến cơ sở + 15 phút dự phòng. Lỗi/không có tuyến từ chối ngày đó. Khi khách chọn giờ, giao diện kiểm tra tuyến với House đã chọn; giờ chồng lịch bị ẩn khỏi các mốc chọn, còn giờ thiếu thời gian tuyến hoặc vượt giới hạn km bị bỏ và báo lý do cùng thông tin tuyến. Nếu mốc bị loại, giữ nhân viên và mở thẻ ngày để khách chọn giờ khác ngay. Lưới vẫn theo 15 phút: nếu đủ điều kiện lúc 10:35 thì mốc sớm nhất chọn được là 10:45. Nếu VietMap lỗi, giao diện báo chưa xác minh được tuyến. Backend lặp kiểm tra khi lưu và báo lỗi theo từng ngày. Không tính phí di chuyển; không dùng `Work_Schedule` hoặc ID lịch do client quyết định.

Staff PART_TIME chỉ được tạo/ngừng hiệu lực khoảng của chính mình. Không thể ngừng khoảng nếu giao cắt với Booking CONFIRMED tương lai; Admin phải đổi Staff hoặc hủy booking trước. Booking dùng VietMap Services key ở backend (`VIETMAP_API_KEY`); có thể cấu hình `VIETMAP_API_BASE_URL`, mặc định `https://maps.vietmap.vn`. Địa chỉ nhà phải được chọn từ autocomplete và có tọa độ. Route v4 nhận giờ khởi hành để tránh vùng hạn chế theo thời gian; tham số này không biến ETA thành dự báo kẹt xe tương lai. Traffic layer là dữ liệu giao thông hiện thời, không xác nhận dự báo cho giờ đặt sau này. ETA dùng để chặn lịch là ước tính tuyến cơ sở cộng đúng 15 phút dự phòng. Nếu Staff dự kiến trễ, Staff báo Admin sớm để Admin liên hệ khách; không tự động dời/hủy booking hay thêm quy tắc hoàn tiền trong luồng này. Các thao tác lịch và đặt chỗ khóa cùng Staff; nghiệm thu cạnh tranh trên SQL Server cần Docker/Testcontainers.

## Cấu hình chạy thật

`local-setup.md` là tài liệu lịch sử của schema cũ 11 bảng và được đánh dấu Deprecated. Các kết quả SQL Server/HTTP trong đó không phải bằng chứng cho baseline refactor hiện tại (15 bảng trong `db/refactor`). Chỉ xem [hướng dẫn demo hiện tại](demo-setup.md) và kết quả kiểm tra được thực hiện trực tiếp trên baseline này khi nghiệm thu.

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
2. Admin cấu hình thông tin công ty, bán kính (mặc định khởi điểm 10km) và ít nhất một khung giờ/ngày tại `/admin/settings`; nhiều khung trong ngày không chồng lấn. Tất cả đầu mốc theo 15 phút. Thay đổi làm Booking tương lai mất hợp lệ bị chặn và báo IDs. Admin tạo Staff và Service; email trùng bị từ chối. Staff PART_TIME đăng ký khoảng nằm trọn trong một khung giờ công ty; FULL_TIME theo khung Admin. Staff gửi đơn nghỉ theo ngày; Admin duyệt chỉ khi không còn Booking CONFIRMED tương lai bị ảnh hưởng, và thấy liên kết đến các Booking đó.
3. Customer đặt lịch thành `CONFIRMED`; đặt trùng bị từ chối kể cả hai cửa sổ đồng thời; hủy theo mốc 2 giờ và kiểm tra giải phóng lịch.
4. Staff chỉ thấy việc của mình; tải JPEG/PNG vào từng detail; file hỏng, quá 5 MB và vượt 5 ảnh bị từ chối. Không hoàn thành khi thiếu ảnh hoặc chưa tới giờ bắt đầu.
5. Hoàn thành chuyển Booking sang `AWAITING_PAYMENT`; Staff được phân công xác nhận CASH hoặc Customer thanh toán ONLINE qua VNPay sandbox (xác nhận bằng QueryDR hoặc IPN); Payment thành `SUCCESS` thì Booking mới thành `COMPLETED`.
6. Kiểm tra feedback một lần, yêu thích và sửa URL sang Booking/Evidence của tài khoản khác bị từ chối.

Admin quản lý Booking tại trang chi tiết `/admin/bookings/{id}`. Chỉ Booking `CONFIRMED` có nút hủy/đổi nhân viên; hủy yêu cầu lý do và lưu người hủy/thời gian. Đổi nhân viên chỉ cập nhật `staff_id`, giữ `original_staff_id`; nhân viên thay thế phải đang hoạt động, không nghỉ đã duyệt, có availability nếu PART_TIME, nằm trong giờ Admin và đủ khoảng tuyến. Customer chủ Booking ghi nhận Staff đã đến từ giờ bắt đầu; ghi nhận không đổi trạng thái. Booking `AWAITING_PAYMENT`/`COMPLETED` không được hủy hoặc chuyển nhân viên vì quy tắc hoàn tiền/xử lý sau hoàn thành chưa được chốt trong SRS.

Các ghi nhận runtime trong [tài liệu local lịch sử](local-setup.md) thuộc schema cũ và không xác nhận Flyway, Hibernate mapping hoặc luồng giao dịch của baseline 15 bảng hiện tại. Với baseline hiện tại, cần chạy `mvn verify` trên SQL Server Testcontainers hoặc SQL Server development riêng; nếu không có runtime đó, báo rõ các test SQL Server chưa được xác minh. Không dùng kết quả của database `hsf302` cũ làm bằng chứng cho schema mới.

Tham khảo: [Spring Security Form Login](https://docs.spring.io/spring-security/reference/servlet/authentication/passwords/form.html) và [Bootstrap 5.3](https://getbootstrap.com/docs/5.3/getting-started/introduction/).
