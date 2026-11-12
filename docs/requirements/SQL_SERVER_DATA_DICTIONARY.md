> **Document:** SQL Server Data Dictionary\
> **File:** `docs/requirements/SQL_SERVER_DATA_DICTIONARY.md`\
> **Version:** v5.9.0\
> **Created:** 2026-10-24\
> **Last Updated:** 2026-11-12\
> **Status:** Active\
# Từ điển dữ liệu SQL Server — 26 bảng

**Logical schema hiện tại:** 26 bảng nghiệp vụ theo phạm vi feedback/khiếu nại/tài chính, hội thoại sự cố, cấu hình giảm trễ, xác nhận nhận bồi thường, trách nhiệm Staff và lịch sử ETA. Lịch sử V19 từng có 27 bảng; V25 bỏ bốn bảng phương án/phiếu việc; V28 thêm hai bảng Staff; V32 thêm bảng ETA. **Physical DBMS:** Microsoft SQL Server. Đây là tài liệu mô tả thiết kế SQL Server; **không** dùng bản `.sql` MySQL từ hồ sơ cũ để chạy trực tiếp.

## Migration V3 và dữ liệu lịch sử

Migration mới thêm `period_no` để cho phép nhiều khung mỗi ngày, `is_active` cho Staff availability và `customer_arrived_at` cho Booking. Migration giữ nguyên 15 bảng nghiệp vụ và không sửa migration đã áp dụng. Existing operating-hour rows được đánh số theo `operating_hour_id` trong từng ngày để bảo toàn giờ hiện hành khi nâng cấp.

## Migration V5 — lịch tuần có ngày hiệu lực

Migration dùng chung V5 thêm `effective_from DATE NOT NULL DEFAULT '1900-01-01'` vào `Company_Operating_Hours`; toàn bộ giờ cũ trở thành lịch nền. Khóa duy nhất mở rộng thành `(company_id,effective_from,day_of_week,period_no)`. Không thêm bảng nghiệp vụ; tổng số vẫn là 15. Lịch áp dụng cho ngày D là phiên bản có `effective_from` lớn nhất nhưng không sau D; phiên bản đó kết thúc ngay trước ngày hiệu lực kế tiếp.

## Migration V6 — xác nhận Staff đến và báo sự cố

Migration dùng chung V6 thêm `staff_arrived_at`, `incident_reported_at` và `incident_note` vào `Bookings`; tổng số bảng nghiệp vụ giữ nguyên 15. Mỗi Booking lưu tối đa một báo cáo sự cố, gồm nội dung và thời điểm; Staff chỉ gửi khi Booking còn `CONFIRMED` và trước `start_time`; hành vi được mở rộng sau ở V32. Customer có thể xem báo cáo trên chi tiết Booking. Không có bảng lịch sử sự cố.

## Migration V8 — đồng thuận trễ và giảm trừ

Migration dùng chung V8 thêm `work_started_at`, ETA dự kiến, phản hồi Customer, phút trễ và phần trăm/giá trị giảm trên `Bookings`; không thêm bảng nghiệp vụ, tổng số vẫn là 15. Theo baseline V8, báo trễ cần Customer đồng ý trước khi Staff bắt đầu và từ chối có thể hủy miễn phí; V32 chuyển lịch sử từng ETA và buộc đồng thuận mới nếu ETA được lỡ. Mức giảm theo số phút trễ được làm tròn lên: 1–15 phút 5%, 16–30 phút 10%, trên 30 phút 20%; tiền giảm làm tròn đến đồng. Thanh toán lưu số tiền sau giảm.

## Migration V9 — rating và chủ đề feedback
Migration dùng chung V9 thêm `rating TINYINT NULL` và `topics NVARCHAR(300) NOT NULL DEFAULT N''` vào `Feedbacks`, đồng thời cho phép `content` NULL để hỗ trợ lời nhắn tùy chọn ở rating 4–5. Rating lịch sử giữ NULL và chỉ đọc; topics mới lưu mã enum phân tách bằng dấu phẩy (danh sách rỗng được phép). Constraint giới hạn rating 1–5, yêu cầu nội dung cho rating 1–3, và bắt buộc reply với trạng thái RESPONDED cùng feedback mới được RESOLVED. Trạng thái RESOLVED lịch sử rating NULL được giữ nguyên. Status chuyển thành OPEN/RESPONDED/RESOLVED; reply cũ được backfill sang RESPONDED. Migration V9 không thêm bảng.

## Migration V10 — hồ sơ sự cố, bằng chứng và sổ tài chính
Migration dùng chung V10 thêm `Users.can_manage_incidents` tạm thời và `Payments.is_simulated`; dữ liệu `ONLINE` hiện có được đánh dấu sandbox mô phỏng, không tính là dòng tiền thật. Thêm bốn bảng nghiệp vụ, đưa tổng số từ 15 lên 19: `Feedback_Cases`, `Feedback_Case_Evidence`, `Staff_Incident_Actions`, `Booking_Financial_Entries`. Migration V15 thay cờ quản lý bằng role `MANAGER`, giữ tối đa một tài khoản Manager và bỏ cột cũ; Admin không thể tự cấp vai trò. Chứng từ lưu đường dẫn file trong storage riêng và metadata trong SQL. Đề xuất cảnh cáo lưu lịch sử, không tự tăng mức theo số lần.

