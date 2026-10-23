> **Document:** Product Requirements Document\
> **File:** `docs/requirements/PRD.md`\
> **Version:** v1.0.0\
> **Created:** 2026-06-14\
> **Last Updated:** 2026-10-13\
> **Status:** Draft

# Yêu Cầu Sản Phẩm

## 1. Tổng Quan Dự Án

- **Tên dự án:** Housekeeping Management System
- **Mục đích:** Hỗ trợ một công ty giúp việc quản lý Staff part-time, lịch sẵn sàng, Booking, công việc, bằng chứng hoàn thành và các nghiệp vụ sau dịch vụ.
- **Đối tượng sử dụng:** Customer, Staff và Admin.
- **Bối cảnh:** Dự án học phần HSF302; ưu tiên thực hành Spring Boot MVC và kiến trúc N-layer.

### 1.1 Nền tảng kỹ thuật đã chốt

- Giao diện: Spring Boot MVC, Thymeleaf và Bootstrap; render HTML phía server, không tạo frontend React riêng.
- Kiến trúc ứng dụng: MVC/N-layer gồm Controller, Service, Repository, Entity và View; quy tắc nghiệp vụ nằm ở Service, không đặt trong Controller/View.
- Nền tảng: Java 21, Spring Boot 4.1.1, Maven, Microsoft SQL Server. Spring Boot 4.1.1 là framework đã được giảng viên chốt chính thức; tài liệu này được đồng bộ theo quyết định đó.
- Persistence/database change management: Spring Data JPA, Hibernate và JDBC Driver cho SQL Server; dùng Flyway để quản lý các database migration có phiên bản. Hibernate không được tự thay đổi schema đã duyệt; thay đổi schema đi qua migration được phê duyệt.
- Xác thực: Spring Security với HTTP Session; một trang đăng nhập chung `/login` cho Customer, Staff và Admin; phân quyền và điều hướng theo role sau xác thực; không dùng JWT.
- Các quyết định trên là lựa chọn kỹ thuật đã được phê duyệt. Với Spring Boot 4.1.1, dependency Flyway/SQL Server/JPA/Testcontainers đã resolve và biên dịch thành công trong Maven package; khả năng khởi động Flyway và hành vi trên SQL Server cần xác minh runtime. Khả năng ánh xạ xác thực vào dữ liệu hiện có vẫn cần xác minh riêng.

## 2. Business Context, Mục Tiêu Và Ràng Buộc

### 2.1 Business Context và ràng buộc

- Hệ thống phục vụ **một công ty** giúp việc.
- Staff là nhân viên **part-time** của công ty, không phải freelancer độc lập.
- Đây là business context/constraint; hệ thống không cần mô hình hóa công ty như một marketplace hay triển khai hành vi riêng chỉ để thể hiện hai đặc điểm này.
- Các quyết định nghiệp vụ được chuẩn hóa trong Business Baseline V1.0 và truy vết chi tiết tại SRS.

### 2.2 Mục tiêu

- Cho phép Customer chọn Staff và đặt dịch vụ trong khoảng Staff sẵn sàng.
- Giúp Admin quản lý lịch sẵn sàng; hệ thống xác minh lịch trước khi xác nhận Booking.
- Hỗ trợ Staff gửi Evidence cho từng Booking_Detail và chỉ hoàn thành Booking khi đủ điều kiện.
- Hỗ trợ Payment tiền mặt/chuyển khoản thủ công và checkout PayOS, Feedback sau hoàn thành và danh sách Favorite_Staff.

## 3. Phạm Vi

### Trong phạm vi

