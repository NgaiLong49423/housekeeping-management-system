> **Document:** Software Requirements Specification\
> **File:** `docs/requirements/SRS.md`\
> **Version:** v7.2.0\
> **Created:** 2026-06-14\
> **Last Updated:** 2026-11-13\
> **Status:** Active\
# SRS — SOFTWARE REQUIREMENTS SPECIFICATION


## 1. System boundary
One server-rendered Java Spring MVC web application, single company, SQL Server. Actors CUSTOMER, STAFF, ADMIN and one MANAGER; all authentication via `Users` and session. Manager inherits all Admin operation permissions and exclusively manages Admin accounts and approvals. Backend enforces role/ownership on every protected operation. View rendering is not a security boundary.

## 2. Role & authority matrix
| Chức năng | Customer | Staff | Admin | Manager |
|---|---:|---:|---:|---:|
| Tài khoản/đăng nhập | Self | Self | Self | Self |
| Quản lý Houses | Own | — | — | — |
| Xem danh mục/danh sách Staff | Yes | As needed | Yes | Yes |
| Tạo Booking | Own | — | — | — |
| Hủy >=2h | Own | — | — | — |
| Hủy muộn/đổi Staff | — | — | Yes | Yes |
| Availability PART_TIME | — | Own | xử lý ngoại lệ xung đột | xử lý ngoại lệ xung đột |
| Đơn nghỉ | — | Own | duyệt/từ chối | duyệt/từ chối |
| Upload Booking image, xác nhận work | — | Assigned | — | — |
| Xác nhận CASH | — | Assigned | — | — |
| ONLINE Payment | Own | — | Quan sát/vận hành | Quan sát/vận hành |
| Feedback | Own & Completed; xem/sửa trước Admin reply | — | Xem, reply/cập nhật reply, resolve sau reply | Như Admin |
| Hồ sơ sự cố/bằng chứng | Own; bổ sung bằng chứng và xác nhận kết quả | Chỉ nhận thông báo quyết định nội bộ của mình; không xem hồ sơ/feedback/bằng chứng | Tiếp nhận, xác minh, trao đổi, đề xuất bồi thường và ghi quyết toán | Xem hồ sơ, duyệt khoản bồi thường đã được Customer đồng ý và quyết định nội bộ Staff |
| Sổ tài chính sự cố | Xem kết quả/biện pháp của mình | — | Lập khoản; ghi quyết toán/chứng từ | Như Admin; duyệt khoản khác người lập |
| Company config, Staff status, Services | — | — | Yes | Yes |
| Tạo/khóa Admin | — | — | — | Yes; không thể tạo Manager khác |

## 3. Functional requirements và acceptance

