> **Document:** Software Requirements Specification\
> **File:** `docs/requirements/SRS.md`\
> **Version:** v1.0.0\
> **Created:** 2026-06-14\
> **Last Updated:** 2026-10-13\
> **Status:** Draft

# Đặc Tả Yêu Cầu Phần Mềm (SRS)

## 1. Giới Thiệu

### 1.1 Mục đích

Tài liệu xác định yêu cầu phần mềm cho Housekeeping Management System trong học phần HSF302. Business Baseline V1.0 là nguồn chuẩn cho nghiệp vụ. PRD mô tả mục tiêu và phạm vi sản phẩm; SRS này mô tả hành vi, điều kiện, trường hợp lỗi và tiêu chí nghiệm thu có thể kiểm chứng.

### 1.2 Phạm vi

Hệ thống hỗ trợ một công ty giúp việc quản lý Staff part-time, lịch sẵn sàng, Booking, Evidence, Payment, Feedback và Favorite_Staff. Phạm vi chức năng và các nội dung loại trừ được mô tả trong [PRD](PRD.md).

### 1.3 Thuật ngữ và trạng thái

| Thuật ngữ/trạng thái | Ý nghĩa |
|---|---|
| Customer | Khách chọn Staff và tạo Booking. |
| Staff | Nhân viên part-time thực hiện Booking được Customer chọn. |
| Admin | Quản lý Work_Schedule và xác minh nhận Payment tiền mặt/chuyển khoản. |
| Booking_Detail | Chi tiết dịch vụ thuộc một Booking. |
| Evidence | Ảnh bằng chứng gắn với đúng Booking_Detail và do Staff phụ trách upload. |
| `CONFIRMED` | Booking hợp lệ được xác nhận tự động. |
| `CANCELLED` | Booking đã hủy theo điều kiện; dữ liệu được giữ và không tiếp tục chiếm lịch. |
| `COMPLETED` | Booking hoàn tất sau khi Staff phụ trách đáp ứng các điều kiện hoàn thành. |
| `UNPAID` / `PAID` | Payment chưa hoàn tất / đã được xác nhận bởi Admin (thủ công) hoặc IPN MoMo hợp lệ. |
| `CASH` / `BANK_TRANSFER` / `MOMO` | Tiền mặt và chuyển khoản được Admin xác minh thủ công; MoMo checkout được xác nhận bằng IPN. |

BR-01 về một công ty và Staff part-time là **business context/constraint**, không phải functional requirement. Xem mục 2.3.

## 2. Bối Cảnh Sản Phẩm

### 2.1 Vai trò và trách nhiệm

| Actor | Trách nhiệm |
|---|---|
| Customer | Xem Services, Staff và khoảng thời gian có thể đặt; chọn Staff, tạo/xem Booking của mình, hủy theo điều kiện, xem trạng thái Payment, chọn phương thức sau khi hoàn thành, gửi Feedback và quản lý Favorite_Staff. |
| Staff | Xem Booking được chọn cho mình, upload Evidence cho đúng Booking_Detail và xác nhận hoàn thành khi đủ điều kiện. |
| Admin | CRUD cơ bản Staff/Services theo schema được duyệt; nhập/cập nhật thời gian sẵn sàng; xem Booking/trạng thái công việc/thông tin Payment; xác minh nhận tiền và cập nhật Payment. |

### 2.2 Xác thực và phân quyền

Sử dụng Spring Security với HTTP Session. Customer, Staff và Admin dùng chung trang `/login`; sau xác thực, hệ thống nhận diện role và điều hướng theo role. Backend phải xác minh role, quyền sở hữu Booking và Staff được giao cho mọi thao tác tương ứng. Không sử dụng JWT và không mặc định thêm bảng Users, Accounts hoặc Roles. Trường định danh, password hash và cách ánh xạ principal vào cấu trúc dữ liệu hiện có vẫn `UNVERIFIED`.

### 2.3 Nền tảng và kiến trúc kỹ thuật đã chốt

- Giao diện dùng Spring Boot MVC, Thymeleaf và Bootstrap, render HTML phía server; không tạo frontend React tách riêng.
- Ứng dụng theo MVC/N-layer: Controller nhận request và điều hướng; Service xử lý nghiệp vụ; Repository truy cập dữ liệu; Entity ánh xạ dữ liệu; Thymeleaf là View. Không đặt quy tắc nghiệp vụ trong Controller/View.
- Java 21; Spring Boot 4.1.1; Maven; Microsoft SQL Server. Spring Boot 4.1.1 là framework đã được giảng viên chốt chính thức; nội dung SRS được đồng bộ theo quyết định đó.
- Persistence dùng Spring Data JPA, Hibernate và JDBC Driver cho SQL Server; Flyway quản lý các database migration có phiên bản. Hibernate không được tự thay đổi schema đã duyệt; thay đổi schema đi qua migration được phê duyệt.
- Lựa chọn dùng Flyway đã được phê duyệt. Flyway SQL Server module, SQL Server JDBC Driver, Spring Data JPA/Hibernate và Testcontainers SQL Server đã resolve/biên dịch với Spring Boot 4.1.1 trong Maven package. Migration được đặt tại `classpath:db/migration` và Hibernate chỉ validate; Flyway runtime, Hibernate validation, DATETIMEOFFSET round-trip và concurrency trên SQL Server vẫn `UNVERIFIED` đến khi acceptance tests chạy thành công trên SQL Server/Testcontainers. Các lựa chọn công nghệ đã được chốt không mang trạng thái `UNVERIFIED`; chỉ các chi tiết triển khai chưa kiểm chứng mới mang nhãn đó.

### 2.4 Business Context/Constraints

