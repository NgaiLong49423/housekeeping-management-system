> **Document:** Software Requirements Specification\
> **File:** `docs/requirements/SRS.md`\
> **Version:** v6.2.0\
> **Created:** 2026-06-14\
> **Last Updated:** 2026-11-10\
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
| Hồ sơ sự cố/bằng chứng | Own; bổ sung ảnh đến khi resolved | Chỉ nhận thông báo quyết định của mình đã được duyệt; không xem hồ sơ/feedback/bằng chứng | Xác minh, ghi kết quả khách thấy, quản lý khoản tiền và đề xuất xử lý Staff | Như Admin; duyệt quyết định nội bộ |
| Sổ tài chính sự cố | Xem kết quả/biện pháp của mình | — | Lập khoản; ghi quyết toán/chứng từ | Như Admin; duyệt khoản khác người lập |
| Company config, Staff status, Services | — | — | Yes | Yes |
| Tạo/khóa Admin | — | — | — | Yes; không thể tạo Manager khác |

## 3. Functional requirements và acceptance

### FR-AUTH — Authentication and roles
- FR-AUTH-01: `Users` là bảng xác thực duy nhất. Username/email unique, password_hash, status ACTIVE/INACTIVE, role CUSTOMER/STAFF/ADMIN/MANAGER. Chỉ có tối đa một Manager; Manager có toàn bộ quyền Admin và là vai trò duy nhất tạo/khóa Admin. Staff 1:1 với Users có role STAFF.
- FR-AUTH-02: Spring Security HTTP Session và role-based access; ngăn User inactive; kiểm tra resource ownership và Staff assignment.
- AC-AUTH-01: Login các role qua một trang, password sai/inactive denied; Customer A cannot operate Customer B's House/Booking/Feedback; Manager vào được toàn bộ chức năng Admin, Admin không truy cập được quản trị tài khoản.
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
- Giờ Admin cấu hình và giờ Staff đăng ký là thời điểm nhân viên có mặt tại nhà khách, sẵn sàng bắt đầu phục vụ. Booking đầu ngày đi từ tâm công ty; hệ thống lấy ETA tuyến xe máy VietMap cơ sở + 15 phút để ước tính giờ xuất phát lùi từ giờ booking và truyền thời điểm đó vào Route v4. Giữa hai booking trong cùng ngày, khoảng trống tối thiểu là ETA tuyến VietMap từ địa chỉ booking trước đến địa chỉ sau + 15 phút dự phòng (ví dụ ETA 20 phút cần 35 phút). Quãng đường đường bộ VietMap giữa hai booking cùng Staff không được vượt `serviceRadiusKm` Admin đang cấu hình; đúng bằng ngưỡng thì chấp nhận. Quy tắc này chỉ áp dụng cho chặng giữa booking, không cộng phí; bán kính nhà quanh tâm công ty vẫn được kiểm tra riêng. Booking cùng Staff không được chồng giờ. Thời điểm khởi hành gửi VietMap chỉ hỗ trợ tránh khu vực hạn chế theo giờ, ETA không phải dự báo kẹt xe tương lai. API lỗi/không có tuyến từ chối booking. Nếu dự kiến trễ, Staff có thể ghi báo cáo sự cố trên Booking và liên hệ Customer trực tiếp; báo cáo hiển thị cho Customer và Admin. Không tự đổi/hủy lịch hoặc áp dụng quy tắc hoàn tiền mới.
- Khi Customer tra giờ, availability-options bắt buộc nhận `houseId` và xác minh House thuộc Customer đang đăng nhập. Các mốc chồng giờ bị ẩn. Khi kiểm tra một mốc cụ thể, nếu thiếu thời gian di chuyển trước/sau thì loại mốc đó và nêu booking liên quan, khoảng trống hiện có, ETA, 15 phút dự phòng, thời điểm đủ điều kiện sớm nhất hoặc giờ bắt đầu muộn nhất. Lưới chọn vẫn theo 15 phút: đủ điều kiện từ 10:35 thì mốc đầu tiên có thể chọn là 10:45. Lỗi VietMap phải được phân biệt với thiếu thời gian di chuyển. Backend lặp lại cùng kiểm tra khi lưu; lỗi từng ngày giữ nguyên dịch vụ, House, ghi chú, giờ và Staff để Customer chỉnh sửa.
- On success snapshot House address/lat/lng/address_note and per-Service name/unit price/duration; total_amount=sum unit prices, total_duration_minutes=sum durations, end=start+duration; `original_staff_id=staff_id`; status CONFIRMED.
- One transaction with reliable concurrency coordination for parallel requests for same Staff. Do not use an unchecked `existsByOverlap` followed by insert alone.
- Multi-date submission validates/persists each date independently. A date that fails availability or route validation is reported; other valid dates may still be created. The UI must show which dates need correction/removal and the count of successfully created bookings.
- After a successful booking, remind Customer to confirm Staff arrival; repeat the reminder on each confirmed Booking detail until Customer records arrival.
- AC-BOOK-01: Valid Booking persists header + >=1 detail atomically with correct totals, duration, snapshots, status, staff references.
- AC-BOOK-02: Lead time <120min rejects, >=120min accepts when aligned to :00/:15/:30/:45; beyond 30d rejects.
- AC-BOOK-03: Different Staff can be booked at same time; same Staff is checked against route-aware gap.
- AC-BOOK-04: End outside current company/Staff interval rejects.
- AC-BOOK-05: Concurrent requests for same Staff/slot: at most one confirmed successful; no partial write.
- AC-BOOK-06: Single-date and multi-date success messages remind Customer to confirm Staff arrival; the detail reminder remains until confirmation.