### FR-AUTH — Authentication and roles
- FR-AUTH-01: `Users` là bảng xác thực duy nhất. Username/email unique, password_hash, status ACTIVE/INACTIVE, role CUSTOMER/STAFF/ADMIN/MANAGER. Chỉ có tối đa một Manager; Manager có toàn bộ quyền Admin và là vai trò duy nhất tạo/khóa Admin. Staff 1:1 với Users có role STAFF.
- FR-AUTH-02: Spring Security HTTP Session và role-based access; ngăn User inactive; kiểm tra resource ownership và Staff assignment.
- FR-AUTH-03: Tài khoản Staff do Admin tạo và Admin do Manager tạo có `must_change_password=true`; lần đăng nhập đầu tiên chỉ được mở trang đổi mật khẩu, thực hiện đổi mật khẩu hoặc đăng xuất. Tài khoản hiện hữu, Customer và Manager không thuộc hai luồng tạo này không bị buộc đổi. Đổi mật khẩu yêu cầu mật khẩu hiện tại, mật khẩu mới tối thiểu 8 ký tự/tối đa 72 byte UTF-8 và xác nhận trùng khớp; mật khẩu mới phải khác mật khẩu cũ. Cập nhật hash và tắt cờ nguyên tử.
- AC-AUTH-01: Login các role qua một trang, password sai/inactive denied; Customer A cannot operate Customer B's House/Booking/Feedback; Manager vào được toàn bộ chức năng Admin, Admin không truy cập được quản trị tài khoản.
- AC-AUTH-02: Không tồn tại cần thiết các bảng Customer, Admin, Accounts, Roles riêng trong schema mới.
- AC-AUTH-03: Staff/Admin mới không thể truy cập URL hoặc thao tác nghiệp vụ trước khi đổi mật khẩu; sai mật khẩu hiện tại, xác nhận không khớp hoặc mật khẩu không hợp lệ không đổi hash/cờ. Đổi thành công duy trì session, cho phép vào portal theo role; lần đăng nhập tiếp theo không yêu cầu đổi lại. Tài khoản hiện hữu và Customer giữ nguyên hành vi.

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
- Customer quản lý nhiều House gồm người nhận, điện thoại, địa chỉ văn bản, loại, mặc định và optional address_note. Backend tra ngầm VietMap để lấy tọa độ định tuyến; Customer có thể nhập địa chỉ dạng văn bản mà không nhập tọa độ hoặc chọn suggestion.
- Mỗi Booking dùng một House thuộc Customer và lưu snapshot địa chỉ/tọa độ nếu tra được. Không áp dụng giới hạn bán kính từ tâm cơ sở.
- Soft delete House `is_deleted=1` chỉ khi không có Booking CONFIRMED hoặc AWAITING_PAYMENT; giữ FK/snapshots lịch sử; House deleted không thể được chọn trong Booking mới.
- AC-HOUSE-01: Không thể đặt House không thuộc Customer hoặc bị soft-delete; tuyến không xác định được thì slot không khả dụng và nêu lý do.
- AC-HOUSE-02: Soft delete với Booking chưa xong bị từ chối; COMPLETED/CANCELLED không ngăn xóa mềm; lịch sử giữ nguyên.
- AC-HOUSE-03: Sửa House không sửa Booking address snapshots.
- AC-HOUSE-04: Mỗi Customer có tối đa một House đang hoạt động được đặt làm mặc định; địa chỉ cũ được backfill người nhận/điện thoại và giữ nguyên chuỗi địa chỉ.