| Mã | Constraint đã phê duyệt |
|---|---|
| BC-01 (BR-01) | Hệ thống phục vụ một công ty; Staff là nhân viên part-time, không phải freelancer độc lập. Đây là ranh giới nghiệp vụ, không hàm ý phải thêm entity Company. |

## 3. Functional Requirements

Mỗi requirement dưới đây có acceptance criteria mang mã duy nhất. Trạng thái yêu cầu trong mục này là đã phê duyệt theo baseline; độ xác minh ERD được tách riêng tại Phụ lục A.

### FR-01: Customer registration and session-based authentication

- **Actor:** Khách chưa đăng nhập đăng ký Customer; Customer, Staff và Admin đăng nhập.
- **Precondition:** Khi đăng nhập, người dùng có thông tin hợp lệ thuộc một role; khi đăng ký, người dùng chưa đăng nhập và cung cấp dữ liệu hợp lệ.
- **Main flow:** Khách tạo tài khoản Customer; sau đó người dùng đăng nhập qua trang chung, hệ thống xác thực và duy trì session gắn với danh tính, role cho các request tiếp theo.
- **Alternative/error:** Email đã được dùng hoặc form đăng ký không hợp lệ thì không tạo tài khoản; thông tin đăng nhập không hợp lệ không tạo session; request được bảo vệ từ người chưa đăng nhập không thực hiện thao tác.
- **Postcondition:** Đăng ký tạo một Customer, không tự động đăng nhập; đăng nhập thành công tạo session đúng role hiện hành; không dùng JWT.
- **Acceptance criteria:**
  - **AC-01.1:** Đăng nhập hợp lệ qua trang `/login` chung tạo HTTP Session gắn với đúng principal và đúng một role Customer, Staff hoặc Admin; sau xác thực hệ thống điều hướng theo role.
  - **AC-01.2:** Đăng nhập thất bại không tạo session đã xác thực.
  - **AC-01.3:** Request chưa đăng nhập không thực hiện được thao tác yêu cầu xác thực.
  - **AC-01.4:** Khách chưa đăng nhập có thể tạo tài khoản Customer qua `/register` bằng họ tên tối đa 150 ký tự, email hợp lệ tối đa 254 ký tự, mật khẩu và xác nhận mật khẩu; số điện thoại tối đa 20 ký tự là tùy chọn. Không cho chọn role, tài khoản tự tạo luôn là Customer.
  - **AC-01.5:** Email phải chưa được dùng ở Customer, Staff hoặc Admin; mật khẩu tối thiểu 8 và tối đa 72 ký tự, không quá 72 byte UTF-8, xác nhận phải khớp. Dữ liệu sai hoặc email trùng không tạo tài khoản; mật khẩu được lưu dạng BCrypt.
  - **AC-01.6:** Đăng ký thành công điều hướng về `/login`; người dùng đăng nhập theo luồng AC-01.1 để tạo session, không tự động đăng nhập.

### FR-02: Backend role, ownership và Staff assignment authorization

- **Actor:** Customer, Staff, Admin.
- **Precondition:** Request có session đã xác thực.
- **Main flow:** Backend kiểm tra role và quan hệ sở hữu/được giao trước khi đọc hoặc thay đổi dữ liệu nghiệp vụ.
- **Alternative/error:** Từ chối request khi sai role, Customer không sở hữu Booking, hoặc Staff không được chọn cho Booking.
- **Postcondition:** Chỉ thao tác đúng quyền mới được thực hiện.
- **Acceptance criteria:**
  - **AC-02.1:** Customer A không xem, hủy hoặc gửi Feedback cho Booking của Customer B.
  - **AC-02.2:** Staff không xem thao tác được bảo vệ hoặc hoàn thành Booking được giao cho Staff khác.
  - **AC-02.3:** Chỉ Admin thực hiện được thao tác quản lý Work_Schedule và xác minh Payment.

### FR-03: Customer tạo Booking và chọn Staff

- **Actor:** Customer.
- **Precondition:** Customer đã đăng nhập; dịch vụ, Staff và khoảng thời gian được chọn hợp lệ.
- **Main flow:** Customer chọn Services, một Staff và khoảng thời gian; gửi một hoặc nhiều Booking_Detail trong cùng Booking; hệ thống ghi nhận một Staff chung cho toàn Booking.
- **Alternative/error:** Từ chối nếu thiếu lựa chọn bắt buộc hoặc các detail không cùng Staff.
- **Postcondition:** Booking được tạo để kiểm tra lịch theo FR-05; trạng thái hợp lệ chuyển theo FR-06.
- **Acceptance criteria:**
  - **AC-03.1:** Booking có nhiều Booking_Detail thì tất cả thuộc Staff mà Customer đã chọn khi tạo Booking.
  - **AC-03.2:** Không có luồng Admin thay Customer phân công Staff.

### FR-04: Admin quản lý lịch sẵn sàng

- **Actor:** Admin.
- **Precondition:** Admin đã đăng nhập; Staff được quản lý tồn tại.
- **Main flow:** Admin nhập hoặc cập nhật các khoảng thời gian Staff sẵn sàng nhận việc.
- **Alternative/error:** Từ chối khoảng thời gian không hợp lệ; quy tắc định dạng/thứ tự thời gian cần được mô tả khi dữ liệu thời gian trong ERD được xác minh.
- **Postcondition:** Khoảng sẵn sàng được dùng cho kiểm tra Booking theo FR-05.
- **Acceptance criteria:**
  - **AC-04.1:** Admin có thể ghi nhận và cập nhật khoảng sẵn sàng cho Staff.
  - **AC-04.2:** Lịch linh hoạt; không bắt buộc ca cố định hoặc lịch lặp.

### FR-05: Xác minh lịch và ngăn đặt trùng

