> **Document:** Agent Instructions\
> **File:** `AGENTS.md`\
> **Version:** v2.2.0\
> **Created:** 2026-06-29\
> **Last Updated:** 2026-11-10\
> **Status:** Template\

# Agent Entry Point

Read `.agents/POLICY.md` before repository work. Then inspect the relevant project documentation and code only for the task at hand.

## Canonical Sources

- Repository overview and project-specific usage: `README.md`
- Contribution and branch workflow: `CONTRIBUTING.md`
- Requirements: `docs/requirements/PRD.md` and `docs/requirements/SRS.md`
- Approved SQL Server schema contract: `docs/requirements/SQL_SERVER_DATA_DICTIONARY.md`
- Database starter: `database/README.md` and the scripts that apply to the selected database
- Machine-readable repository contract: `.agents/repo-contract.yml`
- Reusable Agent procedures: `.agents/skills/` and `.agents/workflows/`
- Java package structure and layer boundaries: `docs/architecture/java-package-structure.md`

`AGENTS.md` is the entry point. `.agents/POLICY.md` owns agent permissions and safety rules. `CONTRIBUTING.md` owns the contributor workflow. The repository contract summarizes paths and machine-checkable rules; it does not override these sources.

The approved application baseline is Java 21, Spring Boot, Spring MVC/Thymeleaf, Spring Data JPA, SQL Server and Flyway. Preserve the single Maven project and the approved SQL Server schema and migration history described by the Data Dictionary and database guide. Do not apply the legacy MySQL reference DDL or rewrite applied Flyway migrations.

## Java Application Structure

For Java application work, read [`docs/architecture/java-package-structure.md`](docs/architecture/java-package-structure.md) and follow its package layout: `config/`, `controller/`, `dto/entity/`, `dto/request/`, `dto/response/`, `entity/`, `repository/`, `service/`, and `service/impl/`; use `domain/` for suitable enums/value types. Every application service has an interface in `service/` and a Spring implementation in `service/impl/`. Repositories are called directly by their owning service implementation; Spring Data repositories remain interfaces and custom JDBC repositories may be classes. Every JPA entity and embeddable has a DTO with `convertEntityToDTO` and `convertDTOToEntity`; services resolve related entities. Keep layer responsibilities and the existing MVC boundary. This is the team's strong default, not a blanket instruction to rename untouched legacy classes. If a new class must sit outside the layout, keep the exception narrow and explain the reason in the change description.

## Repository Layout

- Application starter: `app/`
- Database starter: `database/`
- Project documentation: `docs/`
- Agent policy, contract, skills, workflows, and scripts: `.agents/`
- GitHub templates and Actions: `.github/`

## Protected Content

Never access, enumerate, search, parse, validate, create, edit, move, or delete files under `docs/diagrams/`. Exclude that subtree before selecting or traversing any inputs. Do not open or validate a link target inside it. Apply the same boundary to every skill, workflow, script, and CI job.

## Documentation

Use repository-relative links. Never add machine-local `file:///` links. Preserve each artifact's appropriate metadata format; do not apply document metadata blocks to skill frontmatter, workflows, or machine-readable configuration.

---

## Current Work Progress & System Architecture Evolution (Thay đổi cấu trúc & Hướng dẫn Agent tiếp theo)

> **Branch:** `feature/frontend-update`  
> **Updated:** 2026-11-12  
> **Status:** Đã hoàn thành tái cấu trúc giao diện đặt lịch và xây dựng hoàn chỉnh Core Backend cho Chính sách Hủy đơn & Phí phạt tự động (bTaskee Model). Toàn bộ 306/306 test cases đạt `BUILD SUCCESS`.

---

### 1. Thay đổi cấu trúc hệ thống so với ban đầu (Before vs After)