- Customer chọn một Staff khi tạo Booking; mọi Booking_Detail trong Booking thuộc cùng Staff.
- Customer xem Services, danh sách Staff và khoảng thời gian có thể đặt; xem Booking của mình và trạng thái Payment.
- Staff xem các công việc được Customer chọn/đặt cho mình.
- Admin quản lý Staff và Services ở mức CRUD cơ bản phù hợp với schema được duyệt; xem Booking, trạng thái công việc và thông tin Payment.
- Admin nhập/cập nhật các khoảng sẵn sàng linh hoạt, không bắt buộc ca cố định hoặc lịch lặp.
- Xác minh khoảng thời gian Booking nằm trong lịch rảnh và không giao với Booking khác đang chiếm lịch của cùng Staff; xử lý an toàn các yêu cầu đặt đồng thời.
- Booking hợp lệ tự chuyển `CONFIRMED`; Customer hủy theo điều kiện 2 giờ; Staff phụ trách hoàn thành theo điều kiện trạng thái, thời gian và Evidence.
- Staff upload Evidence ảnh qua Spring Boot; lưu file trên filesystem local và đường dẫn trong database.
- Khi Booking hoàn thành, hệ thống tạo một Payment `UNPAID`; Customer có thể chọn `CASH`/`BANK_TRANSFER` để Admin xác minh thủ công hoặc thanh toán trực tuyến bằng PayOS, chỉ cập nhật `PAID` sau webhook có chữ ký hợp lệ.
- Customer gửi tối đa một Feedback cho Booking `COMPLETED` và thêm/bỏ Staff yêu thích.
- Customer có thể tự đăng ký tài khoản; Staff và Admin được quản trị viên cấp tài khoản. Session-based authentication cho cả ba role; kiểm tra role và ownership tại backend.
- Evidence chỉ nhận JPEG (`.jpg`, `.jpeg`) và PNG (`.png`), kiểm tra nội dung file thực, tối đa 5 MB/ảnh và từ 1 đến tối đa 5 ảnh hợp lệ trên mỗi Booking_Detail; lưu local qua `MultipartFile`.
- Xóa Staff/Services theo hard delete có điều kiện: chỉ xóa khi không có dữ liệu phụ thuộc; nếu có phụ thuộc thì từ chối và giữ nguyên lịch sử, không cascade.

### Ngoài phạm vi

- Freelancer marketplace, AI đề xuất Staff, tự động phân công Staff hoặc Admin duyệt Booking.
- Payment gateway ngoài PayOS, thanh toán nhiều lần/phần, chia phương thức hoặc hoàn tiền.
- Cloud storage, AI xử lý ảnh, Admin kiểm duyệt ảnh, realtime hoặc thông báo tự động.
- Chấm công/tính lương, hợp đồng định kỳ và microservices.
- Trạng thái Booking ngoài `CONFIRMED`, `CANCELLED`, `COMPLETED`; trạng thái Payment ngoài `UNPAID`, `PAID`.
- JWT; mặc định thêm bảng `Users`, `Accounts` hoặc `Roles`.

## 4. Người Dùng Và Trách Nhiệm

| Vai trò | Nhu cầu/trách nhiệm trong phạm vi |
|---|---|
| Customer | Xem Services, Staff và khoảng thời gian có thể đặt; chọn Staff, tạo/xem Booking của mình, hủy đúng điều kiện, xem trạng thái Payment, chọn phương thức sau hoàn thành, gửi Feedback và quản lý Favorite_Staff. |
| Staff | Xem Booking được Customer chọn cho mình, upload Evidence cho đúng Booking_Detail và xác nhận hoàn thành khi mọi điều kiện được đáp ứng. |
| Admin | CRUD cơ bản Staff/Services theo schema được duyệt; quản lý khoảng sẵn sàng, xem Booking/trạng thái công việc/thông tin Payment và xác minh tiền mặt/chuyển khoản trước khi đánh dấu `PAID`. |

Thông tin xác thực hiện có trên các thực thể trong ERD chưa được xác minh; yêu cầu session-based authentication vẫn là yêu cầu đã chốt. Khả năng ánh xạ yêu cầu này vào cấu trúc dữ liệu hiện có được ghi riêng là `UNVERIFIED` trong SRS.

## 5. Yêu Cầu Sản Phẩm Cấp Cao