- **Actor:** Hệ thống khi xử lý yêu cầu tạo Booking.
- **Precondition:** Booking và khoảng thời gian được cung cấp theo FR-03.
- **Main flow:** Xác minh toàn bộ khoảng thời gian Booking nằm trong lịch rảnh của Staff đã chọn; xác minh không giao với Booking khác đang chiếm lịch của cùng Staff; các Booking_Detail cùng Booking được thực hiện nối tiếp trong khoảng chung.
- **Alternative/error:** Từ chối Booking ngoài lịch, giao lịch đang chiếm hoặc yêu cầu cạnh tranh không còn khả dụng.
- **Postcondition:** Không có hai Booking được xác nhận cùng chiếm một slot của cùng Staff do yêu cầu đồng thời.
- **Acceptance criteria:**
  - **AC-05.1:** Booking nằm ngoài khoảng sẵn sàng bị từ chối.
  - **AC-05.2:** Booking giao thời gian với Booking khác đang chiếm lịch của cùng Staff bị từ chối.
  - **AC-05.3:** Khi hai request đồng thời đặt cùng slot cho cùng Staff, tối đa một request thành công; request còn lại bị từ chối hoặc báo slot không còn khả dụng.
  - **AC-05.4:** Các Booking_Detail trong cùng Booking nối tiếp trong khoảng thời gian chung và không chồng lấn.
  - **AC-05.5:** Booking `CANCELLED` không được tính là Booking đang chiếm lịch.

### FR-06: Tự động xác nhận Booking hợp lệ

- **Actor:** Hệ thống.
- **Precondition:** Booking vượt qua toàn bộ kiểm tra tại FR-05.
- **Main flow:** Hệ thống chuyển Booking sang `CONFIRMED` tự động.
- **Alternative/error:** Booking không hợp lệ không được xác nhận.
- **Postcondition:** Booking hợp lệ ở trạng thái `CONFIRMED`; không chờ Admin phê duyệt.
- **Acceptance criteria:**
  - **AC-06.1:** Booking hợp lệ chuyển sang `CONFIRMED` mà không cần thao tác duyệt của Admin.
  - **AC-06.2:** Booking không đạt điều kiện lịch không được tạo như Booking `CONFIRMED`.

### FR-07: Customer hủy Booking

- **Actor:** Customer sở hữu Booking.
- **Precondition:** Booking thuộc Customer và đang `CONFIRMED`.
- **Main flow:** Customer yêu cầu hủy; hệ thống cho phép nếu thời điểm yêu cầu nhỏ hơn hoặc bằng thời điểm bắt đầu trừ 2 giờ; chuyển trạng thái sang `CANCELLED` và giải phóng lịch.
- **Alternative/error:** Từ chối Booking của Customer khác, trạng thái khác `CONFIRMED`, thời điểm sau hạn hoặc Booking `COMPLETED`.
- **Postcondition:** Booking được giữ lại với trạng thái `CANCELLED` và không chiếm lịch Staff.
- **Acceptance criteria:**
  - **AC-07.1:** Với giờ bắt đầu 08:00, yêu cầu lúc 06:00 được chấp nhận.
  - **AC-07.2:** Với giờ bắt đầu 08:00, yêu cầu lúc 06:01 bị từ chối.
  - **AC-07.3:** Chỉ Customer sở hữu Booking `CONFIRMED` mới hủy được; Booking `COMPLETED` không hủy được.
  - **AC-07.4:** Sau khi hủy, dữ liệu Booking vẫn còn, trạng thái là `CANCELLED`, và khung giờ có thể được đặt lại nếu còn trong lịch rảnh.

### FR-08: Staff xác nhận Booking hoàn thành

- **Actor:** Staff được Customer chọn cho Booking.
- **Precondition:** Booking `CONFIRMED`; mọi Booking_Detail đều có ít nhất một Evidence hợp lệ theo FR-09.
- **Main flow:** Staff phụ trách xác nhận hoàn thành bất cứ lúc nào, kể cả trước giờ bắt đầu dự kiến; hệ thống chuyển Booking `CONFIRMED → COMPLETED` và tạo Payment `UNPAID`.
- **Alternative/error:** Từ chối Staff khác, Booking sai trạng thái hoặc Booking có bất kỳ detail nào thiếu Evidence.
- **Postcondition:** Booking ở trạng thái `COMPLETED`; không có `IN_PROGRESS`, tự động hoàn thành hoặc bước Customer duyệt.
- **Acceptance criteria:**
  - **AC-08.1:** Staff được giao có thể hoàn thành Booking `CONFIRMED` tại bất kỳ thời điểm nào khi mọi detail có Evidence hợp lệ.
  - **AC-08.2:** Staff không được giao hoặc người khác không thể hoàn thành Booking.
  - **AC-08.3:** Booking không ở `CONFIRMED` bị từ chối; thời gian bắt đầu/kết thúc dự kiến không chặn thao tác hoàn thành.
  - **AC-08.4:** Chỉ cần một Booking_Detail thiếu Evidence thì toàn Booking không thể chuyển sang `COMPLETED`.

### FR-09: Upload và liên kết Evidence

