> **Document:** Product Requirements Document\
> **File:** `docs/requirements/PRD.md`\
> **Version:** v2.0.0\
> **Created:** 2026-06-14\
> **Last Updated:** 2026-10-24\
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
5. **House Management:** Nhiều Houses, Google Maps geocoding/place selection, tọa độ, ghi chú; soft-delete có kiểm tra booking chưa xong.
6. **Booking Management:** Tạo Booking từ 1+ Service, 1 Staff, 1 House; 30-min starts, lead time 2h/30d horizon, Haversine, lịch nghỉ, đệm 60min, snapshots, cancel, reassignment.
7. **Staff Availability:** FULL_TIME tự động theo công ty; PART_TIME tự đăng ký ngày-khoảng cụ thể, không đè booking.
8. **Leave Management:** Staff xin nghỉ nguyên ngày; Admin duyệt/từ chối, xử lý booking xung đột.
9. **Job Execution:** Staff xem việc được giao, tải nhiều ảnh theo Booking, xác nhận hoàn thành -> AWAITING_PAYMENT.
10. **Payment:** CASH/ONLINE sau khi xong việc; Staff xác nhận CASH; cổng ONLINE xác minh server; attempts và SUCCESS duy nhất; COMPLETED khi đã trả.
11. **Feedback:** Customer gửi 1 feedback dạng text/Completed Booking; Admin reply và resolve.
12. **System Configuration:** Địa điểm công ty, bán kính, 7 ngày giờ hoạt động.

## D. Critical journey
- Cấu hình công ty và Service/Staff -> Customer lưu House -> chọn Nhà/Staff/Services/start -> hệ thống kiểm tra đầy đủ -> Booking CONFIRMED -> Staff làm và upload ảnh -> AWAITING_PAYMENT -> CASH được Staff xác nhận hoặc ONLINE server verified -> Payment SUCCESS & Booking COMPLETED -> Customer feedback -> Admin reply/resolve.
- Hủy đúng hạn do Customer; hủy muộn do Admin; không xóa lịch sử.

## E. Quy định sản phẩm quyết định được việc nghiệm thu
- Chỉ 4 Booking statuses, không ép thêm WORKING/UNPAID/PAID vào Booking.
- Không dùng một Payment 1:1 duy nhất nếu làm mất khả năng retry.
- Không phụ thu giờ kết thúc sau đóng cửa; không auto dispatch Staff.
- Customer không xem/sửa Booking của người khác. Staff không xác nhận thu tiền hay hoàn thành công việc của Staff khác. Admin mới có quyền điều phối và phê duyệt leave.

## F. Non-goals
Microservices, React SPA, JWT, admin-approve-job, rating, staff skills, salary, promos, recurring schedules, monthly plans, multi-company, separate Admin/Customer tables, status history, payment gateway table, custom notification platform.

## G. Constraints and dependencies
Java 21, Spring Boot 4.1.1 (verify repository actual), Maven, Thymeleaf, Bootstrap, Spring Security/session, SQL Server, JPA, Flyway, PayOS reused if viable, Google Maps addressing (credentials configured securely). Avoid hard-coded production secrets. Database/data migration strategy remains pending approval; implementation starts with disposable dev DB without destructive operations.

## H. Success criteria
- Demo 3 roles through user journeys described above; guards and edge conditions enforced by Backend.
- SQL Server schema maps 15 approved tables; H2/mock is not sole evidence of SQL Server compatibility.
- Tests cover bookings/time/concurrency/payment/ownership/state transitions; actual build + test outcomes reported.
- Documentation, README, seed demo, source files match new baseline; no fake validation claims.
