> **Document:** Software Requirements Specification\
> **File:** `docs/requirements/SRS.md`\
> **Version:** v4.0.0\
> **Created:** 2026-06-14\
> **Last Updated:** 2026-11-08\
> **Status:** Active\
# SRS — SOFTWARE REQUIREMENTS SPECIFICATION


## 1. System boundary
One server-rendered Java Spring MVC web application, single company, SQL Server. Actors CUSTOMER, STAFF, ADMIN; all authentication via `Users` and session. Backend enforces role/ownership on every protected operation. View rendering is not a security boundary.

## 2. Role & authority matrix
| Chức năng | Customer | Staff | Admin |
|---|---:|---:|---:|
| Tài khoản/đăng nhập | Self | Self | Self |
| Quản lý Houses | Own | — | — |
| Xem danh mục/danh sách Staff | Yes | As needed | Yes |
| Tạo Booking | Own | — | — |
| Hủy >=2h | Own | — | — |
| Hủy muộn/đổi Staff | — | — | Yes |
| Availability PART_TIME | — | Own | xử lý ngoại lệ xung đột |
| Đơn nghỉ | — | Own | duyệt/từ chối |
| Upload Booking image, xác nhận work | — | Assigned | — |
| Xác nhận CASH | — | Assigned | — |
| ONLINE Payment | Own | — | Quan sát/vận hành |
| Feedback | Own & Completed | — | Reply/resolve |
| Company config, Staff status, Services | — | — | Yes |

## 3. Functional requirements và acceptance

### FR-AUTH — Authentication and roles
- FR-AUTH-01: `Users` là bảng xác thực duy nhất. Username/email unique, password_hash, status ACTIVE/INACTIVE, role CUSTOMER/STAFF/ADMIN. Staff 1:1 với Users có role STAFF.
- FR-AUTH-02: Spring Security HTTP Session và role-based access; ngăn User inactive; kiểm tra resource ownership và Staff assignment.
- AC-AUTH-01: Login 3 roles qua một trang, password sai/inactive denied; Customer A cannot operate Customer B's House/Booking/Feedback.
- AC-AUTH-02: Không tồn tại cần thiết các bảng Customer, Admin, Accounts, Roles riêng trong schema mới.

### FR-SVC — Services
- Admin xem/tạo/sửa/đổi ACTIVE/INACTIVE Service với name, description, optional image_url, price>=0, duration>0.
- Customer chỉ chọn Service ACTIVE; snapshot cũ không đổi khi Admin sửa/deactivate Service.
- AC-SVC-01: Không tạo Booking mới với Service INACTIVE; Booking trước đó vẫn hiển thị service snapshot/price/duration.

### FR-PROFILE/FOLLOW — Staff Profile and follows
- Staff tự chỉnh avatar_url, bio; Admin không duyệt/chỉnh profile.
- Customer follow/unfollow một Staff, unique pair; unfollow xóa relation.
- AC-PROFILE-01: STAFF chỉ sửa profile của mình; Customer không thể mutate profile.
- AC-FOLLOW-01: Follow trùng không có dòng lặp; unfollow mất dòng.

### FR-HOUSE — Houses and VietMap addresses
- Customer quản lý nhiều House gồm người nhận, số điện thoại, địa chỉ VietMap, loại địa chỉ, tùy chọn mặc định và optional address_note. Customer chọn suggestion có ref_id; backend tra tọa độ qua Place API, key chỉ ở server.
- Mỗi Booking dùng đúng một House thuộc Customer; lưu snapshot địa chỉ và tọa độ. Booking yêu cầu có tọa độ và nằm trong bán kính đường chim bay từ vị trí cơ sở do Admin cấu hình; mặc định khởi điểm 10 km.
- Soft delete House `is_deleted=1` chỉ khi không có Booking CONFIRMED hoặc AWAITING_PAYMENT; giữ FK/snapshots lịch sử; House deleted không thể được chọn trong Booking mới.
- AC-HOUSE-01: Không thể đặt House không thuộc Customer, bị soft-delete, thiếu tọa độ VietMap hoặc nằm ngoài bán kính.
- AC-HOUSE-02: Soft delete với Booking chưa xong bị từ chối; COMPLETED/CANCELLED không ngăn xóa mềm; lịch sử giữ nguyên.
- AC-HOUSE-03: Sửa House không sửa Booking address snapshots.
- AC-HOUSE-04: Mỗi Customer có tối đa một House đang hoạt động được đặt làm mặc định; địa chỉ cũ được backfill người nhận/điện thoại và giữ nguyên chuỗi địa chỉ.