### FR-CANCEL/REASSIGN — Cancellation and admin assignment
- Customer self-cancel only when `now <= start_time-120min` and work has not started; additionally, Customer may cancel a Booking for a reported Staff delay for free while work has not started. Persist CANCELLED status + cancelled_by/at/reason; not physical delete.
- Confirmed Booking House/time/services changes require cancel+rebook.
- Admin can replace assigned staff (`staff_id`), preserve immutable `original_staff_id`; verify replacement Staff's eligibility with the relevant rules. No separate reassignment history.
- AC-CANCEL-01: Exactly -120m allows customer cancellation; -119m rejects; cancellation leaves snapshots/details intact.
- AC-REASSIGN-01: Admin reassignment changes current staff, not original staff, and cannot create conflicting active booking.

### FR-ARRIVAL — Two-sided arrival record
- Customer owning a CONFIRMED Booking and its assigned Staff may independently record arrival at any time, including before the scheduled start. Each timestamp is stored separately and does not change Booking status. Customer confirmation is optional; assigned Staff confirmation is required before work can start.
- After the scheduled start, while Staff has not confirmed arrival and work has not started, the owning Customer may record once that Staff has not arrived. Store the report timestamp on the Booking and show it to the Customer and Admin only. It does not block Staff from starting, cancel the Booking, or change money; it is distinct from the Staff ETA/consent workflow.
- AC-ARRIVAL-01: Other Customers, non-CONFIRMED and duplicate Customer confirmations are rejected; a valid pre-start confirmation is allowed.
- AC-ARRIVAL-02: Unassigned Staff, non-CONFIRMED and duplicate Staff confirmations are rejected; a valid pre-start confirmation is allowed.
- AC-ARRIVAL-03: A Customer report is accepted once only for their CONFIRMED Booking at/after scheduled start while Staff arrival and work-start timestamps are absent; it is hidden from Staff and visible to Admin and the owner.
- Note: status-specific cancellation eligibility after work complete/payment is **TBD**; do not create new refund semantics.

