> **Document:** Product Requirements Document\
> **File:** `docs/requirements/PRD.md`\
> **Version:** v3.2.0\
> **Created:** 2026-06-14\
> **Last Updated:** 2026-11-10\
> **Status:** Active\
# PRD — HOME CLEANING BOOKING (CẬP NHẬT THEO BASELINE MỚI)


## A. Vision
Hệ thống web cho một công ty dịch vụ dọn dẹp nhà: khách chọn nhà/nhân viên/dịch vụ và đặt lịch; nhân viên quản lý khả dụng, hoàn thành việc và xác nhận nhận tiền mặt; quản trị viên quản lý vận hành, cấu hình công ty, dịch vụ, nghỉ phép, đặt lịch, feedback. Dễ triển khai và demo trong đồ án Spring MVC.

## B. User groups and outcomes
| Actor | Mục tiêu | Quyền nổi bật |
|---|---|---|
| CUSTOMER | Đặt và theo dõi dịch vụ | Đăng nhập; Houses; chọn Service/Staff; tạo/hủy Booking; trả tiền; Feedback; Follow Staff |
| STAFF | Thực hiện công việc | Xem booking được giao; cập nhật profile; PART_TIME đăng ký availability; xin nghỉ; upload ảnh; xác nhận xong; xác nhận CASH |
| ADMIN | Vận hành một công ty dưới quyền Manager | Quản lý Services/Staff và trạng thái; Company Settings/Operating Hours; duyệt leave; xử lý hủy muộn, reassignment, feedback, sự cố và đề xuất tài chính |
| MANAGER | Quản trị hệ thống, duy nhất một tài khoản | Có toàn bộ quyền vận hành như Admin; tạo/khóa Admin; duyệt khoản tiền và quyết định nội bộ; không thể tạo Manager thứ hai |

## C. Tính năng trong phạm vi (14 module)
1. **Authentication:** Users dùng chung, role/status, session login, phân quyền và ownership.
2. **Service Management:** Danh mục Service, ảnh/giá/thời lượng, ACTIVE/INACTIVE.
3. **Staff Profile:** Staff profile avatar_url, bio; Staff self-edit.
4. **Follow Staff:** Customer follow/unfollow.
5. **House Management:** Nhiều địa chỉ nhập tay gồm người nhận, số điện thoại, tỉnh/thành, phường/xã, chi tiết, loại và địa chỉ mặc định; không cần Google Maps hoặc tọa độ; soft-delete có kiểm tra booking chưa xong.
6. **Booking Management:** Tạo Booking từ 1+ Service, 1 Staff, 1 House; 30-min starts, lead time 2h/30d horizon, lịch nghỉ, đệm 60min, snapshot địa chỉ văn bản, cancel, reassignment.
7. **Staff Availability:** FULL_TIME tự động theo công ty; PART_TIME tự đăng ký ngày-khoảng cụ thể, không đè booking.
8. **Leave Management:** Staff xin nghỉ nguyên ngày; Admin duyệt/từ chối, xử lý booking xung đột.
9. **Arrival and Incident Tracking:** Staff và Customer ghi nhận đã đến độc lập bất cứ lúc nào khi Booking còn CONFIRMED. Staff phải xác nhận đã đến trước khi bắt đầu; sau đó Staff tự chọn bắt đầu sớm hoặc đợi giờ hẹn, không đổi lịch, thời lượng hay giá. Sau giờ hẹn khi Staff chưa xác nhận đến, Customer có thể ghi nhận Staff chưa đến để Admin theo dõi; báo cáo này không tự chặn bắt đầu, hủy lịch hay đổi tiền. Báo trễ do Staff gửi là tùy chọn; nếu Staff gửi báo cáo, Customer cần đồng ý chờ trước khi Staff bắt đầu. Customer có thể từ chối báo cáo hoặc hủy miễn phí khi trễ trên 30 phút trước khi bắt đầu. Nếu tiếp tục, giảm theo các khoảng phút và tỷ lệ do Admin cấu hình; cấu hình hiện hành được dùng khi Staff ghi nhận đến, còn Booking đã tính giảm giữ nguyên số tiền đã lưu. Admin xem báo cáo và phản hồi.
10. **Job Execution:** Staff xem việc được giao, tải nhiều ảnh theo Booking (tối thiểu một ảnh), xác nhận hoàn thành -> AWAITING_PAYMENT.
11. **Payment:** CASH/ONLINE sau khi xong việc; Staff xác nhận CASH; cổng ONLINE xác minh server; khoản giảm do trễ được trừ vào tiền dịch vụ; attempts và SUCCESS duy nhất; COMPLETED khi đã trả.
12. **Feedback và khiếu nại:** Customer gửi đánh giá tổng thể 1–5 sao với chủ đề tùy chọn cho Booking đã hoàn tất; lời nhắn bắt buộc ở 1–3 sao và tùy chọn ở 4–5 sao. Customer xem/sửa trước reply đầu tiên của Admin. Customer có thể lập hồ sơ khiếu nại vệ sinh, hư hại, mất mát hoặc vấn đề khác; đính kèm ảnh và bổ sung ảnh đến khi giải quyết. Công ty xác minh, phản hồi kết quả/cách khắc phục cho khách; trao đổi Customer/Admin diễn ra trong timeline tin nhắn và ảnh append-only của hồ sơ. Customer thấy số tiền đề xuất cùng trạng thái duyệt/thực hiện. Manager xem hội thoại và khoản gắn hồ sơ để duyệt/từ chối khoản do Admin khác lập. Feedback và bằng chứng chỉ Customer sở hữu cùng Admin/Manager xem được.
13. **Khắc phục, tài chính và trách nhiệm Staff:** Ghi từng khoản hoàn/giảm giá, sửa chữa/thay thế, bồi thường và thu hồi nội bộ theo Booking/hồ sơ sự cố, kèm phê duyệt, phương thức, mã tham chiếu và hóa đơn/biên nhận. Admin lập, Manager duyệt khoản tiền. Giao dịch sandbox được đánh dấu mô phỏng, không tính vào dòng tiền thật. Kết quả xác minh Staff được lưu nội bộ; đề xuất nhắc nhở/cảnh cáo theo mức độ cần Manager duyệt, không tự động tăng mức theo số lần hoặc khấu trừ lương. Staff chỉ xem quyết định đã duyệt và thông báo riêng của mình; xác nhận đã nhận không đồng nghĩa thừa nhận lỗi.
14. **System Configuration:** Thông tin địa điểm công ty và 7 ngày giờ hoạt động. Bán kính/tọa độ cũ được giữ trong database để tương thích nhưng không dùng để chặn booking.

