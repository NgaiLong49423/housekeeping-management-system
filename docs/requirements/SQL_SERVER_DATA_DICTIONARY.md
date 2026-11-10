> **Document:** SQL Server Data Dictionary\
> **File:** `docs/requirements/SQL_SERVER_DATA_DICTIONARY.md`\
> **Version:** v5.0.0\
> **Created:** 2026-10-24\
> **Last Updated:** 2026-11-10\
> **Status:** Active\
# Từ điển dữ liệu SQL Server — 21 bảng

**Logical schema:** 21 bảng nghiệp vụ theo phạm vi feedback/khiếu nại/tài chính, hội thoại sự cố và cấu hình giảm trễ được duyệt. **Physical DBMS:** Microsoft SQL Server. Đây là tài liệu mô tả thiết kế SQL Server; **không** dùng bản `.sql` MySQL từ hồ sơ cũ để chạy trực tiếp.

## Migration V3 và dữ liệu lịch sử

Migration mới thêm `period_no` để cho phép nhiều khung mỗi ngày, `is_active` cho Staff availability và `customer_arrived_at` cho Booking. Migration giữ nguyên 15 bảng nghiệp vụ và không sửa migration đã áp dụng. Existing operating-hour rows được đánh số theo `operating_hour_id` trong từng ngày để bảo toàn giờ hiện hành khi nâng cấp.

## Migration V5 — lịch tuần có ngày hiệu lực

Migration dùng chung V5 thêm `effective_from DATE NOT NULL DEFAULT '1900-01-01'` vào `Company_Operating_Hours`; toàn bộ giờ cũ trở thành lịch nền. Khóa duy nhất mở rộng thành `(company_id,effective_from,day_of_week,period_no)`. Không thêm bảng nghiệp vụ; tổng số vẫn là 15. Lịch áp dụng cho ngày D là phiên bản có `effective_from` lớn nhất nhưng không sau D; phiên bản đó kết thúc ngay trước ngày hiệu lực kế tiếp.

## Migration V6 — xác nhận Staff đến và báo sự cố

Migration dùng chung V6 thêm `staff_arrived_at`, `incident_reported_at` và `incident_note` vào `Bookings`; tổng số bảng nghiệp vụ giữ nguyên 15. Mỗi Booking lưu tối đa một báo cáo sự cố, gồm nội dung và thời điểm; Staff chỉ gửi khi Booking còn `CONFIRMED` và trước `start_time`. Customer có thể xem báo cáo trên chi tiết Booking. Không có bảng lịch sử sự cố.

## Migration V8 — đồng thuận trễ và giảm trừ

Migration dùng chung V8 thêm `work_started_at`, ETA dự kiến, phản hồi Customer, phút trễ và phần trăm/giá trị giảm trên `Bookings`; không thêm bảng nghiệp vụ, tổng số vẫn là 15. Một báo cáo trễ cần Customer đồng ý rõ ràng trước khi Staff bắt đầu. Từ chối hủy miễn phí nếu công việc chưa bắt đầu. Mức giảm theo số phút trễ được làm tròn lên: 1–15 phút 5%, 16–30 phút 10%, trên 30 phút 20%; tiền giảm làm tròn đến đồng. Thanh toán lưu số tiền sau giảm.

## Migration V9 — rating và chủ đề feedback
Migration dùng chung V9 thêm `rating TINYINT NULL` và `topics NVARCHAR(300) NOT NULL DEFAULT N''` vào `Feedbacks`, đồng thời cho phép `content` NULL để hỗ trợ lời nhắn tùy chọn ở rating 4–5. Rating lịch sử giữ NULL và chỉ đọc; topics mới lưu mã enum phân tách bằng dấu phẩy (danh sách rỗng được phép). Constraint giới hạn rating 1–5, yêu cầu nội dung cho rating 1–3, và bắt buộc reply với trạng thái RESPONDED cùng feedback mới được RESOLVED. Trạng thái RESOLVED lịch sử rating NULL được giữ nguyên. Status chuyển thành OPEN/RESPONDED/RESOLVED; reply cũ được backfill sang RESPONDED. Migration V9 không thêm bảng.

