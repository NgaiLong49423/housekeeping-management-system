> **Document:** Software Requirements Specification\
> **File:** `docs/requirements/SRS.md`\
> **Version:** v1.0.0\
> **Created:** 2026-06-14\
> **Last Updated:** 2026-10-10\
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
| Admin | Quản lý Work_Schedule và xác minh nhận Payment. |
| Booking_Detail | Chi tiết dịch vụ thuộc một Booking. |
| Evidence | Ảnh bằng chứng gắn với đúng Booking_Detail và do Staff phụ trách upload. |
| `CONFIRMED` | Booking hợp lệ được xác nhận tự động. |
| `CANCELLED` | Booking đã hủy theo điều kiện; dữ liệu được giữ và không tiếp tục chiếm lịch. |
| `COMPLETED` | Booking hoàn tất sau khi Staff phụ trách đáp ứng các điều kiện hoàn thành. |
| `UNPAID` / `PAID` | Payment chưa được Admin xác minh đã nhận tiền / đã được xác minh đã nhận tiền. |
| `CASH` / `BANK_TRANSFER` | Hai phương thức Customer được chọn; chuyển khoản được xác minh thủ công. |

BR-01 về một công ty và Staff part-time là **business context/constraint**, không phải functional requirement. Xem mục 2.3.

## 2. Bối Cảnh Sản Phẩm

### 2.1 Vai trò và trách nhiệm

| Actor | Trách nhiệm |
|---|---|
| Customer | Xem Services, Staff và khoảng thời gian có thể đặt; chọn Staff, tạo/xem Booking của mình, hủy theo điều kiện, xem trạng thái Payment, chọn phương thức sau khi hoàn thành, gửi Feedback và quản lý Favorite_Staff. |
| Staff | Xem Booking được chọn cho mình, upload Evidence cho đúng Booking_Detail và xác nhận hoàn thành khi đủ điều kiện. |
| Admin | CRUD cơ bản Staff/Services theo schema được duyệt; nhập/cập nhật thời gian sẵn sàng; xem Booking/trạng thái công việc/thông tin Payment; xác minh nhận tiền và cập nhật Payment. |

### 2.2 Xác thực và phân quyền

Sử dụng session-based authentication phù hợp với ứng dụng Spring Boot MVC/N-layer. Ba role là Customer, Staff và Admin. Backend phải xác minh role, quyền sở hữu Booking và Staff được giao cho mọi thao tác tương ứng. Không sử dụng JWT và không mặc định thêm bảng Users, Accounts hoặc Roles.

### 2.3 Business Context/Constraints

| Mã | Constraint đã phê duyệt |
|---|---|
| BC-01 (BR-01) | Hệ thống phục vụ một công ty; Staff là nhân viên part-time, không phải freelancer độc lập. Đây là ranh giới nghiệp vụ, không hàm ý phải thêm entity Company. |

## 3. Functional Requirements

Mỗi requirement dưới đây có acceptance criteria mang mã duy nhất. Trạng thái yêu cầu trong mục này là đã phê duyệt theo baseline; độ xác minh ERD được tách riêng tại Phụ lục A.

### FR-01: Session-based authentication

- **Actor:** Customer, Staff, Admin.
- **Precondition:** Người dùng có thông tin đăng nhập hợp lệ thuộc một trong ba role; cấu trúc lưu thông tin đăng nhập hiện có cần đối chiếu ERD.
- **Main flow:** Người dùng đăng nhập; hệ thống xác thực và duy trì session gắn với danh tính, role cho các request tiếp theo.
- **Alternative/error:** Thông tin không hợp lệ không tạo session; request được bảo vệ từ người chưa đăng nhập không thực hiện thao tác.
- **Postcondition:** Session nhận diện người dùng và role hiện hành; không dùng JWT.
- **Acceptance criteria:**
  - **AC-01.1:** Thông tin đăng nhập hợp lệ tạo session gắn với đúng người dùng và một trong ba role Customer, Staff hoặc Admin.
  - **AC-01.2:** Đăng nhập thất bại không tạo session đã xác thực.
  - **AC-01.3:** Request chưa đăng nhập không thực hiện được thao tác yêu cầu xác thực.

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
- **Precondition:** Booking `CONFIRMED`; thời điểm hiện tại không trước giờ bắt đầu; mọi Booking_Detail đều có ít nhất một Evidence hợp lệ theo FR-09.
- **Main flow:** Staff phụ trách xác nhận hoàn thành; hệ thống chuyển Booking `CONFIRMED → COMPLETED`.
- **Alternative/error:** Từ chối Staff khác, Booking sai trạng thái, request trước giờ bắt đầu hoặc Booking có bất kỳ detail nào thiếu Evidence.
- **Postcondition:** Booking ở trạng thái `COMPLETED`; không có `IN_PROGRESS`, tự động hoàn thành hoặc bước Customer duyệt.
- **Acceptance criteria:**
  - **AC-08.1:** Staff được giao hoàn thành Booking `CONFIRMED` tại hoặc sau giờ bắt đầu khi mọi detail có Evidence hợp lệ.
  - **AC-08.2:** Staff không được giao hoặc người khác không thể hoàn thành Booking.
  - **AC-08.3:** Yêu cầu trước giờ bắt đầu hoặc khi Booking không ở `CONFIRMED` bị từ chối.
  - **AC-08.4:** Chỉ cần một Booking_Detail thiếu Evidence thì toàn Booking không thể chuyển sang `COMPLETED`.

