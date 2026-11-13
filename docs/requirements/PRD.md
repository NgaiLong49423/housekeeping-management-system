> **Document:** Product Requirements Document\
> **File:** `docs/requirements/PRD.md`\
> **Version:** v5.0.0\
> **Created:** 2026-06-14\
> **Last Updated:** 2026-11-13\
> **Status:** Active\
# PRD — HOME CLEANING BOOKING (CẬP NHẬT THEO BASELINE MỚI)


## A. Vision
Hệ thống web cho một công ty dịch vụ dọn dẹp nhà: khách chọn nhà/dịch vụ, tự ghép một hoặc nhiều nhân viên và đặt lịch; nhân viên quản lý khả dụng, xác nhận đã đến, thực hiện việc và trưởng nhóm xác nhận nhận tiền mặt; quản trị viên quản lý vận hành, cấu hình công ty, mô tả/giá dịch vụ, nghỉ phép, đặt lịch, feedback. Dễ triển khai và demo trong đồ án Spring MVC.

## B. User groups and outcomes
| Actor | Mục tiêu | Quyền nổi bật |
|---|---|---|
| CUSTOMER | Đặt và theo dõi dịch vụ | Đăng nhập; Houses; chọn Service/Staff; tạo/hủy Booking; trả tiền; Feedback; Follow Staff |
| STAFF | Thực hiện công việc | Xem booking được giao; cập nhật profile; PART_TIME đăng ký availability; xin nghỉ; upload ảnh; xác nhận xong; trưởng nhóm xác nhận CASH sau khi cả nhóm đã đến |
| ADMIN | Vận hành một công ty dưới quyền Manager | Quản lý Services/Staff và trạng thái; Company Settings/Operating Hours; duyệt leave; xác minh giờ đến trễ, reassignment, feedback, sự cố và đề xuất tài chính |
| MANAGER | Quản trị hệ thống, duy nhất một tài khoản | Có toàn bộ quyền vận hành như Admin; tạo/khóa Admin; duyệt khoản tiền và quyết định nội bộ; không thể tạo Manager thứ hai |