### FR-CONFIG — Company settings/opening hours
- Một Company_Settings bản ghi, bán kính dương và một tâm tọa độ; mỗi thứ (Mon=1..Sun=7) có một hoặc nhiều khung không chồng lấn. Ngày đóng cửa có một dòng closed với giờ NULL.
- Lịch tuần có ngày hiệu lực. Lịch nền hiện tại được backfill từ 1900-01-01; phiên bản áp dụng cho ngày D là phiên bản mới nhất có ngày hiệu lực không sau D. Admin có thể tạo nhiều phiên bản tương lai, xem lịch quá khứ ở chế độ chỉ đọc; ngày của phiên bản đã lưu không đổi. Có thể sửa lịch hôm nay hoặc sửa/hủy lịch tương lai.
- Khi tạo/sửa/hủy phiên bản, chặn nếu khoảng ngày phiên bản chi phối có Booking `CONFIRMED` hoặc `AWAITING_PAYMENT`, kể cả khi giờ mới vẫn chứa booking. Booking `CANCELLED` không khóa. Hủy phiên bản tương lai không tự kích hoạt lại availability đã inactive.
- Admin lưu địa điểm/bán kính riêng với các phiên bản giờ. Khi thay đổi giờ hiệu lực, availability Staff không còn nằm trọn trong giờ mới được inactive và giữ lịch sử.
- Giờ bắt đầu Booking theo lưới 30 phút (:00/:30); server kiểm tra lại.
- Tâm/bán kính vẫn lưu để tương thích, không dùng chặn Booking hay reassignment.
- Nếu đổi khung giờ khiến Booking CONFIRMED tương lai mất hợp lệ, Admin phải đổi nhân viên/hủy booking trước khi lưu. Availability ngoài giờ mới được giữ lịch sử nhưng inactive.
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
- Inputs: Customer, own House, chosen ACTIVE Staff, >=1 ACTIVE Services, proposed start, optional area_note. A multi-date form persists each date as an independent Booking.
- Availability offers only 30-minute start-grid options that fit services within one company window and Staff availability, leave, existing appointments and 120-minute lead time. Customer enters a textual address; backend resolves it through VietMap without asking for coordinates.
- Validate start alignment to :00/:30; now+>=120 minutes; start<=now+30 days; full interval inside one company window; Staff not on approved leave; type-specific availability. No service-radius restriction and no company-center-to-first-booking route.
- Between adjacent same-Staff Bookings require VietMap route ETA + 15 minutes. Same address always requires 15 minutes. If route ETA cannot be resolved, reject that slot with the reason. Apply only between adjacent Bookings.
- Backend repeats all checks at save; provider failure/no route blocks that slot. Multi-date submissions can independently succeed/fail.
- On success snapshot House text/coordinates if resolved and each Service details; totals are sums; `original_staff_id=staff_id`; status CONFIRMED.
- Coordinate concurrent same-Staff requests transactionally; unchecked overlap query followed by insert is insufficient.
- AC-BOOK-01: Valid Booking persists header/details atomically with totals, duration and snapshots.
- AC-BOOK-02: Lead time <120min rejects; >=120min accepts only on :00/:30; beyond 30 days rejects.
- AC-BOOK-03: Adjacent same-Staff gap is ETA+15min, same-address gap is 15min; no first-leg/radius restriction; unavailable ETA blocks slot.
- AC-BOOK-04: End outside company/Staff interval rejects.
- AC-BOOK-05: Concurrent requests for same Staff/slot create at most one Booking, without partial writes.
### FR-CANCEL/REASSIGN — Cancellation and admin assignment
- Customer self-cancel under the ordinary rule only when `now <= start_time-120min` and work has not started. The Customer may also cancel free, regardless of the 2-hour cutoff, after declining a concrete ETA or after the Staff misses an ETA that Customer accepted, provided work has not started. A Staff delay report with no Customer response and a Customer no-show/incorrect-arrival report do not create free-cancellation rights. Persist CANCELLED status + cancelled_by/at/reason; not physical delete.
- Confirmed Booking House/time/services changes require cancel+rebook.
- Admin can replace assigned staff (`staff_id`), preserve immutable `original_staff_id`; verify replacement Staff's eligibility with the relevant rules. No separate reassignment history.
- AC-CANCEL-01: Exactly -120m allows ordinary cancellation; -119m rejects. ETA refusal or missed accepted ETA allows free cancellation before work starts regardless of the cutoff; no-show report alone does not. Cancellation leaves snapshots/details intact.
- AC-REASSIGN-01: Admin reassignment changes current staff, not original staff, and cannot create conflicting active booking.

### FR-ARRIVAL — Arrival claim, Customer report and verification
- Staff self-claim `staff_arrived_at`, Customer confirmation and Admin verification are separate facts. Assigned Staff must self-claim arrival before work starts; Customer/Admin confirmation cannot replace this prerequisite. Staff self-claim is the default time used for delay discount, not proof of physical location.
- From scheduled start while Booking is CONFIRMED and service is not completed, owning Customer can report Staff absent/arrival time incorrect once, including after Staff self-claimed arrival or sent an ETA. Persist the report time without changing Booking status or money.
- Customer confirmation only records acknowledgement of the Staff claim. Customer silence leaves the Staff time as the discount basis. An incorrect-time report does not block start/completion, cancel the Booking or change money. Keep report time separate from Staff ETA proposals.
- Admin may correct the discount time only after an incorrect-time report and before service completion/payment. Preserve Staff's claimed time and record Admin-adjusted time, Admin ID, verification timestamp and required note. Recalculate and persist the discount from the corrected time. After completion/payment, reject correction.
- AC-ARRIVAL-01: Enforce ownership, CONFIRMED, at/after start, one report, and service not completed; allow report despite Staff arrival claim, ETA, prior Customer acknowledgement, or work start. Report has no state/money side effect.
- AC-ARRIVAL-02: Staff's own arrival claim is required for start. Customer acknowledgment and Admin verification do not replace it. Customer report does not block start or completion.
- AC-ARRIVAL-03: Customer confirmation only stores acknowledgement; customer silence retains Staff time as discount basis. Admin correction requires a report, records the audit fields, works before completion/payment and is rejected afterward.