## Migration V12 — cho phép Staff bắt đầu sớm
Migration dùng chung V12 thay `CK_Bookings_Work_Start` thành điều kiện chỉ yêu cầu `staff_arrived_at IS NOT NULL` khi `work_started_at` có giá trị; không so sánh với `start_time`. Staff có thể ghi nhận đến trước giờ hẹn trong Booking CONFIRMED. Customer acknowledgement/Admin verification không thay thế `staff_arrived_at`; Staff được giao phải tự xác nhận đến trước khi bắt đầu. Work start không đổi giờ hẹn, thời lượng hoặc giá.

## Migration V13 — ghi nhận Customer báo Staff chưa đến
Migration dùng chung V13 thêm `customer_delay_reported_at DATETIME2(0) NULL` và constraint yêu cầu thời điểm báo không trước `start_time`. Khi áp dụng V32, Customer sở hữu Booking có thể ghi nhận một lần từ giờ hẹn đến trước khi dịch vụ hoàn tất, kể cả sau khai báo đến, ETA hoặc bắt đầu việc. Thời điểm hiển thị cho Customer sở hữu booking và Admin, ẩn với Staff; không tự chặn bắt đầu/hoàn tất, hủy booking hay thay đổi tiền. Admin chỉ dùng V31 arrival verification để sửa mốc tính giảm sau khi Customer báo giờ Staff khai không đúng và trước khi dịch vụ hoàn tất/thanh toán. Không thêm bảng nghiệp vụ.

## Migration V14 — cấu hình mức giảm do đến trễ
Migration dùng chung V14 thêm `Booking_Delay_Compensation_Tiers` và seed ba mức ban đầu 1–15 phút 5%, 16–30 phút 10%, trên 30 phút 20%. Admin có thể thay đổi các khoảng và phần trăm; khoảng phút phải liên tiếp, bắt đầu từ 1 và chỉ mức cuối được không giới hạn. V30 hạ các tier lưu sẵn vượt 30%; service áp trần 30% cả khi đọc cấu hình tier cũ. Staff tự xác nhận đến sẽ tính phút trễ từ giờ hẹn, làm tròn phút lẻ lên, không ân hạn 5 phút; lưu phút, phần trăm và tiền giảm trên Booking. Admin có thể điều chỉnh mốc/mức này theo xác minh V31 trước khi hoàn tất/thanh toán; thay đổi tier không sửa dữ liệu đã lưu. Customer report/acknowledgement không tự tính hoặc đổi tiền.

## Migration V16 — hội thoại hồ sơ sự cố
Migration dùng chung V16 tạo bảng nghiệp vụ `Feedback_Case_Messages` và thêm `message_id` nullable vào `Feedback_Case_Evidence`; tổng số tăng từ 20 lên 21. Mô tả ban đầu được nhập thành tin Customer đầu tiên; reply Admin cũ được nhập thành tin lịch sử với cờ `legacy_timestamp_unknown=1`, vì Feedbacks không lưu thời điểm reply. Bằng chứng hiện có liên kết với tin Customer đầu tiên. Tin nhắn chỉ được thêm khi hồ sơ chưa RESOLVED; Customer sở hữu booking và Admin/Manager được xem tin/ảnh, Staff và Customer khác bị từ chối. Nội dung tin mới tối đa 4.000 ký tự; một lần gửi tối đa 5 ảnh JPEG/PNG, 5 MB mỗi ảnh. Tin/ảnh append-only; hồ sơ đã giải quyết chỉ đọc.

## Migration V17 — xác nhận nhận khoản bồi thường
Migration dùng chung V17 tạo bảng `Financial_Payout_Responses`, đưa schema lên 22 bảng. Mỗi khoản bồi thường được duyệt có tối đa một phản hồi Customer: `ACCEPTED` với phương thức `BANK_TRANSFER`/`CASH`, hoặc `NEEDS_DISCUSSION`. Thông tin ngân hàng chỉ được yêu cầu khi chọn chuyển khoản và được lưu mã hóa AES-GCM trong `encrypted_account_details`; khóa 32 byte Base64 lấy từ `PAYOUT_ENCRYPTION_KEY`/`payout.encryption-key` phải được cấu hình ngoài repository. Customer không nhận ciphertext hoặc thông tin ngân hàng qua view model. Admin/Manager có thể xem thông tin chuyển khoản đã xác nhận để quyết toán; sau khi ghi nhận khoản đã chi, ciphertext được xóa. Hệ thống không thực hiện chuyển tiền.

## Migration V18 — thông tin món đồ Customer khai báo
V18 thêm tên/mô tả, giá trị ước tính và thời gian sử dụng vào `Feedback_Cases`; không thêm bảng. Đây chỉ là thông tin khách khai báo, không phải định giá hoặc mức bồi thường công ty đã chốt.

## Migration V19 — cấu trúc phương án/phiếu việc lịch sử (đã gỡ ở V25)
V19 từng đưa schema từ 22 lên 27 bảng. Các bảng phương án và phiếu việc được giữ trong lịch sử Flyway nhưng đã bị V25 xóa khỏi schema hiện tại cùng `resolution_workflow_required` và `Booking_Financial_Entries.resolution_action_id`. `Feedback_Case_Verifications` vẫn giữ lịch sử xác minh Admin, gồm trạng thái, kết luận nội bộ và các mã bằng chứng đã xem. Bằng chứng hỗ trợ JPEG/PNG, PDF, tối đa một video MP4/WebM 50 MB trong tối đa năm tệp/liên kết mỗi tin. Video dài có thể dùng link HTTPS Google Drive; ứng dụng không kiểm tra quyền chia sẻ.