- **Actor:** Staff được giao cho Booking.
- **Precondition:** Staff đã đăng nhập và được giao Booking/Booking_Detail đích.
- **Main flow:** Staff upload ảnh bằng Spring Boot `MultipartFile`; hệ thống lưu file trên filesystem local và ghi đường dẫn vào database, gắn Evidence với đúng Booking_Detail và Staff upload.
- **Alternative/error:** Từ chối upload gắn với Booking_Detail ngoài Booking được giao hoặc do Staff không phụ trách gửi; từ chối file rỗng, hỏng, không thực sự là JPEG/PNG, vượt 5 MB hoặc vượt quá 5 Evidence hợp lệ trên cùng Booking_Detail.
- **Postcondition:** Evidence liên kết đúng detail và người gửi; có thể dùng để xét điều kiện hoàn thành.
- **Acceptance criteria:**
  - **AC-09.1:** Mỗi Booking_Detail có từ 1 đến tối đa 5 ảnh Evidence hợp lệ trước khi Booking hoàn thành.
  - **AC-09.2:** Evidence gắn sai Booking_Detail, upload bởi Staff không phụ trách Booking, file rỗng/hỏng hoặc nội dung file không hợp lệ bị từ chối.
  - **AC-09.3:** File được lưu trên filesystem local; đường dẫn tương ứng được lưu trong database.
  - **AC-09.4:** Luồng upload nhận `MultipartFile`, chỉ chấp nhận tệp `.jpg`, `.jpeg` hoặc `.png` có nội dung thực tương ứng JPEG/PNG (không tin MIME do client gửi), giới hạn mỗi ảnh ở 5 MB và không dùng cloud storage.

### FR-10: Tạo Payment và thanh toán

- **Actor:** Hệ thống; Customer.
- **Precondition:** Booking vừa chuyển sang `COMPLETED`.
- **Main flow:** Hệ thống tạo đúng một Payment trạng thái `UNPAID`. Customer chọn `CASH`/`BANK_TRANSFER` để Admin xác minh thủ công hoặc chọn `MOMO` để tạo checkout MoMo Test. Payment chỉ chuyển `PAID` sau khi nhận IPN MoMo hợp lệ và đối chiếu khớp dữ liệu Payment.
- **Alternative/error:** Không cho tạo Payment thứ hai cho cùng Booking; không chấp nhận phương thức ngoài danh sách. Giao dịch MoMo thất bại giữ Payment `UNPAID`; callback lặp không được cập nhật hai lần. Return URL không phải bằng chứng thanh toán.
- **Postcondition:** Một Payment gắn Booking; thanh toán thủ công giữ `UNPAID` đến khi Admin xác minh, còn MoMo chỉ chuyển `PAID` bởi IPN hợp lệ.
- **Acceptance criteria:**
  - **AC-10.1:** Khi Booking chuyển sang `COMPLETED`, hệ thống tạo đúng một Payment ở trạng thái `UNPAID`.
  - **AC-10.2:** Tạo Payment thứ hai cho cùng Booking bị từ chối; mỗi Booking có tối đa một Payment.
  - **AC-10.3:** Customer chỉ chọn phương thức sau `COMPLETED` và chọn đúng một trong `CASH`, `BANK_TRANSFER` hoặc `MOMO`.
  - **AC-10.4:** Trước khi Customer chọn phương thức, Payment vẫn là `UNPAID`; không tạo trạng thái Payment mới để biểu diễn phương thức chưa chọn.

### FR-11: Xác nhận Payment

- **Actor:** Admin với tiền mặt/chuyển khoản thủ công; MoMo với giao dịch trực tuyến.
- **Precondition:** Payment `UNPAID`; Admin xác minh đã nhận tiền thủ công, hoặc hệ thống nhận IPN MoMo.
- **Main flow:** Admin xác nhận tiền mặt/chuyển khoản đã nhận. Với MoMo, hệ thống kiểm tra chữ ký HMAC-SHA256, partner/order/request ID và số tiền; chỉ `resultCode=0` khớp dữ liệu mới chuyển Payment `UNPAID → PAID`.
- **Alternative/error:** Từ chối xác nhận thủ công cho Payment `MOMO`; từ chối IPN thiếu/sai chữ ký, sai partner, mã đơn, request ID hoặc số tiền. IPN thất bại giữ `UNPAID`; callback hợp lệ gửi lặp xử lý idempotent. Không xử lý hoàn tiền.
- **Postcondition:** Payment chỉ ở `PAID` sau xác minh Admin đối với phương thức thủ công hoặc IPN xác thực đối với MoMo.
- **Acceptance criteria:**
  - **AC-11.1:** Admin chuyển được Payment `CASH`/`BANK_TRANSFER` sang `PAID` khi đã xác nhận nhận tiền.
  - **AC-11.2:** Không cho Admin chuyển Payment `MOMO` sang `PAID`; phương thức thủ công cũng cần được xác minh.
  - **AC-11.3:** MoMo chỉ cập nhật `PAID` khi IPN có chữ ký hợp lệ và partner/order/request ID cùng số tiền khớp; redirect trình duyệt không cập nhật trạng thái.
  - **AC-11.4:** IPN gửi lặp không tạo cập nhật lặp; giao dịch thất bại không chuyển sang `PAID`.

### FR-12: Customer gửi Feedback

- **Actor:** Customer sở hữu Booking.
- **Precondition:** Booking thuộc Customer và ở trạng thái `COMPLETED`.
- **Main flow:** Customer gửi một rating từ 1 đến 5 và nội dung nhận xét.
- **Alternative/error:** Từ chối Booking chưa hoàn thành, không thuộc Customer, rating ngoài 1–5 hoặc Feedback thứ hai cho cùng Booking.
- **Postcondition:** Booking có tối đa một Feedback. Không có chức năng sửa, xóa hoặc kiểm duyệt Feedback.
- **Acceptance criteria:**
  - **AC-12.1:** Customer sở hữu Booking `COMPLETED` gửi được rating 1–5 kèm nhận xét.
  - **AC-12.2:** Booking chưa `COMPLETED`, không thuộc Customer hoặc rating ngoài 1–5 bị từ chối.
  - **AC-12.3:** Feedback thứ hai cho cùng Booking bị từ chối.
  - **AC-12.4:** SRS không thêm luồng sửa, xóa hoặc Admin kiểm duyệt Feedback.

### FR-13: Quản lý Favorite_Staff