## C. Tính năng trong phạm vi (14 module)
1. **Authentication:** Users dùng chung, role/status, session login, phân quyền và ownership.
2. **Service Management:** Danh mục Service, ảnh/giá/thời lượng, ACTIVE/INACTIVE.
3. **Staff Profile:** Staff profile avatar_url, bio; Staff self-edit.
4. **Follow Staff:** Customer follow/unfollow.
5. **House Management:** House gồm người nhận, điện thoại, loại và địa chỉ mặc định. Khi tạo địa chỉ hoặc đổi vị trí, Customer phải chọn kết quả VietMap để xác minh địa chỉ và tọa độ; sửa thông tin khác giữ vị trí đã lưu. VietMap lỗi thì không có gợi ý giả và không lưu vị trí mới; autocomplete trả 503. Soft-delete có kiểm tra Booking chưa xong.
6. **Booking Management:** Tạo Booking từ 1+ Service, nhóm 1+ Staff, 1 House; trưởng nhóm do Customer chọn. Giá là tổng giá cơ bản + (số người − 1) × tổng phụ phí thêm người của các dịch vụ; thời lượng không đổi. Giờ bắt đầu lưới 30 phút, lead time 2h/30d horizon, lịch nghỉ. Địa chỉ House phải nằm trong bán kính đường chim bay từ tâm công ty. Với từng Staff, hai Booking liền kề cần tuyến VietMap không vượt bán kính công ty và ETA + 15 phút; cùng địa điểm đệm 15 phút. Không áp dụng chặng từ công ty đến Booking đầu ngày; không xác minh được tuyến thì slot bị chặn. Customer hủy trực tiếp khi Booking CONFIRMED, công việc chưa bắt đầu và còn ít nhất 120 phút (đúng 120 phút vẫn được). Không có bước xem trước phí; mọi lần hủy mới miễn phí, ghi người/thời điểm/lý do và không tạo nợ, hoàn tiền, chuyển ví hay chế tài tự động.
7. **Staff Availability:** FULL_TIME tự động theo công ty; PART_TIME tự đăng ký ngày-khoảng cụ thể, không đè booking.
8. **Leave Management:** Staff xin nghỉ nguyên ngày; Admin duyệt/từ chối, xử lý booking xung đột.
9. **Arrival and Incident Tracking:** Mỗi Staff đang được phân công tự xác nhận đã đến; trưởng nhóm chỉ bắt đầu sau khi mọi thành viên đang active đã xác nhận. Staff chỉ gửi ETA trễ trước giờ bắt đầu; Customer chọn đợi hoặc từ chối và hủy miễn phí trong cùng giao dịch. Nếu đã chọn đợi, khách vẫn hủy miễn phí đến khi công việc bắt đầu, kể cả khi ETA bị lỡ; ETA lỡ hết hiệu lực và cần đề xuất/đồng ý mới để bắt đầu. Từ giờ hẹn, Customer có thể báo trưởng nhóm chưa tới hoặc giờ tự khai không đúng, kể cả khi Staff đã tự báo đến; báo cáo một mình không mở ngoại lệ hủy và tạm khóa bắt đầu. Admin xác minh chưa tới hoặc giờ đến thực tế trễ rồi Customer chọn đợi hoặc hủy miễn phí; lựa chọn đợi được lưu. Nếu dịch vụ tiếp tục, giảm trễ theo mức Admin cấu hình dựa trên giờ đến thực tế. Lưu người/thời điểm/ghi chú xác minh và không theo dõi GPS.
10. **Job Execution:** Staff xem việc được giao, tải nhiều ảnh theo Booking (tối thiểu một ảnh), xác nhận hoàn thành -> AWAITING_PAYMENT.
11. **Payment:** CASH/ONLINE sau khi xong việc; chỉ trưởng nhóm xác nhận CASH; cổng ONLINE xác minh server; khoản giảm trễ dùng giờ đến thực tế do Staff khai hoặc Admin xác minh trước khi hoàn tất/thanh toán; attempts và SUCCESS duy nhất; COMPLETED khi đã trả. Thanh toán sau dịch vụ không phát sinh phí hoặc khoản nợ vì trả sau. Bồi thường Customer có đề xuất xử lý Staff chỉ được quyết toán sau khi Manager chốt cuối trách nhiệm, gồm giải trình/kháng nghị; không cần chờ Staff trả đủ.
12. **Feedback và khiếu nại:** Customer gửi đánh giá tổng thể 1–5 sao và có thể kèm báo sự cố trong 24 giờ kể từ khi Staff xác nhận hoàn tất. Hồ sơ cần ít nhất một bằng chứng phù hợp (ưu tiên ảnh; có thể là ảnh có sẵn, hóa đơn, video hoặc link Google Drive do người gửi tự cấp quyền). Form có ô mong muốn xử lý tùy chọn để Admin tham khảo; Customer không tự định giá hoặc chọn bồi thường. Hồ sơ bắt đầu ở “Chờ Admin tiếp nhận”, rồi Admin tiếp nhận, xác minh và trao đổi. Feedback và bằng chứng chỉ Customer sở hữu cùng Admin/Manager xem được.
13. **Khắc phục, tài chính và trách nhiệm Staff:** Admin gửi kết quả để Customer xác nhận. Chỉ hư hại/mất tài sản mới được đề xuất bồi thường; mất đồ phải ưu tiên tìm và trả trước. Luồng tiền: Admin đề xuất số tiền và căn cứ → Customer đồng ý → Manager duyệt → Customer chọn tiền mặt/chuyển khoản → Admin chi bên ngoài hệ thống và lưu chứng từ → Customer xác nhận đã nhận → đóng hồ sơ. Thông tin chuyển khoản chỉ hỏi sau duyệt, được mã hóa và xóa sau quyết toán. Trách nhiệm Staff là quy trình nội bộ: Staff có 48 giờ giải trình và một lần phản đối 48 giờ; Manager quyết định độc lập theo thiệt hại thực tế. Admin ghi từng khoản Staff thực trả kèm chứng từ; không tự khấu trừ lương. Customer chỉ thấy đang xác minh nội bộ. Không còn phương án nhiều lựa chọn hoặc phiếu/lịch làm lại trong hồ sơ sự cố.
14. **System Configuration:** Thông tin địa điểm công ty, bán kính phục vụ và 7 ngày giờ hoạt động. Bán kính áp dụng cho vị trí House so với tâm công ty và làm giới hạn khoảng cách tuyến giữa hai Booking liền kề; không tính tuyến từ công ty đến Booking đầu ngày.

## D. Critical journey
- Cấu hình công ty và Service/Staff -> Customer lưu House -> chọn dịch vụ/nhân viên/giờ -> Booking CONFIRMED -> Staff khai giờ đến, Customer phản hồi hoặc báo sai giờ (không bắt buộc phản hồi), Admin xác minh tranh chấp trước khi hoàn tất nếu cần -> trưởng nhóm chỉ bắt đầu sau khi cả nhóm đã đến -> nhóm thực hiện, tải ảnh -> AWAITING_PAYMENT -> CASH được trưởng nhóm xác nhận hoặc ONLINE sandbox được server xác minh -> COMPLETED -> Customer đánh giá/báo sự cố -> Chờ Admin tiếp nhận -> Admin xác minh/trao đổi -> (nếu hư hại/mất) Admin đề xuất bồi thường -> Customer đồng ý -> Manager duyệt -> Customer chọn cách nhận -> Admin ghi chi và chứng từ -> Customer xác nhận đã nhận -> đóng hồ sơ.
- Customer tự hủy thông thường khi còn ít nhất 120 phút; trường hợp Staff trễ được hủy miễn phí trước khi bắt đầu theo luồng ETA hoặc xác minh Admin. Ghi lịch sử mọi lần hủy; Admin xem số lần và lý do trong 30 ngày để tham khảo, không tự áp chế tài.

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
- SQL Server schema maps 31 business tables; H2/mock is not sole evidence of SQL Server compatibility.
- Tests cover bookings/time/concurrency/payment/ownership/state transitions; actual build + test outcomes reported.
- Documentation, README, seed demo, source files match new baseline; no fake validation claims.