Migration V23 thêm `Feedback_Work_Orders.customer_preferred_at DATETIME2(0) NULL`. Migration V24 thêm `customer_preferred_staff_id BIGINT NULL FK -> Staff.staff_id`. Customer chọn Staff và giờ từ lịch khả dụng theo giờ công ty, lịch đăng ký của Staff, thời gian báo trước và booking đang có; Admin xác nhận đúng lựa chọn khách đã gửi. Hai migration không thêm bảng nghiệp vụ.

## Migration V25 — luồng phản ánh và bồi thường rút gọn
V25 đưa schema đang dùng từ 27 xuống 23 bảng bằng cách xóa dữ liệu và bốn bảng V19 `Feedback_Resolution_Plans`, `Feedback_Resolution_Options`, `Feedback_Resolution_Actions`, `Feedback_Work_Orders`; bỏ `resolution_workflow_required` và `Booking_Financial_Entries.resolution_action_id`. Không sửa V19/V23/V24. `Feedback_Cases` thêm mong muốn xử lý tùy chọn của Customer và các trạng thái tiếp nhận, điều tra/tìm tài sản, chờ Customer, Manager, chi trả/xác nhận, hoàn tất. `Financial_Payout_Responses` ghi riêng lúc Customer đồng ý số tiền, chọn cách nhận và xác nhận đã nhận; `AGREED_AMOUNT` không có phương thức nhận hay dữ liệu tài khoản. Chỉ Customer đồng ý mới cho Manager duyệt. Demo được chỉnh để không giả lập Customer đồng ý hoặc Manager duyệt.

## Migration V26 — mốc hoàn tất dịch vụ
V26 thêm `Bookings.service_completed_at DATETIME2(0) NULL`. Khi Staff xác nhận xong việc, ứng dụng lưu thời điểm này trước khi Booking chuyển sang `AWAITING_PAYMENT`; phản ánh mới được mở tối đa 24 giờ sau mốc đó. Dữ liệu cũ ở trạng thái `AWAITING_PAYMENT`/`COMPLETED` được backfill từ lần thanh toán SUCCESS gần nhất, nếu không có thì dùng `Bookings.end_time`. Hồ sơ sự cố lịch sử không bị đổi hoặc xóa.

## Migration V27 — xóa an toàn thông tin nhận tiền sau quyết toán
V27 thêm `SETTLED` vào `Financial_Payout_Responses.response_status`. Khi Admin ghi nhận đã chi, service chuyển phản hồi từ `ACCEPTED` sang `SETTLED` rồi xóa ciphertext tài khoản; constraint chỉ yêu cầu ciphertext khi chuyển khoản còn đang chờ quyết toán. Các khoản bồi thường đã ở trạng thái `PAID` được backfill sang `SETTLED`, xóa ciphertext và điền thời điểm chọn cách nhận từ thời điểm phản hồi cũ nếu thiếu. Customer vẫn phải xác nhận đã nhận tiền trước khi hồ sơ đóng.

## Migration V28 — giải trình Staff và hoàn trả theo đợt
V28 mở rộng trạng thái `Staff_Incident_Actions` và `Feedback_Cases.staff_action_status` để lưu luồng giải trình, Manager kết luận, Staff phản đối một lần và kết luận cuối. Hạn giải trình và phản đối là 48 giờ; thời điểm Staff quá hạn được lưu riêng và không được xem là thừa nhận lỗi. Manager lưu quyết định, căn cứ và số tiền trách nhiệm thực tế riêng với khoản Customer nhận. `Staff_Incident_Action_Evidence` lưu tệp Staff gửi trong giai đoạn giải trình/phản đối; chỉ Staff liên quan và Admin/Manager được truy cập. `Staff_Recovery_Installments` lưu từng lần Admin ghi nhận thực nhận gồm số tiền, phương thức, mã tham chiếu, chứng từ và người/thời điểm ghi nhận. Không tự động khấu trừ lương. Tổng đối soát thu hồi chỉ tính số thực nhận, không tính nghĩa vụ chưa thu. Migration giữ nguyên trạng thái lịch sử; không chuyển quyết định cũ thành đồng thuận, lời giải trình hoặc kết luận mới. Tổng số bảng nghiệp vụ tăng từ 23 lên 25.

## Migration V29 — dữ liệu phản ánh demo và ảnh minh họa

V29 không thay đổi schema. Chỉ trong `home_cleaning_demo`, migration đồng bộ nội dung cho hai hồ sơ demo (vệ sinh và hư hại tài sản), gắn ảnh PNG minh họa vào tin Customer đầu tiên qua `Feedback_Case_Evidence.message_id`, và làm rõ đề xuất 120.000 ₫ chưa được Customer đồng ý, Manager duyệt hoặc chi. Tên tệp và nội dung ghi rõ ảnh minh họa; không xem ảnh là bằng chứng của khách thật. File ảnh được đóng gói tại `src/main/resources/demo-evidence/`; `DemoFeedbackEvidenceInstaller` chỉ chạy trong profile `demo` để chép file vào evidence storage trước tác vụ đối soát.