### FR-09: Upload và liên kết Evidence

- **Actor:** Staff được giao cho Booking.
- **Precondition:** Staff đã đăng nhập và được giao Booking/Booking_Detail đích.
- **Main flow:** Staff upload ảnh bằng Spring Boot `MultipartFile`; hệ thống lưu file trên filesystem local và ghi đường dẫn vào database, gắn Evidence với đúng Booking_Detail và Staff upload.
- **Alternative/error:** Từ chối upload gắn với Booking_Detail ngoài Booking được giao hoặc do Staff không phụ trách gửi. Quy tắc MIME type/kích thước cụ thể chưa được baseline xác định và không tự đặt trong SRS.
- **Postcondition:** Evidence liên kết đúng detail và người gửi; có thể dùng để xét điều kiện hoàn thành.
- **Acceptance criteria:**
  - **AC-09.1:** Mỗi Booking_Detail có ít nhất một ảnh Evidence hợp lệ trước khi Booking hoàn thành.
  - **AC-09.2:** Evidence gắn sai Booking_Detail hoặc upload bởi Staff không phụ trách Booking bị từ chối.
  - **AC-09.3:** File được lưu trên filesystem local; đường dẫn tương ứng được lưu trong database.
  - **AC-09.4:** Luồng upload nhận file qua `MultipartFile`; không dùng cloud storage.

### FR-10: Tạo Payment và chọn phương thức

- **Actor:** Hệ thống; Customer chọn phương thức.
- **Precondition:** Booking vừa chuyển sang `COMPLETED`.
- **Main flow:** Hệ thống tạo đúng một Payment trạng thái `UNPAID`. Sau đó Customer chọn đúng một phương thức `CASH` hoặc `BANK_TRANSFER`. Việc chọn phương thức không đồng nghĩa thanh toán đã xảy ra; chỉ thanh toán sau khi Booking `COMPLETED`.
- **Alternative/error:** Không cho tạo Payment thứ hai cho cùng Booking; không chấp nhận phương thức ngoài danh sách. Không thêm trạng thái Payment cho giai đoạn Customer chưa chọn phương thức.
- **Postcondition:** Một Payment `UNPAID` gắn Booking; khi Customer chọn, phương thức là một trong hai giá trị hợp lệ.
- **Acceptance criteria:**
  - **AC-10.1:** Khi Booking chuyển sang `COMPLETED`, hệ thống tạo đúng một Payment ở trạng thái `UNPAID`.
  - **AC-10.2:** Tạo Payment thứ hai cho cùng Booking bị từ chối; mỗi Booking có tối đa một Payment.
  - **AC-10.3:** Customer chỉ chọn phương thức sau `COMPLETED` và chọn đúng một trong `CASH` hoặc `BANK_TRANSFER`.
  - **AC-10.4:** Trước khi Customer chọn phương thức, Payment vẫn là `UNPAID`; không tạo trạng thái Payment mới để biểu diễn phương thức chưa chọn.

### FR-11: Admin xác minh Payment

