> **Document:** Product Requirements Document\
> **File:** `docs/requirements/PRD.md`\
> **Version:** v4.1.0\
> **Created:** 2026-06-14\
> **Last Updated:** 2026-11-12\
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
5. **House Management:** Nhiều địa chỉ dạng văn bản gồm người nhận, điện thoại, loại và địa chỉ mặc định. Backend tra ngầm VietMap lấy tọa độ định tuyến; Customer không phải nhập tọa độ hoặc chọn suggestion; soft-delete có kiểm tra Booking chưa xong.
6. **Booking Management:** Tạo Booking từ 1+ Service, 1 Staff, 1 House; giờ bắt đầu lưới 30 phút, lead time 2h/30d horizon, lịch nghỉ. Giữa hai Booking liền kề cùng Staff cần ETA VietMap + 15 phút; cùng địa điểm đệm 15 phút. Không áp dụng bán kính hoặc chặng từ công ty đến Booking đầu ngày; không có ETA thì slot không đặt được.
7. **Staff Availability:** FULL_TIME tự động theo công ty; PART_TIME tự đăng ký ngày-khoảng cụ thể, không đè booking.
8. **Leave Management:** Staff xin nghỉ nguyên ngày; Admin duyệt/từ chối, xử lý booking xung đột.
9. **Arrival and Incident Tracking:** Staff được phân công phải tự xác nhận đã đến trước khi bắt đầu; Customer xác nhận giờ Staff khai hoặc báo giờ đó không đúng, Admin không thay thế dấu đến của Staff. Customer report là thông tin theo dõi, không tự chặn bắt đầu/hoàn tất, hủy Booking hoặc đổi tiền; report không tự mở quyền hủy miễn phí. Giờ Staff khai là mốc tính giảm trễ từ giờ hẹn, làm tròn phút lẻ lên theo tier hiện hành, không ân hạn 5 phút, tối đa 30%; lưu mức giảm trên Booking. Nếu Customer báo giờ sai, Admin được xác minh/chỉnh mốc và tính lại trước khi dịch vụ hoàn tất/thanh toán; lưu nguyên giờ Staff khai, giờ Admin chỉnh, người/thời điểm/ghi chú. Customer im lặng thì giữ giờ Staff khai. Staff gửi ETA cụ thể; Customer đồng ý đúng ETA hoặc từ chối và hủy miễn phí. Nếu ETA đã đồng ý bị lỡ, Staff cần ETA mới và Customer đồng ý lại; Customer được hủy miễn phí trước khi công việc bắt đầu. Báo trễ đơn thuần không cấp quyền hủy. Không theo dõi GPS.
10. **Job Execution:** Staff xem việc được giao, tải nhiều ảnh theo Booking (tối thiểu một ảnh), xác nhận hoàn thành -> AWAITING_PAYMENT.
11. **Payment:** CASH/ONLINE sau khi xong việc; Staff xác nhận CASH; cổng ONLINE xác minh server; khoản giảm dùng giờ Staff tự khai hoặc giờ Admin đã điều chỉnh trước khi hoàn tất/thanh toán; attempts và SUCCESS duy nhất; COMPLETED khi đã trả. Bồi thường Customer có đề xuất xử lý Staff chỉ được quyết toán sau khi Manager chốt cuối trách nhiệm, gồm giải trình/kháng nghị; không cần chờ Staff trả đủ.
12. **Feedback và khiếu nại:** Customer gửi đánh giá tổng thể 1–5 sao và có thể kèm báo sự cố trong 24 giờ kể từ khi Staff xác nhận hoàn tất. Hồ sơ cần ít nhất một bằng chứng phù hợp (ưu tiên ảnh; có thể là ảnh có sẵn, hóa đơn, video hoặc link Google Drive do người gửi tự cấp quyền). Form có ô mong muốn xử lý tùy chọn để Admin tham khảo; Customer không tự định giá hoặc chọn bồi thường. Hồ sơ bắt đầu ở “Chờ Admin tiếp nhận”, rồi Admin tiếp nhận, xác minh và trao đổi. Feedback và bằng chứng chỉ Customer sở hữu cùng Admin/Manager xem được.
13. **Khắc phục, tài chính và trách nhiệm Staff:** Admin gửi kết quả để Customer xác nhận. Chỉ hư hại/mất tài sản mới được đề xuất bồi thường; mất đồ phải ưu tiên tìm và trả trước. Luồng tiền: Admin đề xuất số tiền và căn cứ → Customer đồng ý → Manager duyệt → Customer chọn tiền mặt/chuyển khoản → Admin chi bên ngoài hệ thống và lưu chứng từ → Customer xác nhận đã nhận → đóng hồ sơ. Thông tin chuyển khoản chỉ hỏi sau duyệt, được mã hóa và xóa sau quyết toán. Trách nhiệm Staff là quy trình nội bộ: Staff có 48 giờ giải trình và một lần phản đối 48 giờ; Manager quyết định độc lập theo thiệt hại thực tế. Admin ghi từng khoản Staff thực trả kèm chứng từ; không tự khấu trừ lương. Customer chỉ thấy đang xác minh nội bộ. Không còn phương án nhiều lựa chọn hoặc phiếu/lịch làm lại trong hồ sơ sự cố.
14. **System Configuration:** Thông tin địa điểm công ty và 7 ngày giờ hoạt động. Bán kính/tọa độ cũ được giữ để tương thích, không chặn Booking.

## D. Critical journey
- Cấu hình công ty và Service/Staff -> Customer lưu House -> chọn dịch vụ/nhân viên/giờ -> Booking CONFIRMED -> Staff khai giờ đến, Customer phản hồi hoặc báo sai giờ (không bắt buộc phản hồi), Admin xác minh tranh chấp trước khi hoàn tất nếu cần -> Staff xác nhận bắt đầu và thực hiện, tải ảnh -> AWAITING_PAYMENT -> CASH được Staff xác nhận hoặc ONLINE sandbox được server xác minh -> COMPLETED -> Customer đánh giá/báo sự cố -> Chờ Admin tiếp nhận -> Admin xác minh/trao đổi -> (nếu hư hại/mất) Admin đề xuất bồi thường -> Customer đồng ý -> Manager duyệt -> Customer chọn cách nhận -> Admin ghi chi và chứng từ -> Customer xác nhận đã nhận -> đóng hồ sơ.
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
- SQL Server schema maps 26 business tables; H2/mock is not sole evidence of SQL Server compatibility.
- Tests cover bookings/time/concurrency/payment/ownership/state transitions; actual build + test outcomes reported.
- Documentation, README, seed demo, source files match new baseline; no fake validation claims.