- **Actor:** Customer đã đăng nhập.
- **Precondition:** Customer và Staff đích tồn tại.
- **Main flow:** Customer thêm hoặc bỏ Staff khỏi danh sách yêu thích.
- **Alternative/error:** Không yêu cầu Booking trước; thao tác thêm trùng không tạo quan hệ trùng lặp.
- **Postcondition:** Mỗi cặp Customer–Staff có tối đa một quan hệ yêu thích.
- **Acceptance criteria:**
  - **AC-13.1:** Customer thêm được bất kỳ Staff nào vào yêu thích dù chưa từng đặt Booking với Staff đó.
  - **AC-13.2:** Thêm lặp lại cùng cặp Customer–Staff không tạo bản ghi/quan hệ trùng.
  - **AC-13.3:** Customer bỏ được Staff khỏi danh sách yêu thích.

### FR-14: Xem Services, Staff và thời gian có thể đặt

- **Actor:** Customer.
- **Precondition:** Customer truy cập chức năng xem danh mục.
- **Main flow:** Customer xem danh sách Services, Staff và khoảng thời gian có thể đặt.
- **Alternative/error:** Khi không có dữ liệu phù hợp, hệ thống không hiển thị lựa chọn không khả dụng.
- **Postcondition:** Customer có thông tin để chọn Services, Staff và khoảng thời gian cho FR-03.
- **Acceptance criteria:**
  - **AC-14.1:** Customer có thể xem Services và danh sách Staff.
  - **AC-14.2:** Customer có thể xem khoảng thời gian có thể đặt cho Staff.

### FR-15: Customer xem Booking và Payment của mình

- **Actor:** Customer.
- **Precondition:** Customer đã đăng nhập.
- **Main flow:** Customer xem Booking thuộc mình và trạng thái Payment tương ứng nếu có.
- **Alternative/error:** Không trả về Booking/Payment thuộc Customer khác.
- **Postcondition:** Customer chỉ nhận thông tin Booking và Payment của mình.
- **Acceptance criteria:**
  - **AC-15.1:** Customer xem được danh sách/thông tin Booking của mình.
  - **AC-15.2:** Customer xem được trạng thái Payment của Booking mình; không xem được Payment của Customer khác.

### FR-16: Staff xem công việc được giao

- **Actor:** Staff.
- **Precondition:** Staff đã đăng nhập.
- **Main flow:** Staff xem Booking/công việc được Customer chọn và gắn với chính Staff đó.
- **Alternative/error:** Không hiển thị công việc được giao cho Staff khác.
- **Postcondition:** Staff có thể mở đúng Booking được giao để thực hiện FR-09 và FR-10.
- **Acceptance criteria:**
  - **AC-16.1:** Staff xem được các công việc được chọn/đặt cho mình.
  - **AC-16.2:** Staff không xem được danh sách công việc riêng của Staff khác.

### FR-17: Admin quản lý Staff và Services

- **Actor:** Admin.
- **Precondition:** Admin đã đăng nhập.
- **Main flow:** Admin tạo, xem, cập nhật Staff và Services theo cấu trúc schema được duyệt; chỉ hard-delete bản ghi không có dữ liệu phụ thuộc.
- **Alternative/error:** Từ chối người không phải Admin; từ chối hard delete nếu có bất kỳ dữ liệu phụ thuộc nào; không cascade xóa lịch sử, không soft-delete và không mở rộng schema/CRUD vượt mô hình được duyệt.
- **Postcondition:** Danh sách Staff/Services được duy trì theo dữ liệu dự án.
- **Acceptance criteria:**
  - **AC-17.1:** Admin tạo, xem và cập nhật Staff/Services theo schema được duyệt; chỉ hard-delete khi không có dữ liệu phụ thuộc. Nếu có bất kỳ dữ liệu phụ thuộc nào thì xóa bị từ chối, dữ liệu lịch sử được giữ và không cascade; không dùng soft-delete hoặc trạng thái xóa mới.
  - **AC-17.2:** Customer và Staff không thực hiện được thao tác quản trị CRUD này.

### FR-18: Admin xem Booking, trạng thái công việc và Payment

- **Actor:** Admin.
- **Precondition:** Admin đã đăng nhập.
- **Main flow:** Admin xem Booking, trạng thái công việc và thông tin Payment để quản lý vận hành và xác minh tiền theo FR-11.
- **Alternative/error:** Từ chối người không phải Admin; xem thông tin không tự thay đổi trạng thái Payment.
- **Postcondition:** Admin có thể kiểm tra thông tin Booking/Payment mà không làm phát sinh xác nhận thanh toán tự động.
- **Acceptance criteria:**
  - **AC-18.1:** Admin xem được Booking, trạng thái công việc và thông tin Payment.
  - **AC-18.2:** Việc xem dữ liệu không tự chuyển Payment sang `PAID`.

## 4. Giao Diện Bên Ngoài

- **Xác thực:** session-based authentication; không JWT.
- **Đăng nhập:** một trang `/login` chung cho Customer, Staff và Admin; xác thực server-side bằng Spring Security và HTTP Session.
- **Giao diện:** Spring Boot MVC + Thymeleaf + Bootstrap, render HTML phía server.
- **Upload:** nhận JPEG/PNG qua Spring Boot `MultipartFile`; kiểm tra nội dung thực, tối đa 5 MB/ảnh và 1–5 ảnh hợp lệ/Booking_Detail; lưu trên filesystem local và lưu đường dẫn trong database.
- **Thanh toán:** MoMo checkout dùng môi trường Test; trạng thái thành công lấy từ IPN đã xác minh, không tin redirect. `CASH`/`BANK_TRANSFER` vẫn được Admin xác minh thủ công.
- Các quyết định công nghệ: Java 21, Spring Boot 4.1.1, Maven, Microsoft SQL Server, Spring Data JPA/Hibernate, Flyway và JDBC Driver cho SQL Server. Spring Boot 4.1.1 đã được giảng viên chốt; dependency đã resolve/biên dịch, còn hành vi runtime cần kiểm chứng trên SQL Server/Testcontainers.
- `/login` là route đã chốt; các route nghiệp vụ khác và giao thức chi tiết chưa được chốt trong baseline.