- **Actor:** Admin.
- **Precondition:** Admin đã đăng nhập; Payment `UNPAID`; Customer đã chọn phương thức hợp lệ; Admin đã xác minh thực tế đã nhận tiền.
- **Main flow:** Admin xác nhận nhận tiền; hệ thống chuyển Payment `UNPAID → PAID`.
- **Alternative/error:** Từ chối nếu phương thức chưa chọn/không hợp lệ hoặc Admin chưa xác minh đã nhận tiền. Chuyển khoản được xác minh thủ công; không tích hợp cổng thanh toán. Không xử lý hoàn tiền.
- **Postcondition:** Payment chỉ ở `PAID` sau xác minh của Admin.
- **Acceptance criteria:**
  - **AC-11.1:** Admin chuyển được Payment sang `PAID` khi phương thức là `CASH` hoặc `BANK_TRANSFER` và Admin xác nhận đã nhận tiền.
  - **AC-11.2:** Không cho chuyển `PAID` nếu chưa có phương thức hợp lệ hoặc chưa xác minh nhận tiền.
  - **AC-11.3:** Chỉ Admin thực hiện thao tác xác minh; không có gateway/webhook tự đánh dấu `PAID`.

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
- **Main flow:** Admin thực hiện CRUD cơ bản Staff và Services theo cấu trúc schema được duyệt.
- **Alternative/error:** Từ chối người không phải Admin; không mở rộng schema hoặc nghiệp vụ CRUD vượt mô hình được duyệt.
- **Postcondition:** Danh sách Staff/Services được duy trì theo dữ liệu dự án.
- **Acceptance criteria:**
  - **AC-17.1:** Admin được phép thực hiện CRUD cơ bản Staff và Services trong phạm vi schema được duyệt.
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
- **Upload:** nhận ảnh Evidence qua Spring Boot `MultipartFile`; lưu trên filesystem local và lưu đường dẫn trong database.
- **Thanh toán:** thao tác thủ công; `BANK_TRANSFER` không tích hợp gateway, webhook hay tự động đối soát.
- Chi tiết UI, route, giao thức HTTP, MIME allowlist và giới hạn dung lượng chưa được baseline chốt; không được coi là yêu cầu cụ thể cho tới khi được xác nhận.

## 5. Yêu Cầu Dữ Liệu

### 5.1 Thực thể trong phạm vi

Giữ nguyên 11 thực thể baseline: Customer, Staff, Admin, Services, Booking, Booking_Detail, Work_Schedule, Payment, Feedback, Favorite_Staff và Evidences. Bảng sau ghi quan hệ nghiệp vụ cần được thể hiện; không khẳng định vị trí hoặc tên cột vật lý.

| Thực thể/quan hệ | Quy tắc dữ liệu nghiệp vụ |
|---|---|
| Customer–Booking | Booking thuộc Customer tạo Booking. |
| Booking–Booking_Detail–Staff | Booking có thể có nhiều detail; tất cả detail trong Booking thuộc cùng Staff Customer chọn. |
| Staff–Work_Schedule–Admin | Work_Schedule biểu diễn các khoảng sẵn sàng linh hoạt của Staff do Admin quản lý. |
| Booking–Work_Schedule | Booking phải nằm trong khoảng sẵn sàng và không giao với lịch cùng Staff đang bị Booking khác chiếm. |
| Booking_Detail–Services | Mỗi detail gắn với dịch vụ tương ứng. |
| Booking_Detail–Evidences–Staff | Mỗi detail cần ít nhất một Evidence hợp lệ; Evidence gắn đúng detail và do Staff phụ trách upload. |
| Booking–Payment | Tối đa một Payment cho mỗi Booking; được tạo `UNPAID` khi Booking `COMPLETED`. |
| Customer–Booking–Feedback | Tối đa một Feedback/Booking; chỉ Customer sở hữu Booking `COMPLETED` gửi được. |
| Customer–Favorite_Staff–Staff | Không có quan hệ trùng cho cùng cặp Customer–Staff. |

### 5.2 Trạng thái, quyền sở hữu và giới hạn

- Booking chỉ dùng `CONFIRMED`, `CANCELLED`, `COMPLETED`; transition được quy định tại FR-06…FR-08.
- Payment chỉ dùng `UNPAID`, `PAID`; transition được quy định tại FR-10 và FR-11.
- Các giới hạn tối đa một Payment/Booking, một Feedback/Booking và duy nhất cặp Favorite_Staff là yêu cầu nghiệp vụ đã chốt. Constraint database tương ứng chưa được xác minh.
- Việc Customer chọn phương thức sau khi Payment đã được tạo có thể cần cho phép phương thức chưa chọn trong bản ghi Payment. Khả năng biểu diễn đó trong schema hiện tại là `UNVERIFIED`; không thêm trạng thái Payment mới hoặc tự sửa schema.
- Thông tin đăng nhập/session có thể ánh xạ vào ba thực thể hiện có hay không là `UNVERIFIED`; không giả định thêm bảng/cột.