### FR-INCIDENT — Staff ETA consent and delay compensation
- Assigned Staff submits a concrete ETA later than scheduled start, reason and time before work starts. Retain every ETA and response append-only in `Booking_Delay_Proposals`; Booking fields are only the current summary.
- Customer accepts that exact ETA or declines and cancels free. No response blocks start. If an accepted ETA is missed, its consent expires; Staff must submit a new ETA and Customer must agree again. Customer can cancel free before work starts.
- Staff arrival claim immediately calculates delay from scheduled start, rounds partial minutes up under current tiers with no 5-minute grace, caps discount at 30%, and stores the calculated amount on Booking. Early/on-time arrival gives zero delay discount. Admin correction of a reported claim recalculates the persisted amount before completion/payment. Customer confirmation/report alone does not calculate or modify money.
- AC-INCIDENT-01: Only assigned Staff can submit a valid ETA; ownership/status/time checks apply and every proposal remains auditable.
- AC-INCIDENT-02: Consent applies to one ETA. Decline cancels free; missed accepted ETA cannot authorize start; next ETA requires new consent.
- AC-INCIDENT-03: Tiers use Staff claimed arrival from scheduled start unless Admin corrects it before completion/payment; report/Customer acknowledgment alone leaves payable amount unchanged; discount is capped at 30% and persisted.
- AC-INCIDENT-04: Only Admin can edit contiguous tiers; reject gaps/overlaps/invalid percentages or bounded final tier. Later edits do not change saved Booking discounts.
### FR-JOB — Work execution/evidence
- Authorized assigned Staff upload multiple image evidence records tied to **Booking**, not Booking_Detail; image_url, uploaded_at. At least one image must exist before Staff confirms job completion. Staff confirms without Admin approval.
- Staff first records work start; start is allowed after Staff arrival confirmation and Customer consent for a reported delay, whether before, at or after the scheduled time. Customer cannot cancel after work has started. On valid work completion Booking CONFIRMED -> AWAITING_PAYMENT. `service_completed_at` is set when Staff confirms work completion before payment.
- AC-JOB-01: Staff A cannot upload image or confirm Staff B's Booking; storage path safe and file validation sane.
- AC-JOB-02: Completion without any booking image is rejected server-side; with at least one image it yields AWAITING_PAYMENT, not COMPLETED. Evidence is booking-level; no per-detail image minimum is required.

### FR-PAY — Payments
- Payment only after work completion; method CASH or ONLINE; Payment records represent attempts, Booking 1:N Payments, at most 1 SUCCESS. Amount matches booking total less any approved Staff-delay discount. Status PENDING/SUCCESS/FAILED; created_at/paid_at rules.
- CASH: assigned Staff confirms physically received money; ONLINE: Backend verifies true gateway result, including integrity/authenticity, amount/order binding, replay/idempotency, and concurrency. Browser redirect alone never marks payment success.
- If receipt verified and job completed, Payment SUCCESS and Booking COMPLETED atomically. Failed attempt does not complete Booking. ONLINE payments use the VNPay sandbox mapped to ONLINE, no DB `payment_gateway` column; the Backend confirms a payment from VNPay itself (server-to-server QueryDR lookup or a signed IPN); only when QueryDR is unavailable does it accept the return data VNPay signed with the merchant secret (valid HMAC, response and transaction status 00, amount matching the stored attempt). Unsigned or unverified browser data never marks payment success.
- AC-PAY-01: Create attempts safely; only one SUCCESS per Booking even with concurrent callbacks/requests.
- AC-PAY-02: Staff assigned can confirm CASH; ADMIN cannot replace this rule with manual Admin verification.
- AC-PAY-03: Valid verified ONLINE SUCCESS marks Booking COMPLETED; tampered/failed/duplicate webhook cannot incorrectly mark completed.
- AC-PAY-04: Booking status remains AWAITING_PAYMENT for no verified successful payment.