### FR-CONFIG — Company settings/opening hours
- Một Company_Settings bản ghi, bán kính dương và một tâm tọa độ; mỗi thứ (Mon=1..Sun=7) có một hoặc nhiều khung không chồng lấn. Ngày đóng cửa có một dòng closed với giờ NULL.
- Lịch tuần có ngày hiệu lực. Lịch nền hiện tại được backfill từ 1900-01-01; phiên bản áp dụng cho ngày D là phiên bản mới nhất có ngày hiệu lực không sau D. Admin có thể tạo nhiều phiên bản tương lai, xem lịch quá khứ ở chế độ chỉ đọc; ngày của phiên bản đã lưu không đổi. Có thể sửa lịch hôm nay hoặc sửa/hủy lịch tương lai.
- Khi tạo/sửa/hủy phiên bản, chặn nếu khoảng ngày phiên bản chi phối có Booking `CONFIRMED` hoặc `AWAITING_PAYMENT`, kể cả khi giờ mới vẫn chứa booking. Booking `CANCELLED` không khóa. Hủy phiên bản tương lai không tự kích hoạt lại availability đã inactive.
- Admin lưu địa điểm/bán kính riêng với các phiên bản giờ. Khi thay đổi giờ hiệu lực, availability Staff không còn nằm trọn trong giờ mới được inactive và giữ lịch sử.
- Mọi giờ bắt đầu/kết thúc theo lưới 15 phút (:00/:15/:30/:45); server kiểm tra lại.
- Tọa độ tâm cơ sở do Admin lưu cùng địa chỉ; bán kính dịch vụ mặc định 10 km, Admin có thể điều chỉnh. Đổi tâm/bán kính mà booking tương lai mất điều kiện phục vụ thì chặn lưu đến khi Admin xử lý.
- Nếu đổi khung giờ hoặc bán kính khiến Booking CONFIRMED tương lai mất hợp lệ, Admin phải đổi nhân viên/hủy booking trước khi lưu. Availability ngoài giờ mới được giữ lịch sử nhưng inactive.
- AC-CONFIG-01: Booking phải bắt đầu và kết thúc trong cùng một khung hợp lệ; không nhận khoảng bắc qua giờ nghỉ.
- AC-CONFIG-02: Staff, Customer booking và Admin reassignment cùng tra phiên bản lịch theo ngày; một phiên bản mới chỉ thay lịch từ ngày hiệu lực trở đi.

### FR-AVAIL — Staff availability
- FULL_TIME available mặc định khi công ty mở, không phải đăng ký lịch riêng.
- PART_TIME tạo interval `[start_time,end_time)` nằm trọn trong đúng một khung Admin của ngày; mọi đầu mốc 15 phút, các khoảng không chồng nhau. Booking phải nằm trọn trong khoảng đang active. FULL_TIME theo khung công ty.
- Không sửa/xóa cứng availability: Staff chỉ deactivate. Chặn deactivate nếu khoảng chứa Booking CONFIRMED tương lai; Admin đổi Staff/hủy trước.
- AC-AVAIL-01: Availability ngoài giờ công ty, lệch 15 phút, chồng lấn hoặc Booking vượt khỏi availability bị từ chối.
- AC-AVAIL-02: Deactivate chỉ đổi `is_active`; lịch sử vẫn còn và các Booking đã xử lý được bảo toàn.

### FR-LEAVE — Leave request/review
- Staff submit start_date/end_date, reason, status PENDING. Admin APPROVE/REJECT, store reviewer/date. Full-day only.
- Admin chỉ duyệt nghỉ khi không còn Booking CONFIRMED tương lai thuộc các ngày xin nghỉ; Admin chủ động đổi Staff hoặc hủy trước. Không tự động xử lý booking.
- AC-LEAVE-01: PENDING leave không block booking; APPROVED leave block booking mới; approve bị chặn và liệt kê Booking CONFIRMED tương lai cần xử lý; start_date>end_date invalid.
- AC-LEAVE-02: Staff khác không duyệt/đọc trái quyền; review timestamps/reviewer consistent.