## Migration V10 — hồ sơ sự cố, bằng chứng và sổ tài chính
Migration dùng chung V10 thêm `Users.can_manage_incidents` tạm thời và `Payments.is_simulated`; dữ liệu `ONLINE` hiện có được đánh dấu sandbox mô phỏng, không tính là dòng tiền thật. Thêm bốn bảng nghiệp vụ, đưa tổng số từ 15 lên 19: `Feedback_Cases`, `Feedback_Case_Evidence`, `Staff_Incident_Actions`, `Booking_Financial_Entries`. Migration V15 thay cờ quản lý bằng role `MANAGER`, giữ tối đa một tài khoản Manager và bỏ cột cũ; Admin không thể tự cấp vai trò. Chứng từ lưu đường dẫn file trong storage riêng và metadata trong SQL. Đề xuất cảnh cáo lưu lịch sử, không tự tăng mức theo số lần.

## Migration V12 — cho phép Staff bắt đầu sớm
Migration dùng chung V12 thay `CK_Bookings_Work_Start` thành điều kiện chỉ yêu cầu `staff_arrived_at IS NOT NULL` khi `work_started_at` có giá trị; không so sánh với `start_time`. Customer và Staff có thể ghi nhận đến trước giờ hẹn trong Booking CONFIRMED; Customer là tùy chọn và Staff là điều kiện bắt buộc để bắt đầu. Không thêm bảng nghiệp vụ và không đổi giờ hẹn, thời lượng hoặc giá booking.

## Migration V13 — ghi nhận Customer báo Staff chưa đến
Migration dùng chung V13 thêm `customer_delay_reported_at DATETIME2(0) NULL` và constraint yêu cầu thời điểm báo không trước `start_time`. Customer chỉ ghi nhận một lần trên Booking CONFIRMED khi giờ hẹn đã đến, Staff chưa xác nhận đến và công việc chưa bắt đầu. Thời điểm hiển thị cho Customer sở hữu booking và Admin, ẩn với Staff; không tự hủy booking, chặn bắt đầu hay thay đổi tiền. Không thêm bảng nghiệp vụ.

## Migration V14 — cấu hình mức giảm do đến trễ
Migration dùng chung V14 thêm `Booking_Delay_Compensation_Tiers` và seed ba mức ban đầu 1–15 phút 5%, 16–30 phút 10%, trên 30 phút 20%. Admin có thể thay đổi các khoảng và phần trăm; khoảng phút phải liên tiếp, bắt đầu từ 1 và chỉ mức cuối được không giới hạn. `Bookings.delay_discount_percent` chấp nhận cấu hình 0–100%. Thay đổi chính sách không sửa số phút, phần trăm hoặc số tiền đã lưu trên Booking.

## Migration V16 — hội thoại hồ sơ sự cố
Migration dùng chung V16 tạo bảng nghiệp vụ `Feedback_Case_Messages` và thêm `message_id` nullable vào `Feedback_Case_Evidence`; tổng số tăng từ 20 lên 21. Mô tả ban đầu được nhập thành tin Customer đầu tiên; reply Admin cũ được nhập thành tin lịch sử với cờ `legacy_timestamp_unknown=1`, vì Feedbacks không lưu thời điểm reply. Bằng chứng hiện có liên kết với tin Customer đầu tiên. Tin nhắn chỉ được thêm khi hồ sơ chưa RESOLVED; Customer sở hữu booking và Admin/Manager được xem tin/ảnh, Staff và Customer khác bị từ chối. Nội dung tin mới tối đa 4.000 ký tự; một lần gửi tối đa 5 ảnh JPEG/PNG, 5 MB mỗi ảnh. Tin/ảnh append-only; hồ sơ đã giải quyết chỉ đọc.

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
**Không** có created_at/updated_at. Backend role authorization.

## 2. Staff
`staff_id PK`, `user_id BIGINT NOT NULL UNIQUE FK -> Users`, `staff_type VARCHAR(20) NOT NULL CHECK FULL_TIME/PART_TIME`. Chỉ Users.role=STAFF mới có bản ghi Staff (service authorization; ordinary FK không enforce role).

## 3. Staff_Profiles
`profile_id PK`, `staff_id BIGINT NOT NULL UNIQUE FK -> Staff`, `avatar_url NVARCHAR(500) NULL`, `bio NVARCHAR(MAX) NULL`. Không timestamp.

## 4. Services
`service_id PK`, `service_name NVARCHAR(150) NOT NULL`, `description NVARCHAR(MAX) NULL`, `image_url NVARCHAR(500) NULL`, `price DECIMAL(12,2) NOT NULL CHECK price>=0`, `duration_minutes INT NOT NULL CHECK>0`, `status VARCHAR(20) NOT NULL DEFAULT ACTIVE CHECK ACTIVE/INACTIVE`. Một ảnh URL duy nhất.