### FR-FEED — Feedback
- Customer gửi một feedback duy nhất cho Booking COMPLETED của mình với rating tổng thể 1–5, các chủ đề tùy chọn `TIMELINESS`, `CLEANING_QUALITY`, `STAFF_ATTITUDE`, `COMMUNICATION`, `VALUE_FOR_MONEY`, `OTHER`; lời nhắn bắt buộc ở rating 1–3 và tùy chọn ở 4–5.
- Customer xem feedback, chủ đề, lời nhắn, reply và trạng thái; được sửa rating/chủ đề/lời nhắn khi Admin chưa reply. Reply đầu tiên khóa sửa của Customer; feedback lịch sử rating NULL được giữ và chỉ đọc.
- Admin xem booking liên quan, được tạo/cập nhật reply cho đến khi resolve; chỉ cho resolve sau reply. Trạng thái hiển thị OPEN=`Đã tiếp nhận`, RESPONDED=`Đã phản hồi — chờ xử lý`, RESOLVED=`Đã giải quyết`.
- AC-FEED-01: Server từ chối rating ngoài 1–5, chủ đề ngoài danh sách, thiếu lời nhắn ở 1–3 sao, booking không thuộc Customer/chưa hoàn tất, gửi trùng hoặc sửa sau reply.
- AC-FEED-02: Admin xem đủ thông tin booking; resolve trước reply bị từ chối; Customer chỉ xem feedback của mình; Staff và Customer khác không đọc được feedback/reply.
- AC-FEED-03: Migration V9 giữ feedback cũ không rating, không thêm bảng nghiệp vụ, và SQL Server constraint cùng JPA mapping phản ánh tính hợp lệ của rating/nội dung/trạng thái.