### FR-BOOK — Create Booking
- Inputs: Customer, own House, chosen ACTIVE Staff, >=1 ACTIVE Services, proposed start, optional area_note. One booking form may include several service dates; each date has its own selected Staff and start time and persists as an independent Booking.
- Availability calendar only offers dates with at least one Staff and 15-minute start that can fit all selected Services inside Admin hours and the Staff's active interval (for PART_TIME), after leave, existing-booking and 120-minute lead-time checks. When Customer filters by Staff, dates are limited to that Staff's eligible schedule.
- Validate start alignment to 15-minute slots; now+>=120 minutes; start<=now+30 days; full interval inside a single company window; House within radius; Staff not on approved leave; type-specific availability.
- Giờ Admin cấu hình và giờ Staff đăng ký là thời điểm nhân viên có mặt tại nhà khách, sẵn sàng bắt đầu phục vụ. Booking đầu ngày đi từ tâm công ty; hệ thống lấy ETA tuyến xe máy VietMap cơ sở + 15 phút để ước tính giờ xuất phát lùi từ giờ booking và truyền thời điểm đó vào Route v4. Giữa hai booking trong cùng ngày, khoảng trống tối thiểu là ETA tuyến VietMap từ địa chỉ booking trước đến địa chỉ sau + 15 phút dự phòng (ví dụ ETA 20 phút cần 35 phút). Quãng đường đường bộ VietMap giữa hai booking cùng Staff không được vượt `serviceRadiusKm` Admin đang cấu hình; đúng bằng ngưỡng thì chấp nhận. Quy tắc này chỉ áp dụng cho chặng giữa booking, không cộng phí; bán kính nhà quanh tâm công ty vẫn được kiểm tra riêng. Booking cùng Staff không được chồng giờ. Thời điểm khởi hành gửi VietMap chỉ hỗ trợ tránh khu vực hạn chế theo giờ, ETA không phải dự báo kẹt xe tương lai. API lỗi/không có tuyến từ chối booking. Nếu dự kiến trễ, Staff báo Admin sớm để Admin liên hệ khách; không tự đổi/hủy lịch hoặc áp dụng quy tắc hoàn tiền mới.
- Khi Customer tra giờ, availability-options bắt buộc nhận `houseId` và xác minh House thuộc Customer đang đăng nhập. Các mốc chồng giờ bị ẩn. Khi kiểm tra một mốc cụ thể, nếu thiếu thời gian di chuyển trước/sau thì loại mốc đó và nêu booking liên quan, khoảng trống hiện có, ETA, 15 phút dự phòng, thời điểm đủ điều kiện sớm nhất hoặc giờ bắt đầu muộn nhất. Lưới chọn vẫn theo 15 phút: đủ điều kiện từ 10:35 thì mốc đầu tiên có thể chọn là 10:45. Lỗi VietMap phải được phân biệt với thiếu thời gian di chuyển. Backend lặp lại cùng kiểm tra khi lưu; lỗi từng ngày giữ nguyên dịch vụ, House, ghi chú, giờ và Staff để Customer chỉnh sửa.
- On success snapshot House address/lat/lng/address_note and per-Service name/unit price/duration; total_amount=sum unit prices, total_duration_minutes=sum durations, end=start+duration; `original_staff_id=staff_id`; status CONFIRMED.
- One transaction with reliable concurrency coordination for parallel requests for same Staff. Do not use an unchecked `existsByOverlap` followed by insert alone.
- Multi-date submission validates/persists each date independently. A date that fails availability or route validation is reported; other valid dates may still be created. The UI must show which dates need correction/removal and the count of successfully created bookings.
- AC-BOOK-01: Valid Booking persists header + >=1 detail atomically with correct totals, duration, snapshots, status, staff references.
- AC-BOOK-02: Lead time <120min rejects, >=120min accepts when aligned to :00/:15/:30/:45; beyond 30d rejects.
- AC-BOOK-03: Different Staff can be booked at same time; same Staff is checked against route-aware gap.
- AC-BOOK-04: End outside current company/Staff interval rejects.
- AC-BOOK-05: Concurrent requests for same Staff/slot: at most one confirmed successful; no partial write.