Các yêu cầu chi tiết, luồng thay thế, tiêu chí nghiệm thu và truy vết BR → FR → AC nằm tại SRS.

| Nhóm | Kết quả sản phẩm |
|---|---|
| Xác thực và quyền | Session-based login cho ba vai trò; backend kiểm tra vai trò và quyền sở hữu. |
| Danh mục và công việc | Customer xem Services/Staff/thời gian đặt; Staff xem công việc được giao; Admin quản lý Staff/Services cơ bản và xem Booking. |
| Booking và lịch | Khách chọn Staff; lịch được kiểm tra trước khi Booking tự xác nhận; không cho đặt giao lịch đang chiếm. |
| Hủy và hoàn thành | Hủy giữ dữ liệu và giải phóng lịch; Staff phụ trách có thể hoàn thành Booking đang xác nhận bất cứ lúc nào khi đủ Evidence từng chi tiết, không cần chờ giờ bắt đầu dự kiến. |
| Evidence | Upload ảnh qua Spring Boot, lưu local và lưu đường dẫn trong database. |
| Payment | Một Payment `UNPAID` sau hoàn thành; Customer chọn tiền mặt/chuyển khoản để Admin xác minh hoặc dùng PayOS; webhook PayOS hợp lệ xác nhận `PAID`. |
| Feedback và Favorite | Feedback sau hoàn thành, tối đa một/Booking; Favorite_Staff không trùng cặp Customer–Staff. |

## 6. Yêu Cầu Chất Lượng Và Ràng Buộc

- Quyền truy cập và ownership phải được kiểm tra tại backend.
- Việc kiểm tra lịch phải đảm bảo kết quả đúng khi có yêu cầu đồng thời; cách triển khai Service/transaction sẽ được quyết định trong thiết kế kỹ thuật.
- Hệ thống không được lưu Evidence lên cloud.
- Java 21, Spring Boot 4.1.1, Maven, SQL Server, Spring Data JPA/Hibernate, Flyway, Spring Security, Thymeleaf và Bootstrap là nền tảng kỹ thuật đã chốt. Spring Boot 4.1.1 được ghi nhận theo quyết định chính thức của giảng viên; Flyway runtime, Hibernate validation, DATETIMEOFFSET round-trip và concurrency cần được xác minh trên SQL Server/Testcontainers.
- Evidence phải là JPEG/PNG hợp lệ theo nội dung file, tối đa 5 MB mỗi ảnh và tối đa 5 ảnh trên mỗi Booking_Detail; các giới hạn này đã được quyết định và được đặc tả tại SRS.
- Hard delete Staff/Services phải từ chối khi tồn tại dữ liệu phụ thuộc và không cascade xóa lịch sử; các FK/phụ thuộc vật lý cần xác minh trước khi triển khai.

## 7. Giả Định Và Phụ Thuộc Chưa Xác Minh

- Khả năng lưu Payment khi Customer chưa chọn phương thức sau thời điểm Booking hoàn thành chưa được xác minh từ schema. Nghiệp vụ không tạo trạng thái Payment mới để mô tả tình huống này.
- Cấu trúc dữ liệu đăng nhập/session, thuộc tính/PK/FK/cardinality/constraint vật lý và khả năng hỗ trợ các quy tắc nghiệp vụ trong schema hiện có chưa được xác minh. Tên DBMS, Java/Spring Boot và build tool đã được chốt ở mục 1.1; việc kiểm tra tương thích thực tế vẫn còn mở.

## 8. Nghiệm Thu Và Phê Duyệt

PRD được xem là nhất quán khi phạm vi phản ánh đúng Business Baseline V1.0, business constraint được tách khỏi hành vi chức năng, nội dung ngoài phạm vi được nêu rõ và mọi yêu cầu chi tiết có thể truy vết tới SRS. Các mục chưa có bằng chứng thiết kế phải được ghi riêng, không làm thay đổi trạng thái phê duyệt nghiệp vụ.