### FR-INCIDENT — Staff delay, Customer consent and compensation
- Assigned Staff may optionally report one delay incident for a CONFIRMED Booking before work starts. The report stores a required 1–500 character reason, expected arrival time later than scheduled start, and report timestamp. The Customer sees the report, Staff calls directly using the Booking phone, and Admin can review the report and response.
- Early or late arrival alone does not block work start once assigned Staff has confirmed arrival. Staff may start early or wait until the scheduled start; timing does not alter the booked schedule, duration or amount. If Staff submits a delay report, Customer must explicitly accept waiting or decline; no response is not consent and blocks work start while that report is pending. Decline cancels the Booking free of charge before work starts.
- Admin edits ordered delay tiers, each with an inclusive minimum minute, inclusive maximum minute (or no maximum for the final tier), and integer discount percent from 0 to 100. Tiers must start at minute 1, have no gaps or overlaps, and exactly one final unbounded tier. Initial defaults are 1–15 minutes => 5%, 16–30 => 10%, >30 => 20%.
- On Staff arrival, calculate delay from scheduled start and round partial minutes upward using the active tier configuration. Calculate on total service amount and round the discount to whole VND. At a delay above 30 minutes Customer may cancel free before work starts or continue according to the active configured tier. Payment remains after service and uses total minus discount. A configuration edit affects Bookings whose Staff arrival has not yet been recorded; an already calculated Booking retains its stored discount.
- AC-INCIDENT-01: Report persists ETA/reason/time and is visible to Customer/Admin; wrong Staff, invalid status, duplicate report, or report after work starts is rejected.
- AC-INCIDENT-02: Only the owning Customer may accept/decline once. Decline cancels free; missing response blocks Staff start only when a delay report was submitted; without a report, Staff may start after confirming arrival regardless of scheduled time. Accepted delay permits start and records tiered discount on arrival.
- AC-INCIDENT-03: Delay boundaries are evaluated using the current contiguous tier configuration; zero delay gives 0%; customer payment uses the stored discounted amount.
- AC-INCIDENT-04: Only Admin can view and update delay tiers; invalid gaps, overlaps, ranges, percentages, missing tiers or a bounded final tier are rejected. Saving a configuration does not change discounts already recorded on Bookings.

### FR-JOB — Work execution/evidence
- Authorized assigned Staff upload multiple image evidence records tied to **Booking**, not Booking_Detail; image_url, uploaded_at. At least one image must exist before Staff confirms job completion. Staff confirms without Admin approval.
- Staff first records work start; start is allowed after Staff arrival confirmation and Customer consent for a reported delay, whether before, at or after the scheduled time. Customer cannot cancel after work has started. On valid work completion Booking CONFIRMED -> AWAITING_PAYMENT. No work_completed_at field.
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
- Feedback có thể mở tối đa một hồ sơ sự cố cho booking, chọn nhiều loại `CLEANLINESS`, `PROPERTY_DAMAGE`, `MISSING_PROPERTY`, `OTHER`, có mô tả khách gửi và tệp bằng chứng JPEG/PNG. Customer sở hữu booking đã hoàn tất và có feedback mới được mở hồ sơ; chỉ được bổ sung, không sửa/xóa bằng chứng, cho đến khi case resolve.
- Trạng thái case: `RECEIVED`, `INVESTIGATING`, `VERIFIED`, `NOT_SUBSTANTIATED`, `RESOLVED`. Admin lưu kết quả/cách khắc phục cho Customer thấy và kết luận nội bộ riêng. Chỉ resolve sau reply feedback, kết luận xác minh, đánh dấu biện pháp đã hoàn tất, các khoản hoàn/bồi thường yêu cầu đã quyết toán và không còn khoản tài chính/đề xuất xử lý Staff đang chờ.
- Admin có thể đề xuất `NONE`, `COACHING`, `PROPOSE_WARNING`, `PROPOSE_FINAL_WARNING`, `PROPOSE_RECOVERY`, `OTHER` khi case đã VERIFIED; thông báo nhắc nhở/cảnh cáo/thu hồi phải có nội dung riêng cho Staff. Manager duyệt/từ chối. Mỗi đề xuất/quyết định được lưu lịch sử; không tự kết luận lỗi, tự tăng cảnh cáo theo số lần, hoặc tự động khấu trừ tiền lương. Thu hồi nội bộ chỉ được lập sau khi quyết định `PROPOSE_RECOVERY` được duyệt, rồi vẫn phải qua duyệt khoản tiền riêng. Sau khi duyệt, Staff chỉ thấy quyết định và thông báo của chính mình, có thể xác nhận đã nhận mà không bị coi là thừa nhận lỗi.
- AC-CASE-01: Staff và Customer khác không xem hồ sơ, feedback hoặc bằng chứng; Customer chỉ xem case của booking mình. Staff chỉ thấy thông báo quyết định đã duyệt của chính mình. Upload sau resolve hoặc tệp sai định dạng/kích thước bị từ chối.
- AC-CASE-02: Kết luận khách thấy tách khỏi nội dung nội bộ; không resolve khi thiếu reply hoặc còn khoản/đề xuất xử lý đang chờ.
- AC-CASE-03: Chỉ Manager đang hoạt động khác người đề xuất được duyệt; quyết định nội bộ bị từ chối phải giữ trong lịch sử.
- Hồ sơ sự cố có timeline Customer/Admin theo thời gian; mô tả/ảnh ban đầu và reply Admin cũ được nhập lịch sử, reply cũ ghi rõ thời điểm gửi không được lưu. Mỗi tin mới tối đa 4.000 ký tự và 5 ảnh JPEG/PNG tối đa 5 MB/ảnh; tin/ảnh không sửa/xóa và hồ sơ đã giải quyết chỉ đọc. Customer có thể gửi chữ, ảnh hoặc cả hai; chỉ Customer sở hữu booking và Admin/Manager xem hoặc gửi tin.
- Manager duyệt/từ chối khoản chờ ngay trong hồ sơ, chỉ khi khác người lập. Customer thấy số tiền đề xuất cùng “Chờ duyệt”, sau duyệt “Đã duyệt, chờ thực hiện”; chỉ thể hiện đã chi/hoàn khi quyết toán được ghi nhận. Khoản mô phỏng phải có nhãn rõ. Migration V16 tạo bảng tin nhắn thứ 21 và gắn bằng chứng hiện hữu vào tin ban đầu, không làm mất hồ sơ cũ.