### FR-CANCEL/REASSIGN — Cancellation and admin assignment
- Customer self-cancel only when `now <= start_time-120min`; Admin handles later requests. Persist CANCELLED status + cancelled_by/at/reason; not physical delete.
- Confirmed Booking House/time/services changes require cancel+rebook.
- Admin can replace assigned staff (`staff_id`), preserve immutable `original_staff_id`; verify replacement Staff's eligibility with the relevant rules. No separate reassignment history.
- AC-CANCEL-01: Exactly -120m allows customer cancellation; -119m rejects; cancellation leaves snapshots/details intact.
- AC-REASSIGN-01: Admin reassignment changes current staff, not original staff, and cannot create conflicting active booking.

### FR-ARRIVAL — Customer arrival record
- Customer owning a CONFIRMED Booking may independently record Staff arrival at or after scheduled start. This timestamp does not change Booking status; assigned Staff completion remains separate.
- AC-ARRIVAL-01: Other Customers, pre-start, non-CONFIRMED and duplicate confirmations are rejected.
- Note: status-specific cancellation eligibility after work complete/payment is **TBD**; do not create new refund semantics.

### FR-JOB — Work execution/evidence
- Authorized assigned Staff upload multiple image evidence records tied to **Booking**, not Booking_Detail; image_url, uploaded_at. Staff confirms job completion without Admin approval.
- On valid work completion Booking CONFIRMED -> AWAITING_PAYMENT. No work_completed_at field.
- AC-JOB-01: Staff A cannot upload image or confirm Staff B's Booking; storage path safe and file validation sane.
- AC-JOB-02: Successful work confirmation yields AWAITING_PAYMENT, not COMPLETED. Missing image policy beyond 'can upload many' is TBD; do not invent per-detail 1..5 requirement.

### FR-PAY — Payments
- Payment only after work completion; method CASH or ONLINE; Payment records represent attempts, Booking 1:N Payments, at most 1 SUCCESS. Amount matches booking total. Status PENDING/SUCCESS/FAILED; created_at/paid_at rules.
- CASH: assigned Staff confirms physically received money; ONLINE: Backend verifies true gateway result, including integrity/authenticity, amount/order binding, replay/idempotency, and concurrency. Browser redirect alone never marks payment success.
- If receipt verified and job completed, Payment SUCCESS and Booking COMPLETED atomically. Failed attempt does not complete Booking. Existing PayOS implementation may be retained via correct mapping of PayOS -> ONLINE, no DB `payment_gateway` column.
- AC-PAY-01: Create attempts safely; only one SUCCESS per Booking even with concurrent callbacks/requests.
- AC-PAY-02: Staff assigned can confirm CASH; ADMIN cannot replace this rule with manual Admin verification.
- AC-PAY-03: Valid verified ONLINE SUCCESS marks Booking COMPLETED; tampered/failed/duplicate webhook cannot incorrectly mark completed.
- AC-PAY-04: Booking status remains AWAITING_PAYMENT for no verified successful payment.

### FR-FEED — Feedback
- Customer can write content text (no rating) once for own COMPLETED Booking. Admin can reply in `admin_reply` and mark OPEN/RESOLVED.
- AC-FEED-01: No `rating` field anywhere in new model/UI; unique booking feedback; wrong customer/uncompleted denied; admin reply/resolve works.

## 4. Data constraints
- Approved model exactly 15 business tables (see `04_SQL_SERVER_DATA_DICTIONARY.md`). Avoid technical tables masquerading as business tables. Flyway schema history is tooling metadata, not business table.
- Reassignable staff current/original references; soft-delete House; preserved Booking history; no unnecessary created_at/updated_at fields beyond approved dictionary.
- Enforce authorization, role-FK matching, statuses, totals, uniqueness, radius, time, concurrency, and state transitions in appropriate DB and service layers. Handle failure atomically.

## 5. Non-functional quality (implementation expectations, not invented SLA)
- App must compile and run on supported JDK/SQL Server, avoid secrets in git, protect authorization and CSRF, validate uploads, handle provider faults without false success, show understandable errors, support transaction-safe concurrent booking/payment.
- Unit tests for business decisions; integration tests with SQL Server/Testcontainers if runtime available; MVC/security tests for ownership/status. Report checks exactly and do not claim SLA numbers or performance targets unapproved by user.

## 6. Explicit open points
- ARCH-03 live-data strategy, payment refunds/cancel-after-success, exact PayOS callback persistence needs while DB has only transaction_code, time zone/currency policy, failure timeout, detailed image constraints, cross-midnight, feedback editing, admin booking reassignment eligibility details. Technical defaults must be documented as implementation choices, not approved BR.