## Migration V32–V33 — tài khoản demo

V32 và V33 không thay đổi schema. Chỉ trong `home_cleaning_demo`: V32 đổi `Users.full_name` của năm tài khoản demo ban đầu từ tên tạm "… Demo" sang họ tên tiếng Việt (và `Houses.recipient_name` của Customer demo khi còn tên tạm); V33 thêm năm Staff (`Users`, `Staff`, `Staff_Profiles`) và năm Customer (`Users`, mỗi người một `Houses` mặc định loại `HOME` có tọa độ trong bán kính `Company_Settings`). Bản ghi chỉ được chèn khi email/Staff/địa chỉ chưa tồn tại.

## Migration V37 — lý do khóa tài khoản

V37 (dùng chung cho cả hai database) thêm ba cột vào `Users` để lưu lý do khóa, người và thời điểm đổi trạng thái gần nhất; chi tiết ở mục `Users`. Không thêm bảng, tổng vẫn 25 bảng.

## Mapping kiểu dữ liệu đề xuất
| Logical | SQL Server |
|---|---|
| BIGINT auto-increment PK | `BIGINT IDENTITY(1,1)` + `PRIMARY KEY` |
| VARCHAR string tiếng Việt | `NVARCHAR(n)` / `NVARCHAR(MAX)` khi phù hợp |
| TEXT | `NVARCHAR(MAX)` |
| BOOLEAN | `BIT NOT NULL DEFAULT (0)/(1)` |
| DATETIME | `DATETIME2(0)` hoặc độ chính xác đã chọn đồng bộ với Java |
| TIME | `TIME(0)` |
| DATE | `DATE` |
| Money | `DECIMAL(12,2)` (quyết định logical; không đổi sang integer-scale mà không giải thích) |
| Enum | `VARCHAR(20)` với `CHECK` |

Recommended Java: `Long`, `BigDecimal`, `LocalDate`, `LocalTime`, `LocalDateTime` cho local business time; xác định múi giờ ở application boundary trước khi code theo thời gian. Lat/lng `BigDecimal` (`DECIMAL(10,7)`). Với collations/email uniqueness kiểm tra case-insensitivity thực tế trên SQL Server; tránh phụ thuộc vào mặc định môi trường. Tên physical bảng và cột giữ nguyên theo logical contract trong DDL và `@Table/@Column`.

## 1. Users
`user_id PK BIGINT IDENTITY`, `full_name NVARCHAR(150) NOT NULL`, `email NVARCHAR(255) NOT NULL UNIQUE`, `password_hash NVARCHAR(255) NOT NULL`, `phone NVARCHAR(20) NOT NULL`, `role VARCHAR(20) NOT NULL CHECK CUSTOMER/STAFF/ADMIN/MANAGER`, `status VARCHAR(20) NOT NULL DEFAULT ACTIVE CHECK ACTIVE/INACTIVE`. V15 chuyển người quản lý hiện hữu từ `ADMIN` + cờ V10 sang `MANAGER`, bỏ `can_manage_incidents`, và tạo filtered unique index `UX_Users_Single_Manager` bảo đảm chỉ tồn tại một hàng Manager. Manager có toàn bộ quyền Admin; chỉ Manager được tạo/khóa Admin và duyệt khoản tài chính/quyết định xử lý Staff.
V37 thêm `status_reason NVARCHAR(500) NULL`, `status_changed_at DATETIME2(0) NULL`, `status_changed_by BIGINT NULL FK -> Users`. CHECK `CK_Users_StatusReasonOnlyWhenLocked`: lý do chỉ có khi `status = INACTIVE` và không rỗng; CHECK `CK_Users_StatusChangeAudit`: `status_changed_at` và `status_changed_by` cùng NULL hoặc cùng có giá trị. Mọi lần khóa mới (Customer, Staff, Admin) bắt buộc có lý do 5–500 ký tự; mở khóa xóa lý do và ghi người/thời điểm mở. Tài khoản đã khóa trước V37 giữ lý do NULL. Phân quyền khóa: Manager khóa được Customer, Staff, Admin; Admin khóa được Customer và Staff, không khóa Admin khác. Customer hoặc Staff còn Booking `CONFIRMED`/`AWAITING_PAYMENT` thì chưa khóa được.

**Không** có created_at/updated_at. Backend role authorization.

## 2. Staff
`staff_id PK`, `user_id BIGINT NOT NULL UNIQUE FK -> Users`, `staff_type VARCHAR(20) NOT NULL CHECK FULL_TIME/PART_TIME`. Chỉ Users.role=STAFF mới có bản ghi Staff (service authorization; ordinary FK không enforce role).

## 3. Staff_Profiles
`profile_id PK`, `staff_id BIGINT NOT NULL UNIQUE FK -> Staff`, `avatar_url NVARCHAR(500) NULL`, `bio NVARCHAR(MAX) NULL`. Không timestamp.