## 5. Yêu Cầu Dữ Liệu

### 5.1 Thực thể trong phạm vi

Giữ nguyên 11 thực thể nghiệp vụ baseline: Customer, Staff, Admin, Services, Booking, Booking_Detail, Work_Schedule, Payment, Feedback, Favorite_Staff và Evidences. Tích hợp gateway bổ sung bảng hỗ trợ kỹ thuật `Payment_Attempt` để lưu mã yêu cầu/đơn MoMo, checkout và trạng thái từng lần thử; bảng này không thay thế Payment hay Booking. Bảng sau ghi quan hệ nghiệp vụ cần được thể hiện; không khẳng định vị trí hoặc tên cột vật lý.

| Thực thể/quan hệ | Quy tắc dữ liệu nghiệp vụ |
|---|---|
| Customer–Booking | Booking thuộc Customer tạo Booking. |
| Booking–Booking_Detail–Staff | Booking có thể có nhiều detail; tất cả detail trong Booking thuộc cùng Staff Customer chọn. |
| Staff–Work_Schedule–Admin | Work_Schedule biểu diễn các khoảng sẵn sàng linh hoạt của Staff do Admin quản lý. |
| Booking–Work_Schedule | Booking phải nằm trong khoảng sẵn sàng và không giao với lịch cùng Staff đang bị Booking khác chiếm. |
| Booking_Detail–Services | Mỗi detail gắn với dịch vụ tương ứng. |
| Booking_Detail–Evidences–Staff | Mỗi detail cần từ 1 đến tối đa 5 Evidence ảnh hợp lệ (JPEG/PNG, tối đa 5 MB/ảnh); Evidence gắn đúng detail và do Staff phụ trách upload. |
| Booking–Payment | Tối đa một Payment cho mỗi Booking; được tạo `UNPAID` khi Booking `COMPLETED`. |
| Payment–Payment_Attempt | Một Payment có thể có nhiều lần thử MoMo; mỗi mã order/request của MoMo là duy nhất. Lần thử không tự tạo Payment thứ hai. |
| Customer–Booking–Feedback | Tối đa một Feedback/Booking; chỉ Customer sở hữu Booking `COMPLETED` gửi được. |
| Customer–Favorite_Staff–Staff | Không có quan hệ trùng cho cùng cặp Customer–Staff. |

### 5.2 Trạng thái, quyền sở hữu và giới hạn

- Booking chỉ dùng `CONFIRMED`, `CANCELLED`, `COMPLETED`; transition được quy định tại FR-06…FR-08.
- Payment chỉ dùng `UNPAID`, `PAID`; transition được quy định tại FR-10 và FR-11.
- Các giới hạn tối đa một Payment/Booking, một Feedback/Booking và duy nhất cặp Favorite_Staff là yêu cầu nghiệp vụ đã chốt. Constraint database tương ứng chưa được xác minh.
- Payment giữ hai trạng thái `UNPAID`/`PAID`; trạng thái mỗi lần thử MoMo được lưu độc lập, và IPN lặp phải được xử lý idempotent.
- Thông tin đăng nhập/session có thể ánh xạ vào ba thực thể hiện có hay không là `UNVERIFIED`; không giả định thêm bảng/cột.

## 6. Yêu Cầu Chất Lượng

| ID | Yêu cầu | Cách xác minh |
|---|---|---|
| NFR-01 | Backend phải kiểm tra role và ownership cho thao tác nghiệp vụ. | AC-02.1…AC-02.3. |
| NFR-02 | Kiểm tra đặt lịch phải ngăn hai request đồng thời cùng chiếm slot Staff. | AC-05.3; chỉ quy định kết quả, không chọn cơ chế transaction/locking. |
| NFR-03 | Evidence phải là JPEG/PNG được kiểm tra nội dung thực, tối đa 5 MB/ảnh, từ 1 đến tối đa 5 ảnh hợp lệ/Booking_Detail và lưu local; không gửi lên cloud. | AC-09.1, AC-09.3 và AC-09.4. |
| NFR-04 | IPN MoMo phải xác minh HMAC-SHA256 và đối chiếu partner, mã order/request, số tiền; không ghi log secret hoặc dữ liệu xác thực. | AC-11.3, kiểm thử chữ ký đúng/sai và callback lặp. |

## 7. Ràng Buộc Và Phụ Thuộc

- 11 thực thể nghiệp vụ baseline được giữ nguyên; `Payment_Attempt` là bảng hỗ trợ integration MoMo.
- Không dùng JWT, gateway ngoài MoMo, cloud storage, AI, hoàn tiền hoặc các tính năng ngoài phạm vi trong PRD.
- Flyway migration runtime, kết nối môi trường, thông tin xác thực trong schema, thuộc tính/PK/FK/cardinality/nullability và constraint vật lý trên SQL Server chưa được xác minh. Dependency resolution/biên dịch đã kiểm tra; Spring Boot 4.1.1 và các lựa chọn nền tảng khác đã phê duyệt tại mục 2.3 không phải là unknown nghiệp vụ.
- Các unknown trên là unknown về thiết kế/bằng chứng; không làm thay đổi trạng thái đã phê duyệt của BR/FR.

## 8. Truy Vết BR → FR → AC