## 5. Company_Settings
`company_id PK`, `company_address NVARCHAR(500) NOT NULL`, `latitude DECIMAL(10,7) NOT NULL`, `longitude DECIMAL(10,7) NOT NULL`, `service_radius_km DECIMAL(6,2) NOT NULL CHECK >0`. Chỉ 1 row theo nghiệp vụ; enforce qua application/bootstrap hoặc constraint hợp lý, không phát minh company marketplace. CHECK lat between -90/+90, lng -180/+180.

## 6. Company_Operating_Hours
`operating_hour_id PK`, `company_id BIGINT NOT NULL FK`, `effective_from DATE NOT NULL DEFAULT '1900-01-01'`, `day_of_week INT NOT NULL CHECK BETWEEN 1 AND 7` (Mon=1), `period_no SMALLINT NOT NULL DEFAULT 1`, `opening_time TIME(0) NULL`, `closing_time TIME(0) NULL`, `is_closed BIT NOT NULL DEFAULT 0`; `UNIQUE(company_id,effective_from,day_of_week,period_no)`. Mỗi phiên bản có đủ bảy ngày; ngày có thể có nhiều khung không chồng lấn hoặc một dòng nghỉ. Java chặn phút ngoài 00/15/30/45. Các truy vấn Staff/Booking/Admin chọn phiên bản gần nhất không sau ngày được xét.

## 7. Houses
`house_id PK`, `customer_id BIGINT NOT NULL FK -> Users`, `house_name NVARCHAR(100) NOT NULL`, `address NVARCHAR(500) NOT NULL`, `latitude DECIMAL(10,7) NULL`, `longitude DECIMAL(10,7) NULL`, `recipient_name NVARCHAR(150) NOT NULL`, `recipient_phone NVARCHAR(20) NOT NULL`, `province_city NVARCHAR(100) NULL`, `ward_commune NVARCHAR(100) NULL`, `address_detail NVARCHAR(500) NOT NULL`, `address_type VARCHAR(20) NOT NULL CHECK HOME/OFFICE`, `is_default BIT NOT NULL DEFAULT 0`, `address_note NVARCHAR(500) NULL`, `is_deleted BIT NOT NULL DEFAULT 0`. Địa chỉ Customer được xác minh từ VietMap và cần tọa độ khi tạo Booking. `Bookings.house_id` giữ FK sau soft-delete.

## 8. Staff_Availability
`availability_id PK`, `staff_id BIGINT NOT NULL FK -> Staff`, `work_date DATE NOT NULL`, `start_time TIME(0) NOT NULL`, `end_time TIME(0) NOT NULL`, `created_at DATETIME2(0) NOT NULL DEFAULT SYSDATETIME()`, `is_active BIT NOT NULL DEFAULT 1`. CHECK start<end; no updated_at. PART_TIME tạo khoảng trọn trong một khung giờ công ty; ngừng bằng is_active=0, không xóa/sửa lịch sử.

## 9. Staff_Leaves
`leave_id PK`, `staff_id BIGINT NOT NULL FK -> Staff`, `start_date DATE NOT NULL`, `end_date DATE NOT NULL`, `reason NVARCHAR(500) NOT NULL`, `status VARCHAR(20) NOT NULL DEFAULT PENDING CHECK PENDING/APPROVED/REJECTED`, `created_at DATETIME2(0) NOT NULL DEFAULT SYSDATETIME()`, `reviewed_by BIGINT NULL FK -> Users`, `reviewed_at DATETIME2(0) NULL`. CHECK start<=end; PENDING reviewed fields NULL; APPROVED/REJECTED reviewed fields non-NULL.