## 6. Yêu Cầu Chất Lượng

| ID | Yêu cầu | Cách xác minh |
|---|---|---|
| NFR-01 | Backend phải kiểm tra role và ownership cho thao tác nghiệp vụ. | AC-02.1…AC-02.3. |
| NFR-02 | Kiểm tra đặt lịch phải ngăn hai request đồng thời cùng chiếm slot Staff. | AC-05.3; chỉ quy định kết quả, không chọn cơ chế transaction/locking. |
| NFR-03 | Evidence lưu local; không gửi lên cloud. | AC-09.3 và AC-09.4. |

## 7. Ràng Buộc Và Phụ Thuộc

- Phạm vi chỉ dùng 11 thực thể đã liệt kê; không thêm/xóa entity trong yêu cầu.
- Không tự thêm bảng, cột, quan hệ, trạng thái hoặc quy tắc nghiệp vụ.
- Không dùng JWT, payment gateway, cloud storage, AI, hoàn tiền hoặc các tính năng ngoài phạm vi trong PRD.
- DBMS, phiên bản Java/Spring Boot, build tool, thông tin xác thực trong schema, thuộc tính/PK/FK và constraint vật lý chưa được xác minh.
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
| BR-08 | Functional | FR-08 | **AC-08.1:** Staff đúng người hoàn thành Booking đúng trạng thái, không trước giờ bắt đầu, mọi detail đủ Evidence. **AC-08.2:** Staff khác bị từ chối. **AC-08.3:** Sai thời điểm/trạng thái bị từ chối. **AC-08.4:** Một detail thiếu ảnh làm cả Booking không hoàn thành. |
| BR-09 | Functional | FR-09 | **AC-09.1:** Mỗi detail có ít nhất một ảnh hợp lệ trước hoàn thành. **AC-09.2:** Sai detail hoặc Staff upload bị từ chối. |
| BR-10 | Functional | FR-09 | **AC-09.3:** File lưu local, đường dẫn lưu DB. **AC-09.4:** Dùng `MultipartFile`, không cloud. |
| BR-11 | Functional | FR-10 | **AC-10.3:** Chỉ chọn `CASH` hoặc `BANK_TRANSFER` sau `COMPLETED`. |
| BR-12 | Functional | FR-10, FR-11 | **AC-10.1:** Tạo đúng một Payment `UNPAID` khi Booking hoàn thành. **AC-10.2:** Payment thứ hai cùng Booking bị từ chối. **AC-10.4:** Phương thức chưa chọn không tạo trạng thái Payment mới. **AC-11.1:** Chỉ Admin đã xác minh nhận tiền và có phương thức hợp lệ mới chuyển `PAID`. **AC-11.2:** Thiếu phương thức/xác minh thì không `PAID`. **AC-11.3:** Không gateway/webhook tự xác nhận. |
| BR-13 | Functional | FR-12 | **AC-12.1:** Customer sở hữu Booking `COMPLETED` gửi rating 1–5 và nhận xét. **AC-12.2:** Sai owner/trạng thái/rating bị từ chối. **AC-12.3:** Feedback thứ hai bị từ chối. **AC-12.4:** Không thêm sửa/xóa/kiểm duyệt. |
| BR-14 | Functional | FR-13 | **AC-13.1:** Có thể yêu thích Staff chưa từng phục vụ. **AC-13.2:** Không tạo cặp trùng. **AC-13.3:** Customer bỏ yêu thích được. |

Yêu cầu session/authorization ngoài danh sách BR được truy vết riêng:

| Requirement | AC |
|---|---|
| FR-01 | AC-01.1: login hợp lệ tạo session đúng role; AC-01.2: login lỗi không tạo session; AC-01.3: chưa đăng nhập không thực hiện thao tác được bảo vệ. |
| FR-02 | AC-02.1: Customer không truy cập Booking người khác; AC-02.2: Staff không thao tác Booking Staff khác; AC-02.3: thao tác Admin chỉ dành cho Admin. |

Các chức năng vai trò đã có trong Business Baseline V1.0 nhưng không mang mã BR riêng được truy vết riêng, không gán nhầm vào BR khác:

