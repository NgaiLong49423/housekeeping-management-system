> **Document:** Product Requirements Document\
> **File:** `docs/requirements/PRD.md`\
> **Version:** v2.3.0\
> **Created:** 2026-06-14\
> **Last Updated:** 2026-11-09\
> **Status:** Active\
# PRD — HOME CLEANING BOOKING (CẬP NHẬT THEO BASELINE MỚI)


## A. Vision
Hệ thống web cho một công ty dịch vụ dọn dẹp nhà: khách chọn nhà/nhân viên/dịch vụ và đặt lịch; nhân viên quản lý khả dụng, hoàn thành việc và xác nhận nhận tiền mặt; quản trị viên quản lý vận hành, cấu hình công ty, dịch vụ, nghỉ phép, đặt lịch, feedback. Dễ triển khai và demo trong đồ án Spring MVC.

## B. User groups and outcomes
| Actor | Mục tiêu | Quyền nổi bật |
|---|---|---|
| CUSTOMER | Đặt và theo dõi dịch vụ | Đăng nhập; Houses; chọn Service/Staff; tạo/hủy Booking; trả tiền; Feedback; Follow Staff |
| STAFF | Thực hiện công việc | Xem booking được giao; cập nhật profile; PART_TIME đăng ký availability; xin nghỉ; upload ảnh; xác nhận xong; xác nhận CASH |
| ADMIN | Vận hành một công ty | Quản lý Services/Staff và trạng thái; Company Settings/Operating Hours; duyệt leave; xử lý hủy muộn, reassignment, feedback |

## C. Tính năng trong phạm vi (12 module)
1. **Authentication:** Users dùng chung, role/status, session login, phân quyền và ownership.
2. **Service Management:** Danh mục Service, ảnh/giá/thời lượng, ACTIVE/INACTIVE.
3. **Staff Profile:** Staff profile avatar_url, bio; Staff self-edit.
4. **Follow Staff:** Customer follow/unfollow.
5. **House Management:** Nhiều địa chỉ nhập tay gồm người nhận, số điện thoại, tỉnh/thành, phường/xã, chi tiết, loại và địa chỉ mặc định; không cần Google Maps hoặc tọa độ; soft-delete có kiểm tra booking chưa xong.
6. **Booking Management:** Tạo Booking từ 1+ Service, 1 Staff, 1 House; 30-min starts, lead time 2h/30d horizon, lịch nghỉ, đệm 60min, snapshot địa chỉ văn bản, cancel, reassignment.
7. **Staff Availability:** FULL_TIME tự động theo công ty; PART_TIME tự đăng ký ngày-khoảng cụ thể, không đè booking.
8. **Leave Management:** Staff xin nghỉ nguyên ngày; Admin duyệt/từ chối, xử lý booking xung đột.
9. **Arrival and Incident Tracking:** Staff và Customer ghi nhận đã đến độc lập từ giờ bắt đầu; Staff báo nguyên nhân và giờ dự kiến đến, gọi Customer; Customer đồng ý chờ hoặc từ chối trên Booking. Chưa phản hồi thì Staff chưa thể bắt đầu; từ chối hoặc trễ trên 30 phút có thể hủy miễn phí trước khi bắt đầu. Nếu tiếp tục, giảm 5%/10%/20% theo số phút Staff đến thực tế; Admin xem báo cáo và phản hồi.
10. **Job Execution:** Staff xem việc được giao, tải nhiều ảnh theo Booking (tối thiểu một ảnh), xác nhận hoàn thành -> AWAITING_PAYMENT.
11. **Payment:** CASH/ONLINE sau khi xong việc; Staff xác nhận CASH; cổng ONLINE xác minh server; khoản giảm do trễ được trừ vào tiền dịch vụ; attempts và SUCCESS duy nhất; COMPLETED khi đã trả.
12. **Feedback:** Customer gửi 1 feedback dạng text/Completed Booking; Admin reply và resolve.
13. **System Configuration:** Thông tin địa điểm công ty và 7 ngày giờ hoạt động. Bán kính/tọa độ cũ được giữ trong database để tương thích nhưng không dùng để chặn booking.

## D. Critical journey
- Cấu hình công ty và Service/Staff -> Customer lưu House -> chọn Nhà/Staff/Services/start -> hệ thống kiểm tra đầy đủ -> Booking CONFIRMED và nhắc Customer xác nhận Staff đến -> Staff báo sự cố trước giờ hẹn nếu cần, gọi Customer trực tiếp, ghi nhận đã đến và tải ít nhất một ảnh minh chứng -> xác nhận hoàn thành -> AWAITING_PAYMENT -> CASH được Staff xác nhận hoặc ONLINE server verified -> Payment SUCCESS & Booking COMPLETED -> Customer feedback -> Admin reply/resolve.
- Hủy đúng hạn do Customer; hủy muộn do Admin; không xóa lịch sử.

## E. Quy định sản phẩm quyết định được việc nghiệm thu
- Chỉ 4 Booking statuses, không ép thêm WORKING/UNPAID/PAID vào Booking.
- Không dùng một Payment 1:1 duy nhất nếu làm mất khả năng retry.
- Không phụ thu giờ kết thúc sau đóng cửa; không auto dispatch Staff.
- Customer không xem/sửa Booking của người khác. Staff không xác nhận thu tiền hay hoàn thành công việc của Staff khác. Admin mới có quyền điều phối và phê duyệt leave.

## F. Non-goals
Microservices, React SPA, JWT, admin-approve-job, rating, staff skills, salary, promos, recurring schedules, monthly plans, multi-company, separate Admin/Customer tables, status history, payment gateway table, custom notification platform.

## G. Constraints and dependencies
Java 21, Spring Boot, Maven, Thymeleaf, Bootstrap, Spring Security/session, SQL Server, JPA, Flyway, VNPay sandbox for ONLINE payment. House addresses are entered manually and do not require Google Maps credentials. Avoid hard-coded production secrets. Apply schema changes only through the append-only SQL Server Flyway migrations; never reset the user's database on startup.

## H. Success criteria
- Demo 3 roles through user journeys described above; guards and edge conditions enforced by Backend.
- SQL Server schema maps 15 approved tables; H2/mock is not sole evidence of SQL Server compatibility.
- Tests cover bookings/time/concurrency/payment/ownership/state transitions; actual build + test outcomes reported.
- Documentation, README, seed demo, source files match new baseline; no fake validation claims.