### FR-CASE — Khiếu nại sự cố và trách nhiệm nội bộ
- Feedback có thể mở tối đa một hồ sơ sự cố cho booking, chọn nhiều loại `CLEANLINESS`, `PROPERTY_DAMAGE`, `MISSING_PROPERTY`, `OTHER`, có mô tả khách gửi và ít nhất một bằng chứng phù hợp. Ưu tiên ảnh khi sự cố có thể chụp; nếu không, chấp nhận ảnh sẵn có, hóa đơn, video MP4/WebM hoặc link video Google Drive do khách tự chia sẻ. Chỉ Customer sở hữu booking mới được mở hồ sơ trong 24 giờ kể từ `Bookings.service_completed_at`, thời điểm Staff xác nhận hoàn tất trước thanh toán. Bằng chứng append-only cho đến khi case resolve. Ảnh/PDF tối đa 5 MB mỗi tệp; mỗi tin tối đa một video MP4/WebM 50 MB và tổng tối đa 5 tệp. Link Drive không được hệ thống xác thực quyền chia sẻ.
- Với quy trình sự cố mới, mỗi lần Admin xác minh được lưu vào lịch sử riêng cùng trạng thái, kết luận nội bộ và các mã ảnh/PDF/video/link Drive đã xem. Manager có thể xem lịch sử này nhưng không sửa thay phần điều tra; Customer không thấy kết luận nội bộ.
- Hồ sơ bắt đầu `RECEIVED` (Chờ Admin tiếp nhận), Admin tiếp nhận để sang `INVESTIGATING`; phản ánh mất tài sản phải chuyển `SEARCHING_ITEM` trước khi gửi kết quả cuối hoặc đề xuất bồi thường. Customer ghi mong muốn xử lý tùy chọn để Admin tham khảo, không tự định giá hay chốt bồi thường. Admin xác minh và trao đổi; kết quả không có khoản tiền chuyển `WAITING_CUSTOMER` để Customer xác nhận hoặc yêu cầu hỗ trợ thêm.
- Chỉ `PROPERTY_DAMAGE`/`MISSING_PROPERTY` được tạo một đề xuất `COMPENSATION`; mất đồ phải qua bước tìm kiếm. Admin nhập số tiền và căn cứ, không lấy giá Customer khai để điền tự động. Customer chọn đồng ý hoặc cần trao đổi. Khi đồng ý, khoản chuyển `PENDING_APPROVAL`, hồ sơ `WAITING_MANAGER`; Manager chỉ duyệt sau đồng ý của Customer. Manager từ chối trả hồ sơ `INVESTIGATING`; Admin hủy đề xuất cũ và lập đề xuất mới để Customer xác nhận lại.
- Sau duyệt, hồ sơ `WAITING_PAYOUT`; Customer chọn tiền mặt/chuyển khoản. Chỉ chọn chuyển khoản mới yêu cầu thông tin ngân hàng, mã hóa AES-GCM và chỉ Admin/Manager xem. Nếu có đề xuất xử lý Staff, chỉ quyết toán sau khi Manager chốt trách nhiệm cuối, kể cả hạn giải trình/kháng nghị; không cần chờ Staff hoàn trả đủ. Admin thực hiện chi ngoài hệ thống, lưu mã tham chiếu/chứng từ; sau chi, payout response chuyển sang `SETTLED`, ciphertext được xóa và hồ sơ chuyển `WAITING_CUSTOMER_CONFIRMATION`. Customer xác nhận đã nhận mới đóng hồ sơ. Thời điểm Customer đồng ý số tiền, chọn cách nhận và xác nhận nhận tiền được lưu riêng.
- Giữ lịch sử trao đổi, bằng chứng, xác minh và xử lý Staff riêng. Không còn phương án nhiều lựa chọn, work order, chọn Staff/giờ làm lại hoặc trường chi phí/7ật tư/bằng chứng công việc. Hồ sơ lịch sử không được tự gán sự đồng ý hay kết quả mới.
- Khi có căn cứ Staff liên quan, Admin mở đề xuất xử lý và yêu cầu Staff giải trình trong 48 giờ. Staff xem nội dung/bằng chứng đã giới hạn quyền, gửi giải trình cùng ảnh/tài liệu; quá hạn chỉ ghi nhận không phản hồi, không xem là thừa nhận lỗi. Manager xem hai phía và chốt không quy trách nhiệm, biện pháp nội bộ, hoặc số tiền trách nhiệm vật chất dựa trên thiệt hại thực tế. Staff được phản đối một lần trong 48 giờ; Manager lưu kết luận cuối. Customer chỉ thấy “Đang xác minh nội bộ”, không thấy nội dung nội bộ. Khoản Customer đã chốt và Manager duyệt chỉ được Admin chi sau khi Manager chốt trách nhiệm Staff; không cần chờ Staff trả hết. Admin ghi từng lần Staff thực trả kèm ngày, phương thức, mã tham chiếu và chứng từ; hệ thống tính số dư, không tự khấu trừ lương. Số Staff hoàn trả độc lập với khoản Customer nhận. V28 thêm hai bảng cho tệp giải trình riêng tư và các lần thực nhận; dữ liệu quyết định Staff cũ được giữ nguyên.
- AC-CASE-01: Staff và Customer khác không xem hồ sơ, feedback hoặc bằng chứng; Customer chỉ xem case của booking mình. Staff chỉ thấy thông báo quyết định đã duyệt của chính mình. Upload sau resolve hoặc tệp sai định dạng/kích thước bị từ chối.
- AC-CASE-02: Kết luận khách thấy tách khỏi nội dung nội bộ; không resolve khi Customer chưa xác nhận kết quả/đã nhận tiền hoặc còn khoản bồi thường chưa hủy/quyết toán.
- AC-CASE-03: Chỉ Manager đang hoạt động khác người đề xuất được duyệt; quyết định nội bộ bị từ chối phải giữ trong lịch sử.
- Hồ sơ sự cố có timeline Customer/Admin theo thời gian; mô tả/ảnh ban đầu và reply Admin cũ được nhập lịch sử, reply cũ ghi rõ thời điểm gửi không được lưu. Mỗi tin mới tối đa 4.000 ký tự và 5 ảnh JPEG/PNG tối đa 5 MB/ảnh; tin/ảnh không sửa/xóa và hồ sơ đã giải quyết chỉ đọc. Customer có thể gửi chữ, ảnh hoặc cả hai; chỉ Customer sở hữu booking và Admin/Manager xem hoặc gửi tin.
- Manager chỉ duyệt khoản bồi thường sau khi Customer đồng ý chính xác số tiền; khoản cần trao đổi hoặc bị hủy/từ chối phải được Admin lập lại để Customer xác nhận lại. Customer thấy rõ các bước chờ khách, chờ Manager, chọn cách nhận và xác nhận đã nhận; chỉ thể hiện đã chi khi Admin lưu chứng từ và quyết toán. Khoản mô phỏng phải có nhãn rõ. V25 giữ lịch sử Flyway, gỡ bốn bảng phương án/phiếu việc và dùng luồng trạng thái rút gọn; V26 thêm `Bookings.service_completed_at` để tính hạn báo sự cố 24 giờ; V27 cho phép xóa ciphertext sau quyết toán qua trạng thái `SETTLED`; V28 bổ sung luồng giải trình/kháng nghị Staff và thu hồi theo thực nhận. Schema hiện tại có 26 bảng nghiệp vụ.