| Thành phần | Cấu trúc Ban đầu (Baseline) | Cấu trúc Hiện tại (Current) | Mục đích thay đổi |
| :--- | :--- | :--- | :--- |
| **Bảng `Bookings`** | Chỉ lưu `status` (`CONFIRMED`, `CANCELLED`...), hủy đơn là cập nhật cờ `CANCELLED` cứng không tính toán dòng tiền. | Bổ sung `cancellation_fee` (phí phạt), `refund_amount` (tiền hoàn lại), `reschedule_count` (số lần dời lịch). | Hỗ trợ tính toán chi phí phạt động và giới hạn số lần dời lịch miễn phí. |
| **Bảng `Users`** | Chỉ lưu thông tin cá nhân, trạng thái chỉ có `ACTIVE`/`INACTIVE`, không có cơ chế quản chế tài khoản. | Bổ sung `unpaid_penalty_debt` (nợ phí hủy), `allow_cod` (quyền thanh toán tiền mặt), `suspended_until` (thời hạn khóa), trạng thái `SUSPENDED`. | Triển khai chế tài chống "bom" đơn, chống quỵt nợ phạt khi khách hủy đơn COD. |
| **Hệ thống Ví (`UserWallets`, `WalletTransactions`)** | Chưa có hệ thống ví nội bộ; bồi hoàn phải thao tác thủ công ngoài hệ thống. | Tạo mới bảng `UserWallets` và nhật ký `WalletTransactions` (Flyway `V32`). | Tự động hoàn tiền thừa về ví khách hàng và tự động giải ngân 80% tiền đền bù vào ví CTV. |
| **Nhật ký Hủy (`CancellationLogs`)** | Chỉ lưu một chuỗi text `cancellation_reason` trong bảng `Bookings`. | Tạo mới bảng `CancellationLogs` lưu chi tiết: mốc thời gian cách giờ làm `delta_t`, mã lý do, phí phạt, phân chia 80/20, cờ lỗi CTV `is_worker_fault`. | Phục vụ đối soát kế toán, giải quyết khiếu nại và làm căn cứ đánh giá lịch sử vi phạm 30 ngày của khách. |
| **Xử lý Đồng thời (Concurrency)** | Chỉ kiểm tra điều kiện trạng thái thông thường qua bộ lọc. | Sử dụng Khóa bi quan (`PESSIMISTIC_WRITE` / SQL Server `WITH (UPDLOCK, ROWLOCK)`). | Ngăn chặn triệt để Race Condition: Khách bấm hủy cùng lúc CTV bấm "Bắt đầu làm việc". |

---

### 2. Chi tiết những phần đã sửa đổi & nâng cấp

#### A. Tinh chỉnh luồng Đặt lịch phía Khách hàng (Frontend Flow):
- **Trang chủ (`/`):** Thu gọn chỉ hiển thị 4 dịch vụ tiêu biểu kèm nút bấm "Xem tất cả dịch vụ" chuyển hướng sang `/services`.
- **Trang danh mục dịch vụ (`/services`):** Loại bỏ các khối cam kết quy chuẩn và banner giới thiệu để tập trung hiển thị danh sách thẻ dịch vụ.
- **Bước 1 Đặt lịch (`/customer/bookings/new`):** Mặc định chỉ hiển thị 4 dịch vụ ban đầu, có nút bo tròn "Xem thêm dịch vụ khác / Thu gọn" linh hoạt; tự động mở rộng nếu dịch vụ đang chọn nằm ở nhóm phía sau.
- **Bước 3 Đặt lịch:** Danh sách thẻ nhân viên yêu thích và dropdown lọc nghiêm ngặt chỉ hiển thị nhân viên đã được thả tim (`favorite == true`), kèm thông báo điều hướng nếu danh sách trống.
- **Bước 4 Đặt lịch:** Khi khách đã chọn nhân viên yêu thích, **bỏ hoàn toàn Bước 2: Chọn nhân viên cho ca...**, khách chỉ cần chọn giờ bắt đầu là hệ thống tự động gán nhân viên yêu thích và chốt thẻ xanh (`✓ Sẵn sàng thực hiện`).

#### B. Xây dựng Core Engine Hủy đơn & Phí phạt (Backend Services & API):
- **Công thức phí hủy theo mốc thời gian (`delta_t = T_start - T_cancel`):**
  - **Ân hạn (Grace Period):** Trong 10 phút đầu sau khi tạo đơn và còn cách giờ làm > 1h: Phí = `0đ`.
  - **Trước > 6 tiếng:** Phí = `0đ`.
  - **Từ 2h đến 6 tiếng:** Phí = `25%` giá trị đơn (tối thiểu 30.000đ).
  - **Dưới 2 tiếng:** Phí = `50%` giá trị đơn (tối thiểu 50.000đ).
  - **Sau giờ bắt đầu hoặc CTV đã đến (`ARRIVED`):** Phí = `100%` giá trị đơn.