### FR-FIN — Khoản tiền, chứng từ và đối soát
- `Payments` chỉ lưu lần thu booking. `Booking_Financial_Entries` lưu từng `REFUND`, `PRICE_REDUCTION`, `REMEDIATION_COST`, `COMPENSATION`, `STAFF_RECOVERY` gắn booking và hồ sơ nếu phù hợp; ghi lý do, số tiền VND, người lập/duyệt, phương thức, mã giao dịch, chứng từ, trạng thái và thời điểm.
- Mọi khoản cần Manager khác người lập duyệt. Chỉ khoản đã quyết toán cùng chứng từ mới tính dòng tiền thật; `SIMULATED` tách riêng. ONLINE hiện là VNPay sandbox và được đánh dấu mô phỏng; không thể hiện giả lập như đã hoàn tiền thật.
- Báo cáo tách doanh thu dịch vụ, hoàn/giảm, chi phí khắc phục/bồi thường và thu hồi nội bộ; thuần = doanh thu - hoàn/giảm - chi phí/bồi thường + thu hồi. Giao dịch mô phỏng không tham gia thuần dòng tiền thật.
- AC-FIN-01: Khoản phải thuộc booking hợp lệ; chi phí/bồi thường/thu hồi nội bộ phải link case; thu hồi Staff chỉ lập khi case VERIFIED và quyết định Staff được duyệt.
- AC-FIN-02: Không tự duyệt, không quyết toán trước duyệt/chứng từ, không tính khoản pending/rejected/cancelled hoặc mô phỏng vào tiền thật.
- AC-FIN-03: Tệp hóa đơn/biên nhận chỉ Admin xem; Customer/Staff không truy cập được. Hệ thống lưu chứng từ và mã tham chiếu, không tự phát hành hóa đơn thuế.

## 4. Data constraints
- Approved model currently has 21 business tables (see `SQL_SERVER_DATA_DICTIONARY.md`); case messages, evidence, staff action history, financial entries and delay compensation tiers are business records. Flyway schema history is tooling metadata, not a business table.
- Reassignable staff current/original references; soft-delete House; preserved Booking history; no unnecessary created_at/updated_at fields beyond approved dictionary.
- Enforce authorization, role-FK matching, statuses, totals, uniqueness, radius, time, concurrency, and state transitions in appropriate DB and service layers. Handle failure atomically.

## 5. Non-functional quality (implementation expectations, not invented SLA)
- App must compile and run on supported JDK/SQL Server, avoid secrets in git, protect authorization and CSRF, validate uploads, handle provider faults without false success, show understandable errors, support transaction-safe concurrent booking/payment.
- Unit tests for business decisions; integration tests with SQL Server/Testcontainers if runtime available; MVC/security tests for ownership/status. Report checks exactly and do not claim SLA numbers or performance targets unapproved by user.

## 6. Explicit open points
- ARCH-03 live-data strategy and real gateway refund API, exact VNPay callback persistence needs while DB has only transaction_code, failure timeout, cross-midnight, admin booking reassignment eligibility details. Current refund/compensation ledger tracks payout evidence; it does not invoke VNPay refund APIs. Technical defaults must be documented as implementation choices, not approved BR.