Mỗi AC có mã duy nhất theo requirement cha. `AC-BC-01.1` là tiêu chí kiểm chứng tài liệu cho business constraint, không phải test hành vi runtime.

| BR | Loại | Requirement | AC và nội dung kiểm chứng |
|---|---|---|---|
| BR-01 | Business constraint | BC-01 | **AC-BC-01.1:** PRD/SRS ghi rõ một công ty, Staff part-time, không marketplace/freelancer và không suy ra phải thêm Company entity. |
| BR-02 | Functional | FR-03 | **AC-03.1:** Mọi detail trong một Booking thuộc Staff Customer chọn. **AC-03.2:** Không có luồng Admin phân công thay Customer. |
| BR-03 | Functional | FR-04 | **AC-04.1:** Admin nhập/cập nhật khoảng sẵn sàng. **AC-04.2:** Không bắt buộc ca cố định/lịch lặp. |
| BR-04 | Functional | FR-05 | **AC-05.1:** Ngoài lịch rảnh bị từ chối. **AC-05.2:** Giao lịch cùng Staff đang chiếm bị từ chối. **AC-05.3:** Hai request cùng slot đồng thời có tối đa một request thành công. **AC-05.4:** Các detail nối tiếp, không chồng lấn. **AC-05.5:** `CANCELLED` không chiếm lịch. |
| BR-05 | Functional | FR-06 | **AC-06.1:** Booking hợp lệ tự thành `CONFIRMED` không cần Admin duyệt. **AC-06.2:** Booking ngoài điều kiện lịch không được xác nhận. |
| BR-06 | Functional | FR-07 | **AC-07.1:** Hủy đúng mốc 2 giờ được phép. **AC-07.2:** Hủy sau mốc bị từ chối. |
| BR-07 | Functional | FR-07 | **AC-07.3:** Chỉ `CONFIRMED → CANCELLED`, không hủy `COMPLETED`. **AC-07.4:** Dữ liệu được giữ, lịch được giải phóng. |
| BR-08 | Functional | FR-08 | **AC-08.1:** Staff đúng người hoàn thành Booking `CONFIRMED` ở bất kỳ thời điểm nào khi mọi detail đủ Evidence. **AC-08.2:** Staff khác bị từ chối. **AC-08.3:** Booking sai trạng thái bị từ chối. **AC-08.4:** Một detail thiếu ảnh làm cả Booking không hoàn thành. |
| BR-09 | Functional | FR-09 | **AC-09.1:** Mỗi detail có từ 1–5 ảnh hợp lệ trước hoàn thành, vượt giới hạn bị từ chối. **AC-09.2:** Sai detail/Staff, file rỗng/hỏng hoặc nội dung không hợp lệ bị từ chối. |
| BR-10 | Functional | FR-09 | **AC-09.3:** File lưu local, đường dẫn lưu DB. **AC-09.4:** Dùng `MultipartFile`, chỉ phần mở rộng `.jpg`/`.jpeg`/`.png` và nội dung thực JPEG/PNG, tối đa 5 MB/ảnh, không tin MIME client, không cloud. |
| BR-11 | Functional | FR-10 | **AC-10.3:** Chỉ chọn `CASH`, `BANK_TRANSFER` hoặc `MOMO` sau `COMPLETED`. |
| BR-12 | Functional | FR-10, FR-11 | **AC-10.1:** Tạo đúng một Payment `UNPAID` khi Booking hoàn thành. **AC-10.2:** Payment thứ hai cùng Booking bị từ chối. **AC-10.4:** Phương thức chưa chọn không tạo trạng thái Payment mới. **AC-11.1:** Admin xác nhận tiền mặt/chuyển khoản đã nhận. **AC-11.2:** Admin không tự xác nhận MoMo. **AC-11.3:** Chỉ IPN đúng chữ ký, partner/order/request ID, số tiền mới chuyển MoMo sang `PAID`; redirect không có quyền cập nhật. **AC-11.4:** IPN lặp an toàn; giao dịch thất bại giữ `UNPAID`. |
| BR-13 | Functional | FR-12 | **AC-12.1:** Customer sở hữu Booking `COMPLETED` gửi rating 1–5 và nhận xét. **AC-12.2:** Sai owner/trạng thái/rating bị từ chối. **AC-12.3:** Feedback thứ hai bị từ chối. **AC-12.4:** Không thêm sửa/xóa/kiểm duyệt. |
| BR-14 | Functional | FR-13 | **AC-13.1:** Có thể yêu thích Staff chưa từng phục vụ. **AC-13.2:** Không tạo cặp trùng. **AC-13.3:** Customer bỏ yêu thích được. |

Yêu cầu session/authorization ngoài danh sách BR được truy vết riêng:

| Requirement | AC |
|---|---|
| FR-01 | AC-01.1: login hợp lệ tạo session đúng role; AC-01.2: login lỗi không tạo session; AC-01.3: chưa đăng nhập không thực hiện thao tác được bảo vệ; AC-01.4…AC-01.6: chỉ Customer tự đăng ký, kiểm tra dữ liệu/email và đăng nhập sau khi đăng ký. |
| FR-02 | AC-02.1: Customer không truy cập Booking người khác; AC-02.2: Staff không thao tác Booking Staff khác; AC-02.3: thao tác Admin chỉ dành cho Admin. |

Các chức năng vai trò đã có trong Business Baseline V1.0 nhưng không mang mã BR riêng được truy vết riêng, không gán nhầm vào BR khác:

| Nội dung baseline theo vai trò | FR | AC |
|---|---|---|
| Customer xem Services, Staff và thời gian có thể đặt | FR-14 | AC-14.1, AC-14.2 |
| Customer xem Booking của mình và trạng thái Payment | FR-15 | AC-15.1, AC-15.2 |
| Staff xem công việc được giao cho mình | FR-16 | AC-16.1, AC-16.2 |
| Admin CRUD cơ bản Staff/Services theo schema được duyệt, hard delete có điều kiện | FR-17 | AC-17.1, AC-17.2 |
| Admin xem Booking, trạng thái công việc và thông tin Payment | FR-18 | AC-18.1, AC-18.2 |