- **Cung cấp 2 REST API mới:**
  - `GET /api/bookings/{id}/cancellation-preview`: Cho phép giao diện xem trước mức phạt, số tiền được hoàn ví, và kiểm tra quyền dời lịch miễn phí (`canReschedule`).
  - `POST /api/bookings/{id}/cancel`: Thực thi hủy đơn có kèm mã lý do (`reasonCode`, `reasonNote`).

---

### 3. Toàn bộ các cơ chế ràng buộc kỹ thuật & Nghiệp vụ (Key Constraints)

1. **Ràng buộc Chống quỵt nợ phạt COD (Debt Enforcement):**
   - Đối với đơn thanh toán tiền mặt (COD), khi khách hủy đơn phát sinh phí phạt, số tiền phạt được hệ thống tự động ghi nhận vào `Users.unpaid_penalty_debt`.
   - Khách hàng không thể bỏ qua khoản phí này: hệ thống chặn không cho đặt lịch đơn mới cho đến khi hoàn tất thanh toán khoản nợ phạt (hoặc tự động cộng dồn cưỡng chế).
2. **Ràng buộc Chống "bom" đơn trong 30 ngày (Anti-Fraud / Abuse Control):**
   - Đếm số lần hủy đơn trễ có phát sinh phí trong 30 ngày gần nhất (`late_cancel_count_30d`):
     - **Vi phạm lần 1:** Gửi cảnh báo vi phạm trong thông điệp phản hồi.
     - **Vi phạm lần 2:** **Tước quyền thanh toán tiền mặt (`allow_cod = false`)**, bắt buộc khách phải thanh toán trước 100% trực tuyến cho các đơn sau.
     - **Vi phạm lần 3 trở lên:** **Đình chỉ tài khoản tạm thời (`status = SUSPENDED`, `suspended_until = now + 7 ngày`)**.
3. **Ràng buộc Chống Race Condition khi CTV đã làm việc:**
   - Hệ thống khóa bi quan dòng dữ liệu đơn hàng bằng `findByIdWithLock(bookingId)` trong Transaction ACID.
   - Chặn tuyệt đối hành vi hủy đơn nếu CTV đã bấm Bắt đầu làm (`workStartedAt != null`) hoặc đơn đã `COMPLETED`.
4. **Ràng buộc Đền bù quyền lợi cho CTV (Worker Compensation):**
   - Bất kể đơn thanh toán Online hay COD, hệ thống đều trích **80% phí phạt** chuyển vào ví `UserWallets` của CTV để bù đắp chi phí di chuyển và thời gian chờ ca của thợ (20% còn lại thuộc về phí sàn).
5. **Ràng buộc Miễn trừ hợp lý cho Khách hàng:**
   - Nếu đơn hàng chưa có CTV nhận ca (`PENDING`), khách hủy sát giờ vẫn được miễn phí 100%.
   - Nếu CTV đến trễ hơn giờ hẹn quá 15 phút mà chưa check-in, khách chọn lý do `WORKER_LATE` sẽ được miễn phí hủy 100% (`is_worker_fault = true`) và không bị ghi nhận điểm phạt chống bom đơn.

---

### 4. Hướng dẫn tiếp nối cho Agent / Contributor tiếp theo (Actionable Handoff)

1. **Frontend Integration - Modal Pop-up Hủy đơn:**
   - Tại trang chi tiết lịch hẹn của khách hàng (`/customer/bookings/{id}`), thay thế nút Submit hủy đơn thông thường bằng Pop-up xác nhận hủy:
     - Khi mở Pop-up: Gọi `GET /api/bookings/{id}/cancellation-preview` để hiển thị số tiền phạt, số tiền hoàn vào ví, và nút "Dời lịch miễn phí" (nếu `canReschedule == true`).
     - Khi xác nhận: Gọi `POST /api/bookings/{id}/cancel` kèm lý do.
2. **Giao diện Ví Khách hàng & Thanh toán nợ phạt (`/customer/wallet`):**
   - Xây dựng trang hiển thị số dư ví, lịch sử hoàn tiền hủy đơn.
   - Thêm nút "Thanh toán nợ phạt ngay" tích hợp VNPay nếu `unpaid_penalty_debt > 0` để giúp khách hàng tự gỡ khóa quyền đặt đơn và mở lại quyền COD.
3. **Quản lý Khiếu nại Hủy đơn:**
   - Xây dựng giao diện tra cứu lịch sử `CancellationLogs` để Admin/Manager đối soát các đơn khách báo `WORKER_LATE` dựa trên dữ liệu check-in/GPS thực tế.