## 4. Services
`service_id PK`, `service_name NVARCHAR(150) NOT NULL`, `description NVARCHAR(MAX) NULL`, `image_url NVARCHAR(500) NULL`, `price DECIMAL(12,2) NOT NULL CHECK price>=0`, `duration_minutes INT NOT NULL CHECK>0`, `status VARCHAR(20) NOT NULL DEFAULT ACTIVE CHECK ACTIVE/INACTIVE`. Một ảnh URL duy nhất.

## 5. Company_Settings
`company_id PK`, `company_address NVARCHAR(500) NOT NULL`, `latitude DECIMAL(10,7) NOT NULL`, `longitude DECIMAL(10,7) NOT NULL`, `service_radius_km DECIMAL(6,2) NOT NULL CHECK >0`. Chỉ 1 row theo nghiệp vụ; enforce qua application/bootstrap hoặc constraint hợp lý, không phát minh company marketplace. Booking chỉ nhận địa chỉ đã xác minh tọa độ và nằm trong bán kính phục vụ. CHECK lat between -90/+90, lng -180/+180.

## 6. Company_Operating_Hours
`operating_hour_id PK`, `company_id BIGINT NOT NULL FK`, `effective_from DATE NOT NULL DEFAULT '1900-01-01'`, `day_of_week INT NOT NULL CHECK BETWEEN 1 AND 7` (Mon=1), `period_no SMALLINT NOT NULL DEFAULT 1`, `opening_time TIME(0) NULL`, `closing_time TIME(0) NULL`, `is_closed BIT NOT NULL DEFAULT 0`; `UNIQUE(company_id,effective_from,day_of_week,period_no)`. Mỗi phiên bản có đủ bảy ngày; ngày có thể có nhiều khung không chồng lấn hoặc một dòng nghỉ. Giờ bắt đầu Booking trên lưới :00/:30; biên availability Staff có thể theo mốc 15 phút. Các truy vấn Staff/Booking/Admin chọn phiên bản gần nhất không sau ngày được xét.

## 7. Houses
`house_id PK`, `customer_id BIGINT NOT NULL FK -> Users`, `house_name NVARCHAR(100) NOT NULL`, `address NVARCHAR(500) NOT NULL`, `latitude DECIMAL(10,7) NULL`, `longitude DECIMAL(10,7) NULL`, `recipient_name NVARCHAR(150) NOT NULL`, `recipient_phone NVARCHAR(20) NOT NULL`, `province_city NVARCHAR(100) NULL`, `ward_commune NVARCHAR(100) NULL`, `address_detail NVARCHAR(500) NOT NULL`, `address_type VARCHAR(20) NOT NULL CHECK HOME/OFFICE`, `is_default BIT NOT NULL DEFAULT 0`, `address_note NVARCHAR(500) NULL`, `is_deleted BIT NOT NULL DEFAULT 0`. Địa chỉ Customer được xác minh từ VietMap và cần tọa độ khi tạo Booking. `Bookings.house_id` giữ FK sau soft-delete. Từ V35 (quy tắc ứng dụng, không đổi schema), `address_detail` là số nhà/hẻm/căn hộ và tên đường Customer tự nhập, bắt buộc có số không thuộc tên phường/quận và có tên đường; `address` giữ địa chỉ VietMap dùng để định vị. `Bookings.address_snapshot` lưu `address_detail` ghép với `address`. Nhà chưa có `address_detail` đủ cụ thể không đặt Booking mới được cho tới khi Customer cập nhật.

## 8. Staff_Availability
`availability_id PK`, `staff_id BIGINT NOT NULL FK -> Staff`, `work_date DATE NOT NULL`, `start_time TIME(0) NOT NULL`, `end_time TIME(0) NOT NULL`, `created_at DATETIME2(0) NOT NULL DEFAULT SYSDATETIME()`, `is_active BIT NOT NULL DEFAULT 1`. CHECK start<end; no updated_at. PART_TIME tạo khoảng trọn trong một khung giờ công ty; ngừng bằng is_active=0, không xóa/sửa lịch sử.

## 9. Staff_Leaves
`leave_id PK`, `staff_id BIGINT NOT NULL FK -> Staff`, `start_date DATE NOT NULL`, `end_date DATE NOT NULL`, `reason NVARCHAR(500) NOT NULL`, `status VARCHAR(20) NOT NULL DEFAULT PENDING CHECK PENDING/APPROVED/REJECTED`, `created_at DATETIME2(0) NOT NULL DEFAULT SYSDATETIME()`, `reviewed_by BIGINT NULL FK -> Users`, `reviewed_at DATETIME2(0) NULL`. CHECK start<=end; PENDING reviewed fields NULL; APPROVED/REJECTED reviewed fields non-NULL.