## Phụ Lục A — ERD Verification (bằng chứng thiết kế)

Phụ lục này không thay đổi hay gắn `UNVERIFIED` lên các BR/FR đã phê duyệt. Bằng chứng ERD trong lần đồng bộ này không được mở hay truy cập từ `docs/diagrams/`. Kết quả xác minh bên dưới phân biệt thiết kế SQL trong source với hành vi đã chạy trên SQL Server: migration và `database/schema.sql` hiện cung cấp bằng chứng về DDL; môi trường hiện tại chưa có Docker/SQL Server khả dụng để chạy các kiểm tra tích hợp. `VERIFIED` chỉ áp dụng cho nội dung được xác nhận trực tiếp trong source hoặc runtime tương ứng.

| Hạng mục | Kết quả xác minh thiết kế | Diễn giải |
|---|---|---|
| Danh sách 11 thực thể baseline và phần mở rộng MoMo | `VERIFIED` trong baseline và DDL source | Migration V1 giữ 11 thực thể nghiệp vụ; V2 bổ sung bảng kỹ thuật `Payment_Attempt` cho MoMo; snapshot phản ánh schema hiện tại. Chưa xác nhận migration trên SQL Server runtime. |
| Quan hệ, PK/FK, nullability, kiểu và constraint | `VERIFIED` trong DDL source; runtime `UNVERIFIED` | Có thể đối chiếu trong V1, V2 và snapshot. Tính hợp lệ thực tế trên SQL Server và Hibernate validation chưa chạy do thiếu Docker/SQL Server. |
| Booking–Booking_Detail–Staff–Work_Schedule | `VERIFIED` về cột FK trong DDL; invariant `UNVERIFIED` runtime | DDL nối Detail với Booking và Work_Schedule. Service kiểm tra cùng Staff, thời lượng tuần tự, không overlap/khoảng trống và nằm trong lịch; kiểm thử concurrency chưa chạy trên SQL Server. |
| Booking–Payment và MoMo attempts | `VERIFIED` trong DDL source; runtime `UNVERIFIED` | V1 giữ FK/UNIQUE một Payment mỗi Booking và trạng thái `UNPAID`/`PAID`; V2 cho phép phương thức `MOMO` và thêm Payment_Attempt với mã order/request duy nhất, trạng thái lần thử và FK tới Payment. Flyway/runtime chưa được chạy. |
| Booking–Feedback | `VERIFIED` trong DDL source; runtime `UNVERIFIED` | DDL có FK và UNIQUE giới hạn một Feedback mỗi Booking; chưa được kiểm chứng bằng SQL Server runtime. |
| Booking_Detail–Evidence–Staff | `VERIFIED` về FK source; ownership/cardinality `UNVERIFIED` runtime | DDL thể hiện các FK đã duyệt. Service kiểm tra Staff được phân công và giới hạn 1–5 Evidence hợp lệ trước `COMPLETED`; kiểm thử đồng thời chưa chạy. |
| Định danh email ba role | `VERIFIED` về UNIQUE riêng và collation trong DDL; uniqueness toàn cục `UNVERIFIED` runtime | DDL có UNIQUE riêng và collation không phân biệt hoa/thường. Service canonical hóa trim/lowercase và transaction SERIALIZABLE khóa các dải index theo thứ tự cố định; test cạnh tranh ba role đã viết nhưng chưa chạy được. |
| Hard delete Staff/Services và dữ liệu phụ thuộc | `VERIFIED` trong DDL source; runtime `UNVERIFIED` | Các FK khai báo `ON DELETE NO ACTION`; kết quả thực thi và Hibernate validate cần chạy trên SQL Server. |
| DATETIMEOFFSET và migration Flyway | Mapping/source `VERIFIED`; SQL Server runtime `UNVERIFIED` | Migration dùng `DATETIMEOFFSET(0)` và JPA map `OffsetDateTime`; bài kiểm tra round-trip `+07:00` cùng Flyway tạo schema rỗng đã có nhưng không chạy do Docker không khả dụng. |
| Evidence file storage và reconciliation | `VERIFIED` trong source; runtime `UNVERIFIED` | Service lưu metadata, giới hạn upload, bù trừ lỗi ghi file và reconciler xử lý file mồ côi; chưa chạy kiểm thử tích hợp trong môi trường này. |

MoMo là phần mở rộng được phê duyệt cho luồng thanh toán; V1 không bị sửa và V2 ghi nhận phần mở rộng. Đây chưa phải nghiệm thu database: Flyway tạo schema rỗng, Hibernate validate, DATETIMEOFFSET round-trip và các test concurrency trên SQL Server/Testcontainers đều còn `UNVERIFIED` vì Docker/SQL Server không khả dụng.

## 9. Nghiệm Thu Và Lịch Sử Thay Đổi

Tài liệu đạt nghiệm thu khi mọi BR có đường truy vết phù hợp; mỗi AC có mã duy nhất và nội dung kiểm chứng cụ thể; các trường hợp biên trong baseline được bao phủ; yêu cầu nghiệp vụ được tách khỏi phụ lục xác minh ERD; không có chức năng, entity, cột, quan hệ hoặc trạng thái tự bổ sung; mọi bằng chứng thiết kế chưa xác minh được gắn nhãn riêng.

Lịch sử thay đổi tài liệu được theo dõi bằng Git. Việc cập nhật SRS/PRD không tự yêu cầu thay đổi ERD, schema, migration hoặc code.