### FR-FIN — Khoản tiền, chứng từ và đối soát
- `Payments` chỉ lưu lần thu booking. `Booking_Financial_Entries` lưu `REFUND`, `PRICE_REDUCTION`, `REMEDIATION_COST`, `COMPENSATION` và giữ `STAFF_RECOVERY` chỉ để đọc lịch sử cũ; thu hồi Staff mới được lưu riêng theo từng lần thực nhận trong `Staff_Recovery_Installments`. Nghĩa vụ Staff là số tiền Manager kết luận, không phải khoản tiền đã thu. Báo cáo chỉ cộng từng khoản thực nhận có chứng từ, không cộng số dư còn nợ. Bồi thường khách chỉ Manager duyệt sau khi Customer đồng ý đúng số tiền, và Admin quyết toán đúng số tiền đó.
- Mọi khoản cần Manager khác người lập duyệt. Chỉ khoản đã quyết toán cùng chứng từ mới tính dòng tiền thật; `SIMULATED` tách riêng. ONLINE hiện là VNPay sandbox và được đánh dấu mô phỏng; không thể hiện giả lập như đã hoàn tiền thật.
- Báo cáo tách doanh thu dịch vụ, hoàn/giảm, chi phí khắc phục/bồi thường và thu hồi nội bộ; thuần = doanh thu - hoàn/giảm - chi phí/bồi thường + thu hồi. Giao dịch mô phỏng không tham gia thuần dòng tiền thật.
- AC-FIN-01: Khoản phải thuộc booking hợp lệ; chi phí/bồi thường phải link case; khoản thu hồi Staff chỉ ghi từng lần thực nhận theo quyết định cuối của Manager và không vượt nghĩa vụ đã chốt.
- AC-FIN-02: Không tự duyệt, không quyết toán trước duyệt/chứng từ, không tính khoản pending/rejected/cancelled hoặc mô phỏng vào tiền thật.
- AC-FIN-03: Tệp hóa đơn/biên nhận chỉ Admin xem; Customer/Staff không truy cập được. Hệ thống lưu chứng từ và mã tham chiếu, không tự phát hành hóa đơn thuế.

## 4. Data constraints
- Approved model currently has 26 business tables (see `SQL_SERVER_DATA_DICTIONARY.md`); case messages/evidence, Staff action history/evidence/recovery installments, financial entries and delay compensation tiers are business records. Flyway schema history is tooling metadata, not a business table.
- Reassignable staff current/original references; soft-delete House; preserved Booking history; no unnecessary created_at/updated_at fields beyond approved dictionary.
- Enforce authorization, role-FK matching, statuses, totals, uniqueness, route ETA, time, concurrency, and state transitions in appropriate DB and service layers. Handle failure atomically.

## 5. Non-functional quality (implementation expectations, not invented SLA)
- App must compile and run on supported JDK/SQL Server, avoid secrets in git, protect authorization and CSRF, validate uploads, handle provider faults without false success, show understandable errors, support transaction-safe concurrent booking/payment.
- Unit tests for business decisions; integration tests with SQL Server/Testcontainers if runtime available; MVC/security tests for ownership/status. Report checks exactly and do not claim SLA numbers or performance targets unapproved by user.

## 6. Explicit open points
- ARCH-03 live-data strategy and real gateway refund API, exact VNPay callback persistence needs while DB has only transaction_code, failure timeout, cross-midnight, admin booking reassignment eligibility details. Current refund/compensation ledger tracks payout evidence; it does not invoke VNPay refund APIs. Technical defaults must be documented as implementation choices, not approved BR.