## 10. Bookings
`booking_id PK`, `customer_id BIGINT NOT NULL FK -> Users`, `staff_id BIGINT NOT NULL FK -> Staff` (**current**), `original_staff_id BIGINT NOT NULL FK -> Staff` (**immutable**), `house_id BIGINT NOT NULL FK -> Houses`, `address_snapshot NVARCHAR(500) NOT NULL`, `latitude_snapshot DECIMAL(10,7) NULL`, `longitude_snapshot DECIMAL(10,7) NULL`, `address_note_snapshot NVARCHAR(500) NULL`, `start_time DATETIME2(0) NOT NULL`, `end_time DATETIME2(0) NOT NULL`, `total_duration_minutes INT NOT NULL`, `total_amount DECIMAL(12,2) NOT NULL`, `area_note NVARCHAR(MAX) NULL`, `status VARCHAR(20) NOT NULL DEFAULT CONFIRMED CHECK CONFIRMED/AWAITING_PAYMENT/COMPLETED/CANCELLED`, `cancelled_at DATETIME2(0) NULL`, `cancelled_by BIGINT NULL FK -> Users`, `cancellation_reason NVARCHAR(500) NULL`, `created_at DATETIME2(0) NOT NULL DEFAULT SYSDATETIME()`, `customer_arrived_at DATETIME2(0) NULL`, `staff_arrived_at DATETIME2(0) NULL`, `customer_delay_reported_at DATETIME2(0) NULL` (V13, owner Customer/Admin only), `incident_reported_at DATETIME2(0) NULL`, `incident_note NVARCHAR(500) NULL`, `work_started_at DATETIME2(0) NULL`, `incident_expected_arrival_at DATETIME2(0) NULL`, `incident_customer_response VARCHAR(20) NULL CHECK ACCEPTED/DECLINED`, `incident_customer_responded_at DATETIME2(0) NULL`, `delay_minutes INT NULL CHECK >=0`, `delay_discount_percent DECIMAL(5,2) NOT NULL DEFAULT 0 CHECK BETWEEN 0 AND 100`, `delay_discount_amount DECIMAL(12,2) NOT NULL DEFAULT 0 CHECK BETWEEN 0 AND total_amount`.
CHECK end_time>start_time, nonnegative totals, valid coordinate range; status CANCELLED means all cancel fields present, others cancel fields NULL. Incident timestamp and note are both NULL or both present and note is nonblank. No updated_at/work_completed_at/history; incident report is a single Booking-level value.

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
`case_id PK`, `feedback_id BIGINT NOT NULL UNIQUE FK -> Feedbacks`, `incident_types NVARCHAR(150) NOT NULL` (comma-separated unique codes CLEANLINESS/PROPERTY_DAMAGE/MISSING_PROPERTY/OTHER; multiple allowed), `customer_description NVARCHAR(MAX) NOT NULL`, `case_status VARCHAR(20) CHECK RECEIVED/INVESTIGATING/VERIFIED/NOT_SUBSTANTIATED/RESOLVED`, `customer_outcome NVARCHAR(MAX) NULL`, `remedy_type VARCHAR(30) NULL`, `remedy_completed BIT NOT NULL DEFAULT 0`, `internal_finding NVARCHAR(MAX) NULL`, latest staff-action summary fields, `reviewed_by BIGINT NULL FK -> Users`, `reviewed_at`, `created_at`, `resolved_at`. Customer-facing outcome is separate from internal finding. Case can resolve only after feedback reply, completed remedy, any required refund/compensation has a settled ledger row, no pending financial entries, and no pending Staff action.

## 17. Feedback_Case_Evidence
`evidence_id PK`, `case_id BIGINT NOT NULL FK -> Feedback_Cases`, `message_id BIGINT NULL FK -> Feedback_Case_Messages` (V16; legacy rows linked to initial Customer message), `image_path NVARCHAR(500) NOT NULL`, `uploaded_by BIGINT NOT NULL FK -> Users`, `uploaded_at DATETIME2(0) NOT NULL DEFAULT SYSDATETIME()`. Append-only JPEG/PNG evidence on conversation messages; access limited to owning Customer and Admin/Manager.

## 18. Staff_Incident_Actions
Append-only decision history: `action_id PK`, `case_id BIGINT NOT NULL FK -> Feedback_Cases`, `staff_id BIGINT NOT NULL FK -> Staff`, `action_type VARCHAR(30) CHECK NONE/COACHING/PROPOSE_WARNING/PROPOSE_FINAL_WARNING/PROPOSE_RECOVERY/OTHER`, `status VARCHAR(20) CHECK PENDING/APPROVED/REJECTED`, `reason NVARCHAR(1000) NOT NULL`, `staff_notice NVARCHAR(1000) NULL`, `proposed_by BIGINT NOT NULL FK -> Users`, `decided_by BIGINT NULL FK -> Users`, `created_at`, `decided_at`, `acknowledged_at`. Non-NONE actions require a Staff-facing notice. Decision must be by the active Manager and another user than the proposer; Staff sees only their approved notice and may acknowledge receipt; no automatic escalation or wage deduction. Internal recovery requires an approved `PROPOSE_RECOVERY` record and separate Manager approval of the financial entry.

