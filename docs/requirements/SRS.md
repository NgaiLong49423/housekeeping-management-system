> **Document:** Software Requirements Specification\
> **File:** `docs/requirements/SRS.md`\
> **Version:** v2.0.0\
> **Created:** 2026-06-14\
> **Last Updated:** 2026-10-24\
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

### FR-HOUSE — Houses and manually entered addresses
- Customer quản lý nhiều House gồm người nhận, số điện thoại, tỉnh/thành, phường/xã, địa chỉ chi tiết, loại địa chỉ, tùy chọn mặc định và optional address_note; không tích hợp Google Maps và không yêu cầu tọa độ.
- Mỗi Booking dùng đúng một House thuộc Customer; lưu snapshot địa chỉ văn bản. Tọa độ House/Booking có thể NULL và không được dùng để từ chối booking.
- Soft delete House `is_deleted=1` chỉ khi không có Booking CONFIRMED hoặc AWAITING_PAYMENT; giữ FK/snapshots lịch sử; House deleted không thể được chọn trong Booking mới.
- AC-HOUSE-01: Không thể đặt House không thuộc Customer hoặc bị soft-delete; hệ thống không kiểm tra bán kính theo tọa độ.
- AC-HOUSE-02: Soft delete với Booking chưa xong bị từ chối; COMPLETED/CANCELLED không ngăn xóa mềm; lịch sử giữ nguyên.
- AC-HOUSE-03: Sửa House không sửa Booking address snapshots.
- AC-HOUSE-04: Mỗi Customer có tối đa một House đang hoạt động được đặt làm mặc định; địa chỉ cũ được backfill người nhận/điện thoại và giữ nguyên chuỗi địa chỉ.

### FR-CONFIG — Company settings/opening hours
- Một Company_Settings bản ghi; 7 dòng Company_Operating_Hours (Mon=1..Sun=7), closed day NULL times; open day opening<closing.
- AC-CONFIG-01: Đặt Booking start vào ngày đóng cửa hoặc ngoài `[opening,closing)` bị từ chối; end > closing hợp lệ, không surcharge.

### FR-AVAIL — Staff availability
- FULL_TIME available mặc định khi công ty mở, không phải đăng ký lịch riêng.
- PART_TIME tự tạo các interval `[start_time,end_time)` trên `work_date`, nhiều non-overlap intervals; không có entry thì không available. Start của Booking thuộc ít nhất một interval; end có thể ngoài interval.
- Không cho PART_TIME tự đổi/xóa availability ảnh hưởng Booking confirmed; Admin giải quyết.
- AC-AVAIL-01: PART_TIME không có interval trong ngày => reject Booking; start trong 1 interval dù end ra ngoài => accepted nếu các rule khác đúng.
- AC-AVAIL-02: Two PART_TIME slots overlapping cùng ngày bị reject; sửa/delete impact CONFIRMED bị từ chối.

### FR-LEAVE — Leave request/review
- Staff submit start_date/end_date, reason, status PENDING. Admin APPROVE/REJECT, store reviewer/date. Full-day only.
- Approved leave rejects new bookings starting on leave date; Admin resolves confirmed booking conflict (no automatic cancel/reassign algorithm assumed).
- AC-LEAVE-01: PENDING leave không tự động block booking; APPROVED leave block booking mới; start_date>end_date invalid.
- AC-LEAVE-02: Staff khác không duyệt/đọc trái quyền; review timestamps/reviewer consistent.

### FR-BOOK — Create Booking
- Inputs: Customer, own House, chosen ACTIVE Staff, >=1 ACTIVE Services, proposed start, optional area_note.
- Validate start alignment to 30-minute slots; now+>=120 minutes; start<=now+30 days; start inside company hours; House within radius; Staff not on approved leave; type-specific availability; check any overlapping Bookings and >=60m buffer for same Staff.
- Interval collision policy `[a,b)` and 60m buffer in both directions; Bookings occupying staff are CONFIRMED and AWAITING_PAYMENT unless an approved alternative explicitly documented; COMPLETED historic appointment occupancy should be evaluated relative to scheduled interval to prevent retroactive double booking (implementation policy must not silently loosen known 60m rule).
- On success snapshot House address/lat/lng/address_note and per-Service name/unit price/duration; total_amount=sum unit prices, total_duration_minutes=sum durations, end=start+duration; `original_staff_id=staff_id`; status CONFIRMED.
- One transaction with reliable concurrency coordination for parallel requests for same Staff. Do not use an unchecked `existsByOverlap` followed by insert alone.
- AC-BOOK-01: Valid Booking persists header + >=1 detail atomically with correct totals, duration, snapshots, status, staff references.
- AC-BOOK-02: Lead time 119min reject, 120min accept; beyond 30d reject; non-30min slot reject.
- AC-BOOK-03: Different Staff can be booked same time at same House; same Staff conflicts/59min gap reject, 60min gap accepted.
- AC-BOOK-04: End after company close and after PART_TIME slot end accepted (start conditions still required).
- AC-BOOK-05: Concurrent requests for same Staff/slot: at most one confirmed successful; no partial write.

### FR-CANCEL/REASSIGN — Cancellation and admin assignment
- Customer self-cancel only when `now <= start_time-120min`; Admin handles later requests. Persist CANCELLED status + cancelled_by/at/reason; not physical delete.
- Confirmed Booking House/time/services changes require cancel+rebook.
- Admin can replace assigned staff (`staff_id`), preserve immutable `original_staff_id`; verify replacement Staff's eligibility with the relevant rules. No separate reassignment history.
- AC-CANCEL-01: Exactly -120m allows customer cancellation; -119m rejects; cancellation leaves snapshots/details intact.
- AC-REASSIGN-01: Admin reassignment changes current staff, not original staff, and cannot create conflicting active booking.
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