## 10. Bookings
`booking_id PK`, `customer_id BIGINT NOT NULL FK -> Users`, `staff_id BIGINT NOT NULL FK -> Staff` (**current**), `original_staff_id BIGINT NOT NULL FK -> Staff` (**immutable**), `house_id BIGINT NOT NULL FK -> Houses`, `address_snapshot NVARCHAR(500) NOT NULL`, `latitude_snapshot DECIMAL(10,7) NULL`, `longitude_snapshot DECIMAL(10,7) NULL`, `address_note_snapshot NVARCHAR(500) NULL`, `start_time DATETIME2(0) NOT NULL`, `end_time DATETIME2(0) NOT NULL`, `total_duration_minutes INT NOT NULL`, `total_amount DECIMAL(12,2) NOT NULL`, `area_note NVARCHAR(MAX) NULL`, `status VARCHAR(20) NOT NULL DEFAULT CONFIRMED CHECK CONFIRMED/AWAITING_PAYMENT/COMPLETED/CANCELLED`, `cancelled_at DATETIME2(0) NULL`, `cancelled_by BIGINT NULL FK -> Users`, `cancellation_reason NVARCHAR(500) NULL`, `created_at DATETIME2(0) NOT NULL DEFAULT SYSDATETIME()`, `customer_arrived_at DATETIME2(0) NULL`, `staff_arrived_at DATETIME2(0) NULL`, `customer_delay_reported_at DATETIME2(0) NULL` (V13, owner Customer/Admin only), arrival verification actual/recorded times, Customer/Admin actor, source and optional Admin note (V31), `incident_reported_at DATETIME2(0) NULL`, `incident_note NVARCHAR(500) NULL`, `work_started_at DATETIME2(0) NULL`, `service_completed_at DATETIME2(0) NULL` (V26, set when Staff confirms work before payment), `incident_expected_arrival_at DATETIME2(0) NULL`, `incident_customer_response VARCHAR(20) NULL CHECK ACCEPTED/DECLINED/EXPIRED`, `incident_customer_responded_at DATETIME2(0) NULL`, `delay_minutes INT NULL CHECK >=0`, `delay_discount_percent DECIMAL(5,2) NOT NULL DEFAULT 0 CHECK BETWEEN 0 AND 100; application policy caps newly calculated compensation at 30% (V30)`, `delay_discount_amount DECIMAL(12,2) NOT NULL DEFAULT 0 CHECK BETWEEN 0 AND total_amount`.
CHECK end_time>start_time, nonnegative totals, valid coordinate range; status CANCELLED means all cancel fields present, others cancel fields NULL. Incident timestamp and note are both NULL or both present and note is nonblank. New incident reports are accepted only within 24 hours of `service_completed_at`; V26 backfills historical values from successful payment time, falling back to scheduled `end_time`. Existing incident records remain unchanged.

## 11. Booking_Details
`booking_detail_id PK`, `booking_id BIGINT NOT NULL FK -> Bookings`, `service_id BIGINT NOT NULL FK -> Services`, `service_name_snapshot NVARCHAR(150) NOT NULL`, `unit_price DECIMAL(12,2) NOT NULL CHECK>=0`, `duration_minutes INT NOT NULL CHECK>0`. No sequence_no, execution_order, service-specific time. Backend guarantees >=1 details per booking and snapshots/totals in one transaction.

## 12. Booking_Images
`image_id PK`, `booking_id BIGINT NOT NULL FK -> Bookings`, `image_url NVARCHAR(500) NOT NULL`, `uploaded_at DATETIME2(0) NOT NULL DEFAULT SYSDATETIME()`. No uploaded_by, no booking_detail_id.

## 13. Payments
`payment_id PK`, `booking_id BIGINT NOT NULL FK -> Bookings`, `amount DECIMAL(12,2) NOT NULL CHECK>=0`, `payment_method VARCHAR(20) NOT NULL CHECK CASH/ONLINE`, `status VARCHAR(20) NOT NULL DEFAULT PENDING CHECK PENDING/SUCCESS/FAILED`, `transaction_code NVARCHAR(255) NULL`, `created_at DATETIME2(0) NOT NULL DEFAULT SYSDATETIME()`, `paid_at DATETIME2(0) NULL`, V10 `is_simulated BIT NOT NULL DEFAULT 0`. ONLINE hiện dùng VNPay sandbox và được đánh dấu mô phỏng. SUCCESS iff paid_at not null; not SUCCESS => paid_at null. One Booking has many attempts. **At most one SUCCESS per Booking**, enforce by SQL Server filtered unique index `UNIQUE(booking_id) WHERE status='SUCCESS'` (implementation recommendation, compatible with schema) and service transaction/locking. No payment_gateway/confirmed_by/updated_at or 1:1 booking uniqueness.

## 14. Feedbacks
`feedback_id PK`, `booking_id BIGINT NOT NULL UNIQUE FK -> Bookings`, `customer_id BIGINT NOT NULL FK -> Users`, `rating TINYINT NULL CHECK NULL OR 1..5`, `topics NVARCHAR(300) NOT NULL DEFAULT N''` (mã topic enum cách nhau bằng dấu phẩy), `content NVARCHAR(MAX) NULL`, `admin_reply NVARCHAR(MAX) NULL`, `status VARCHAR(20) NOT NULL DEFAULT OPEN CHECK OPEN/RESPONDED/RESOLVED`. Rating NULL chỉ dành cho feedback lịch sử; rating mới 1–3 cần content không rỗng, rating 4–5 cho phép content NULL/rỗng. RESPONDED và feedback mới RESOLVED yêu cầu admin_reply không rỗng; OPEN chưa có reply. Customer chỉ sửa trước reply đầu tiên; Admin phải reply trước resolve. Trạng thái RESOLVED lịch sử không rating được giữ nguyên. No created_at, replied_at.

## 15. Staff_Follows
`customer_id BIGINT NOT NULL FK -> Users`, `staff_id BIGINT NOT NULL FK -> Staff`, `created_at DATETIME2(0) NOT NULL DEFAULT SYSDATETIME()`, `PRIMARY KEY(customer_id,staff_id)`; unfollow=DELETE pair.