## 19. Booking_Financial_Entries
One row per adjustment: `entry_id PK`, `booking_id BIGINT NOT NULL FK -> Bookings`, `case_id BIGINT NULL FK -> Feedback_Cases`, `entry_type VARCHAR(30) CHECK REFUND/PRICE_REDUCTION/REMEDIATION_COST/COMPENSATION/STAFF_RECOVERY`, `amount DECIMAL(12,2) > 0`, `currency CHAR(3) CHECK VND`, `reason NVARCHAR(1000) NOT NULL`, `status VARCHAR(20) CHECK PROPOSED/PENDING_APPROVAL/APPROVED/REJECTED/PAID/CANCELLED/SIMULATED`, `payment_method`, `transaction_reference`, `receipt_path`, `is_simulated`, creator/approver/settler/canceller IDs and timestamps/reasons. Admin manager other than creator approves. PAID/SIMULATED require receipt, method and settler. Only PAID, non-simulated records contribute to real cash-flow totals; SIMULATED is reported separately.

## 20. Booking_Delay_Compensation_Tiers
`tier_id PK`, `min_delay_minutes INT NOT NULL CHECK >=1`, `max_delay_minutes INT NULL CHECK NULL OR >= min_delay_minutes`, `discount_percent INT NOT NULL CHECK 0..100`. Unique `min_delay_minutes`; at most one row may have `max_delay_minutes IS NULL`. Service validates that ranges start at minute 1, are contiguous and non-overlapping, and end in exactly one unbounded tier. V14 seeds 1–15/5%, 16–30/10%, 31+/20%. Admin edits the current policy; Booking rows retain their calculated discount values.

## 21. Feedback_Case_Messages
`message_id PK`, `case_id BIGINT NOT NULL FK -> Feedback_Cases`, `sender_user_id BIGINT NULL FK -> Users` (NULL only for imported legacy Admin replies), `sender_role VARCHAR(20) NOT NULL CHECK CUSTOMER/ADMIN`, `message_text NVARCHAR(MAX) NULL`, `sent_at DATETIME2(0) NOT NULL DEFAULT SYSDATETIME()`, `legacy_imported BIT NOT NULL DEFAULT 0`, `legacy_timestamp_unknown BIT NOT NULL DEFAULT 0`. New messages contain text up to 4,000 characters and/or evidence attached through `Feedback_Case_Evidence.message_id`, with at most five JPEG/PNG images of 5 MB each per request. Legacy Admin reply has unknown original time and must be labeled as imported, not shown as its migration timestamp. Messages are chronological and append-only; resolved cases are read-only. Customer sees proposal amounts and states (pending approval, approved pending execution, settled, simulated); internal findings and Staff actions remain Admin/Manager only. Manager can approve/reject an entry here only when a different Admin proposed it.

## Referential integrity & SQL Server notes
- For FK child tables `Booking_Details`, `Booking_Images`, `Payments`, `Feedbacks`, `Booking_Financial_Entries` -> Bookings and case messages/evidence/actions -> parents: `ON DELETE NO ACTION` (SQL Server analog of RESTRICT). Also preserve history on Houses/Staff/Services via appropriate FK protection, no cascade that deletes booking history.
- SQL Server **multiple cascade paths** risk: prefer NO ACTION/restrict where appropriate, avoid delete cascades by default.
- Idempotent service-layer validation: check DB locks/transactions for staff slot collision and availability mutation; use explicit transaction boundaries.
- MySQL `AUTO_INCREMENT`, backticks, `BOOLEAN`, `CURRENT_TIMESTAMP`, `LIMIT`, `ON DELETE RESTRICT` should not be copied verbatim to SQL Server.
- Hibernate `ddl-auto=validate`; Flyway applies schema. Fresh schema migration path must be separated from existing production schema. Do **not** modify existing applied versioned Flyway migrations in-place on a shared DB.
- Flyway's `flyway_schema_history` is a tooling table, not a business Entity.
- The entity mapping and migration must match column types and nullability. Do not silently add auditing, gateway IDs, or admin ID just to make a vendor SDK easier.

## Concurrency focus
Serialize booking creation on same Staff via reliable row lock/transaction/isolation or equivalently proven mechanism. Serialize Payment success callbacks/confirmation on same Booking and enforce filtered unique success. Explicitly test SQL Server behavior; 'query then insert' without a lock is unsafe.