| Nội dung baseline theo vai trò | FR | AC |
|---|---|---|
| Customer xem Services, Staff và thời gian có thể đặt | FR-14 | AC-14.1, AC-14.2 |
| Customer xem Booking của mình và trạng thái Payment | FR-15 | AC-15.1, AC-15.2 |
| Staff xem công việc được giao cho mình | FR-16 | AC-16.1, AC-16.2 |
| Admin CRUD cơ bản Staff/Services theo schema được duyệt | FR-17 | AC-17.1, AC-17.2 |
| Admin xem Booking, trạng thái công việc và thông tin Payment | FR-18 | AC-18.1, AC-18.2 |

## Phụ Lục A — ERD Verification (bằng chứng thiết kế)

Phụ lục này không thay đổi hay gắn `UNVERIFIED` lên các BR/FR đã phê duyệt. Kết quả dựa trên báo cáo đối chiếu ERD đã cung cấp trước đó; không truy cập thư mục `docs/diagrams/`.

| Hạng mục | Kết quả xác minh thiết kế | Diễn giải |
|---|---|---|
| 11 tên thực thể | `MATCH` | Báo cáo ERD trước ghi nhận đủ Customer, Staff, Admin, Services, Booking, Booking_Detail, Work_Schedule, Payment, Feedback, Favorite_Staff và Evidences. |
| Thuộc tính, PK, FK | `UNVERIFIED` | Báo cáo trước không cung cấp bằng chứng đủ chi tiết để xác định thuộc tính hay khóa. Không suy luận chúng vắng mặt. |
| Booking–Booking_Detail–Staff–Work_Schedule | `UNVERIFIED` | Có quan hệ gián tiếp Booking_Detail–Work_Schedule–Staff; chưa xác minh rằng tất cả detail trong cùng Booking được bảo đảm thuộc một Staff được Customer chọn. |
| Khoảng Booking chung và thời lượng/tuần tự detail | `UNVERIFIED` | Baseline quy định hành vi; bằng chứng ERD trước chưa xác minh thuộc tính thời gian hoặc ràng buộc tuần tự. |
| Booking_Detail–Evidences–Staff | `MATCH` về liên kết; constraint `UNVERIFIED` | Quan hệ Evidence với detail và Staff được báo cáo; chưa xác minh khóa, ownership và điều kiện hợp lệ. |
| Tối đa một Payment/Booking | `UNVERIFIED` | BR-12 đã chốt; chưa xác minh constraint vật lý. |
| Tối đa một Feedback/Booking | `UNVERIFIED` về constraint | Báo cáo trước nói cardinality sơ đồ có vẻ gợi ý tối đa một; constraint database chưa xác minh. |
| Thông tin xác thực ba role | `UNVERIFIED` | Chưa xác minh trường đăng nhập/session trong các thực thể hiện có. Không thêm Users/Accounts/Roles. |
| Trạng thái Booking/Payment | `UNVERIFIED` về biểu diễn dữ liệu | Các trạng thái nghiệp vụ đã chốt; thuộc tính hoặc constraint vật lý chưa xác minh. |
| Khả năng để Payment chưa có method sau khi tạo | `UNVERIFIED` | Yêu cầu nghiệp vụ là tạo Payment `UNPAID` lúc hoàn thành rồi Customer chọn method sau. Chưa xác minh schema hiện tại biểu diễn được thời điểm chưa chọn; không thêm trạng thái Payment. |

Không có `CONFLICT` thiết kế nào được xác nhận từ bằng chứng báo cáo trước. Nếu một kiểm tra hợp lệ sau này phát hiện mâu thuẫn, ghi nhận riêng kèm tác động và yêu cầu quyết định; không tự sửa ERD/schema.

## 9. Nghiệm Thu Và Lịch Sử Thay Đổi

Tài liệu đạt nghiệm thu khi mọi BR có đường truy vết phù hợp; mỗi AC có mã duy nhất và nội dung kiểm chứng cụ thể; các trường hợp biên trong baseline được bao phủ; yêu cầu nghiệp vụ được tách khỏi phụ lục xác minh ERD; không có chức năng, entity, cột, quan hệ hoặc trạng thái tự bổ sung; mọi bằng chứng thiết kế chưa xác minh được gắn nhãn riêng.

Lịch sử thay đổi tài liệu được theo dõi bằng Git. Việc cập nhật SRS/PRD không tự yêu cầu thay đổi ERD, schema, migration hoặc code.