## 16. Feedback_Cases
`case_id PK`, `feedback_id BIGINT NOT NULL UNIQUE FK -> Feedbacks`, `incident_types NVARCHAR(150) NOT NULL`, `customer_description NVARCHAR(MAX) NOT NULL`, `customer_requested_resolution NVARCHAR(1000) NULL`, item description/Customer estimate/age fields from V18, customer-visible outcome, internal finding and Staff review fields. `case_status VARCHAR(40)` includes `RECEIVED`, `INVESTIGATING`, `SEARCHING_ITEM`, `WAITING_CUSTOMER`, `WAITING_MANAGER`, `WAITING_PAYOUT`, `WAITING_CUSTOMER_CONFIRMATION`, `RESOLVED`; `VERIFIED`/`NOT_SUBSTANTIATED` remain readable for history. Customer desire is reference only. A case can close only after Customer confirmation; compensation cases additionally require an agreed, manager-approved and paid amount and Customer receipt confirmation.

## 17. Feedback_Case_Evidence
`evidence_id PK`, case/message foreign keys, nullable `image_path`, `media_type VARCHAR(20)` IMAGE/PDF/VIDEO/DRIVE_LINK, content type, original filename, nullable `external_url`, uploader and timestamp. Exactly one file path or external link is present. Upload limit is 5 MB for images/PDF and 50 MB for one MP4/WebM video; at most five evidence items per message. Google links are HTTPS links on `drive.google.com` or `docs.google.com`; access rights are managed by the sender.

## 18. Staff_Incident_Actions
`action_id PK`, `case_id BIGINT NOT NULL FK -> Feedback_Cases`, `staff_id BIGINT NOT NULL FK -> Staff`, `action_type VARCHAR(30) CHECK NONE/COACHING/PROPOSE_WARNING/PROPOSE_FINAL_WARNING/PROPOSE_RECOVERY/OTHER`, `status VARCHAR(30)` including legacy `PENDING/APPROVED/REJECTED` and new response/appeal/final/recovery stages, `reason NVARCHAR(1000) NOT NULL`, `staff_notice NVARCHAR(1000) NULL`, `proposed_by BIGINT NOT NULL FK -> Users`, `decided_by BIGINT NULL FK -> Users`, `created_at`, `decided_at`, `acknowledged_at`, `response_deadline`, `staff_response NVARCHAR(4000)`, `staff_responded_at`, `appeal_text NVARCHAR(4000)`, `appealed_at`, `decision_reason NVARCHAR(1000)`, `decision_outcome VARCHAR(20) NULL`, `liability_amount DECIMAL(12,2) NULL`, `finalized_at`, `appeal_decided_by BIGINT NULL`, `appeal_decided_at`. New proposals first allow Staff 48 hours to explain; no response is not an admission. Manager reviews the response or timeout, determines no liability/internal action/actual recovery amount, and gives Staff one 48-hour appeal window. An appeal is resolved by Manager; without appeal the first decision becomes final after deadline. A recovery amount is based on actual loss and is independent of Customer compensation. No automatic payroll deduction.

## 24. Staff_Incident_Action_Evidence
`evidence_id PK`, `action_id BIGINT NOT NULL FK -> Staff_Incident_Actions`, `uploaded_by BIGINT NOT NULL FK -> Users`, `evidence_stage VARCHAR(20) CHECK RESPONSE/APPEAL`, `media_type VARCHAR(20)`, `content_type VARCHAR(100) NULL`, `original_name NVARCHAR(255) NULL`, `storage_path NVARCHAR(500) NOT NULL`, `uploaded_at`. Attachments are private and available only to the related Staff, active Admin and Manager; never include them in the Customer case timeline.

## 25. Staff_Recovery_Installments
`installment_id PK`, `action_id BIGINT NOT NULL FK -> Staff_Incident_Actions`, `amount DECIMAL(12,2) NOT NULL CHECK >0`, `payment_method VARCHAR(20) CHECK CASH/BANK_TRANSFER/OTHER`, `transaction_reference NVARCHAR(255) NOT NULL`, `receipt_path NVARCHAR(500) NULL`, `received_by BIGINT NOT NULL FK -> Users`, `received_at DATETIME2(0) NOT NULL`. Each row is one actual payment recorded by Admin. Service locks the liability record and prevents cumulative receipts exceeding `liability_amount`; state becomes `RECOVERY_SETTLED` only at the full amount. Reports count these received amounts, never the unpaid balance.

## 19. Booking_Financial_Entries
One row per adjustment: `entry_id PK`, `booking_id BIGINT NOT NULL FK -> Bookings`, `case_id BIGINT NULL FK -> Feedback_Cases`, entry type, amount/currency, reason, status, payment method, reference, receipt, simulation flag and actor timestamps. Compensation is allowed only for damage/lost-property cases; Admin enters amount and basis, Customer agrees, then Manager approves. Settlement must match that amount and the Customer-selected payout method. Existing ledger entries remain unchanged.