## D. Critical journey
- Cấu hình công ty và Service/Staff -> Customer lưu House -> chọn dịch vụ/nhân viên/giờ -> Booking CONFIRMED -> Staff thực hiện và tải ảnh -> AWAITING_PAYMENT -> CASH được Staff xác nhận hoặc ONLINE sandbox được server xác minh -> COMPLETED -> Customer đánh giá hoặc mở hồ sơ sự cố -> công ty xác minh, ghi khắc phục/khoản tiền và phản hồi -> quản lý khác duyệt khoản chi và đề xuất xử lý nội bộ -> hoàn tất và đối soát.
- Hủy đúng hạn do Customer; hủy muộn do Admin; không xóa lịch sử.

## E. Quy định sản phẩm quyết định được việc nghiệm thu
- Chỉ 4 Booking statuses, không ép thêm WORKING/UNPAID/PAID vào Booking.
- Không dùng một Payment 1:1 duy nhất nếu làm mất khả năng retry.
- Không phụ thu giờ kết thúc sau đóng cửa; không auto dispatch Staff.
- Customer không xem/sửa Booking của người khác. Staff không xác nhận thu tiền hay hoàn thành công việc của Staff khác. Admin mới có quyền điều phối và phê duyệt leave.

## F. Non-goals
Microservices, React SPA, JWT, admin-approve-job, rating riêng cho Staff và từng dịch vụ, staff skills, salary, promos, recurring schedules, monthly plans, multi-company, separate Admin/Customer tables, generic Booking status history, payment gateway table, custom notification platform. Hồ sơ khiếu nại và lịch sử quyết định xử lý Staff là module được đưa vào phạm vi riêng.

## G. Constraints and dependencies
Java 21, Spring Boot, Maven, Thymeleaf, Bootstrap, Spring Security/session, SQL Server, JPA, Flyway, VNPay sandbox for ONLINE payment. House addresses are entered manually and do not require Google Maps credentials. Avoid hard-coded production secrets. Apply schema changes only through the append-only SQL Server Flyway migrations; never reset the user's database on startup.

## H. Success criteria
- Demo 3 roles through user journeys described above; guards and edge conditions enforced by Backend.
- SQL Server schema maps 21 business tables; H2/mock is not sole evidence of SQL Server compatibility.
- Tests cover bookings/time/concurrency/payment/ownership/state transitions; actual build + test outcomes reported.
- Documentation, README, seed demo, source files match new baseline; no fake validation claims.