## 20. Booking_Delay_Compensation_Tiers
`tier_id PK`, `min_delay_minutes INT NOT NULL CHECK >=1`, `max_delay_minutes INT NULL CHECK NULL OR >= min_delay_minutes`, `discount_percent INT NOT NULL CHECK 0..100`. Unique `min_delay_minutes`; at most one row may have `max_delay_minutes IS NULL`. Service validates that ranges start at minute 1, are contiguous and non-overlapping, and end in exactly one unbounded tier. V14 seeds 1–15/5%, 16–30/10%, 31+/20%. Admin edits the current policy; Booking rows retain their calculated discount values.

## 21. Feedback_Case_Messages
`message_id PK`, `case_id BIGINT NOT NULL FK -> Feedback_Cases`, `sender_user_id BIGINT NULL FK -> Users` (NULL only for imported legacy Admin replies), role, text, timestamp and legacy flags. New messages contain text and/or up to five image/PDF/video/Drive-link evidence items; images/PDF max 5 MB, one MP4/WebM video max 50 MB. Resolved cases are read-only. Internal findings and Staff actions remain Admin/Manager only.

## 22. Financial_Payout_Responses
`payout_response_id PK`, `entry_id BIGINT NOT NULL UNIQUE FK -> Booking_Financial_Entries`, `customer_id BIGINT NOT NULL FK -> Users`, `response_status VARCHAR(24) CHECK AGREED_AMOUNT/ACCEPTED/NEEDS_DISCUSSION/SETTLED`, nullable payout method and encrypted account details, `amount_agreed_at`, `payout_method_selected_at`, `customer_confirmed_at`, `responded_at`. `AGREED_AMOUNT` records Customer acceptance before Manager approval and requires no payout method. After approval, Customer chooses cash or transfer; bank details are AES-GCM encrypted, visible only to Admin/Manager and cleared after settlement, which changes the response status to `SETTLED`. Closing a paid compensation case requires `customer_confirmed_at`.

## 23. Feedback_Case_Verifications
`verification_id PK`, `case_id BIGINT NOT NULL FK -> Feedback_Cases`, `admin_id BIGINT NOT NULL FK -> Users`, verification status, comma-separated evidence IDs reviewed in that verification, internal finding and timestamp. Append-only history is visible only to Admin/Manager and does not expose the finding to Customer.

## Referential integrity & SQL Server notes
- For FK child tables `Booking_Details`, `Booking_Images`, `Payments`, `Feedbacks`, `Booking_Financial_Entries` -> Bookings and case messages/evidence/verifications -> parents: `ON DELETE NO ACTION` (SQL Server analog of RESTRICT). Also preserve history on Houses/Staff/Services via appropriate FK protection, no cascade that deletes booking history.
- SQL Server **multiple cascade paths** risk: prefer NO ACTION/restrict where appropriate, avoid delete cascades by default.
- Idempotent service-layer validation: check DB locks/transactions for staff slot collision and availability mutation; use explicit transaction boundaries.
- MySQL `AUTO_INCREMENT`, backticks, `BOOLEAN`, `CURRENT_TIMESTAMP`, `LIMIT`, `ON DELETE RESTRICT` should not be copied verbatim to SQL Server.
- Hibernate `ddl-auto=validate`; Flyway applies schema. Fresh schema migration path must be separated from existing production schema. Do **not** modify existing applied versioned Flyway migrations in-place on a shared DB.
- Flyway's `flyway_schema_history` is a tooling table, not a business Entity.
- The entity mapping and migration must match column types and nullability. Do not silently add auditing, gateway IDs, or admin ID just to make a vendor SDK easier.

## Concurrency focus
Serialize booking creation on same Staff via reliable row lock/transaction/isolation or equivalently proven mechanism. Serialize Payment success callbacks/confirmation on same Booking and enforce filtered unique success. Explicitly test SQL Server behavior; 'query then insert' without a lock is unsafe.


## Migration V31 — xác minh giờ đến đang tranh chấp

V31 thêm vào `Bookings` giờ đến thực tế đã xác minh/điều chỉnh, giờ ghi nhận, User xác minh, nguồn `CUSTOMER`/`ADMIN` và ghi chú Admin tùy chọn. Constraint yêu cầu toàn bộ trường audit bắt buộc cùng có mặt; không tạo Booking status mới hoặc bảng trạng thái chung. Theo luồng hiện hành, Customer xác nhận chỉ là acknowledgement; chỉ Admin dùng các trường V31 để sửa mốc tính giảm sau báo giờ sai và trước khi hoàn tất/thanh toán. Dữ liệu nguồn CUSTOMER cũ vẫn được giữ để đọc lịch sử.
## 26. Booking_Delay_Proposals (V32)
`proposal_id BIGINT PK IDENTITY`, `booking_id BIGINT NOT NULL FK -> Bookings`, `staff_id BIGINT NOT NULL FK -> Staff`, `expected_arrival_at DATETIME2(0) NOT NULL`, `reason NVARCHAR(500) NOT NULL`, `proposed_at DATETIME2(0) NOT NULL`, `status VARCHAR(10) NOT NULL CHECK PENDING/ACCEPTED/DECLINED/EXPIRED`, `responded_at DATETIME2(0) NULL`. Append-only per ETA proposal/response, index `(booking_id, proposal_id DESC)`. Customer no-show reports and acknowledgements never change money. Staff arrival self-claim calculates the persisted delay discount; Admin can correct the baseline via V31 after a Customer report and before completion/payment.
