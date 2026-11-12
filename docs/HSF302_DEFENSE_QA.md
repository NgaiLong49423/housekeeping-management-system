# CẨM NANG VẤN ĐÁP BẢO VỆ ĐỒ ÁN HSF302
## Housekeeping Management System (Hệ Thống Đặt Dịch Vụ Dọn Nhà)

Tài liệu này tổng hợp toàn bộ 14 câu hỏi trọng tâm, tiêu chí đánh giá khắt khe của hội đồng giảng viên môn HSF302, kèm theo hướng dẫn trả lời, dẫn chứng code cụ thể (file, class, method) và cơ sở kỹ thuật tương ứng.

---

### Câu 1: Kiểm tra kiến trúc hệ thống (Layered Architecture)
- **Câu hỏi của Thầy**: *"Dự án đi theo kiến trúc gì? Chỉ ra luồng dữ liệu từ giao diện xuống cơ sở dữ liệu có tuân thủ đúng nguyên tắc phân tầng không?"*
- **Trả lời vấn đáp**:
  Dự án tuân thủ nghiêm ngặt **Kiến trúc phân tầng 3 lớp (3-Tier Layered Architecture)** chuẩn doanh nghiệp của Spring Boot:
  1. **Presentation Layer (`com.housekeeping.management.controller`)**: Chỉ tiếp nhận HTTP Request, binding dữ liệu form với `@Valid`, gọi Service tương ứng và trả về View (Thymeleaf) hoặc lệnh chuyển hướng `redirect:`. Tuyệt đối Controller không tiêm Repository hay viết SQL/logic nghiệp vụ phức tạp.
  2. **Business Logic Layer (`com.housekeeping.management.service` và `service.impl`)**: Đóng gói toàn bộ quy tắc nghiệp vụ, tính toán tiền tệ, quản lý vòng đời trạng thái và ranh giới giao dịch `@Transactional`. Service tách bạch thành Interface và Class triển khai (`Impl`).
  3. **Data Access Layer (`com.housekeeping.management.repository`)**: Kế thừa `JpaRepository` của Spring Data JPA để thao tác cơ sở dữ liệu an toàn, chống SQL Injection.
  4. **Domain/Data Layer (`com.housekeeping.management.entity` và `dto`)**: Các entity ánh xạ bảng trong cơ sở dữ liệu và các DTO (Java Record) vận chuyển dữ liệu an toàn.
- **Dẫn chứng minh họa**:
  - `BookingController` -> gọi interface `BookingReservationService` -> được thực thi bởi `BookingReservationServiceImpl` -> gọi `BookingRepository` và `BookingDetailRepository`.

---

### Câu 2: Thao tác convert DTO sang Entity (Tránh gọi new lặp đi lặp lại)
- **Câu hỏi của Thầy**: *"Thao tác chuyển đổi từ DTO sang Entity xử lý ở đâu để tránh việc trong Controller cứ gọi `new Booking()`, `booking.setX()`, `booking.setY()` lặp đi lặp lại?"*
- **Trả lời vấn đáp**:
  Controller nhận dữ liệu dạng DTO (`BookingWizardForm`, `AdminAccountForm`,...). Việc khởi tạo và lắp ráp Entity được đóng gói hoàn toàn bên trong tầng Service (Factory/Assembler pattern). Controller chỉ cần truyền DTO sang Service:
  ```java
  reservations.createBooking(user.getUserId(), bookingForm.staffId(), bookingForm.houseId(),
          bookingForm.startAt(), bookingForm.serviceIds(), bookingForm.areaNote());
  ```
  Tầng Service sẽ thực hiện khởi tạo Entity, validate quan hệ (Customer, Staff, House, Services) và lưu xuống Database trong cùng 1 Transaction an toàn.
- **File dẫn chứng**:
  - Controller gọi: [`BookingController.java`](file:///app/housekeeping-management-system/src/main/java/com/housekeeping/management/controller/BookingController.java) (dòng 184).
  - Service lắp ráp Entity: [`BookingReservationServiceImpl.java`](file:///app/housekeeping-management-system/src/main/java/com/housekeeping/management/service/impl/BookingReservationServiceImpl.java) (method `createBooking(...)`).

---

### Câu 3: Chỗ nào lưu Booking?
- **Câu hỏi của Thầy**: *"Khi khách bấm Đặt lịch trên web thì chính xác file nào, dòng code nào thực hiện lệnh lưu Booking vào Database?"*
- **Trả lời vấn đáp**:
  - **File**: `BookingReservationServiceImpl.java` (gói `com.housekeeping.management.service.impl`).
  - **Method**: `public Long createBooking(Long customerId, Long staffId, Long houseId, LocalDateTime start, List<Long> requestedServiceIds, String areaNote)`
  - **Dòng code lưu**:
    ```java
    Booking booking = new Booking();
    booking.setCustomer(customer);
    booking.setStaff(staff);
    booking.setHouse(house);
    booking.setBookingTime(now);
    booking.setScheduledStartTime(start);
    booking.setScheduledEndTime(calculatedEnd);
    booking.setStatus(BookingStatus.CONFIRMED);
    // ...
    bookingRepository.save(booking);
    ```
    Đồng thời lưu các bảng chi tiết liên quan:
    - Lưu danh sách dịch vụ: `bookingDetailRepository.saveAll(details)`
    - Lưu bút toán tài chính: `financialService.recordBookingCreation(booking.getBookingId(), ...)`

---

### Câu 4: Phân tách Controller theo trang (1 Controller per Page/Feature)
- **Câu hỏi của Thầy**: *"Thường một Controller đi với một trang. Có bị lỗi gom tất cả chức năng quản trị vào một file AdminController khổng lồ không?"*
- **Trả lời vấn đáp**:
  Hệ thống **không hề có** một AdminController ôm đồm. Các chức năng của Admin đã được tách rạch ròi thành 7 Controller độc lập, mỗi Controller quản lý một trang chức năng riêng biệt:
  1. `AdminAccountController`: Quản lý danh sách và trạng thái tài khoản (`/admin/accounts`).
  2. `AdminStaffController`: Quản lý hồ sơ nhân viên và ca làm việc (`/admin/staff`).
  3. `AdminBookingController`: Điều phối và giám sát các lịch hẹn (`/admin/bookings`).
  4. `AdminFeedbackController`: Tiếp nhận đánh giá và giải quyết sự cố (`/admin/feedbacks`).
  5. `AdminServiceController`: Quản lý danh mục gói dịch vụ dọn nhà (`/admin/services`).
  6. `AdminLeaveController`: Xét duyệt đơn xin nghỉ phép của nhân viên (`/admin/leaves`).
  7. `AdminCompanySettingsController`: Cài đặt giờ hoạt động và mức bồi thường trễ (`/admin/settings`).
- **File dẫn chứng**: Thư mục `com.housekeeping.management.controller`.

---

### Câu 5: Quản lý nghiệp vụ đi từ tầng Service móc lên
- **Câu hỏi của Thầy**: *"Các thao tác quản lý dữ liệu có được đẩy hết vào tầng Service để xử lý không?"*
- **Trả lời vấn đáp**:
  Toàn bộ các Controller chỉ đóng vai trò điều phối giao diện. Nghiệp vụ quản lý đều do các Service tương ứng xử lý:
  - Quản lý tài khoản: `AdminAccountService` -> `AdminAccountServiceImpl`.
  - Quản lý nhân viên: `StaffManagementService` -> `StaffManagementServiceImpl`.
  - Quản lý lịch hẹn: `AdminBookingManagementService` -> `AdminBookingManagementServiceImpl`.
  - Quản lý dịch vụ: `ServiceManagementService` -> `ServiceManagementServiceImpl`.
  - Quản lý đơn nghỉ: `LeaveManagementService` -> `LeaveManagementServiceImpl`.

---

### Câu 6: Định dạng số tiền có dấu chấm phân cách hàng nghìn
- **Câu hỏi của Thầy**: *"Số tiền hiển thị trên web có dấu chấm phân cách hàng nghìn không (ví dụ 150.000 đ thay vì 150000)?"*
- **Trả lời vấn đáp**:
  Toàn bộ các trang giao diện Thymeleaf đều sử dụng hàm định dạng chuẩn của Thymeleaf Numbers Utility:
  `${#numbers.formatDecimal(price, 0, 'POINT', 0, 'COMMA')} ₫`
  - Đảm bảo hiển thị chính xác: `100000` -> `100.000 ₫`, `1500000` -> `1.500.000 ₫`.
- **Dẫn chứng trên giao diện**:
  - `booking-detail.html` (dòng 180, 247, 381).
  - `booking-new.html` (dòng 50, 243).
  - `bookings.html` (dòng 128).
  - `services.html` (bảng giá niêm yết).

---

### Câu 7: Ràng buộc khuyến mãi / bồi thường có mức trần (Maximum Cap)
- **Câu hỏi của Thầy**: *"Nếu có khuyến mãi hoặc bồi thường giảm giá thì tối đa bao nhiêu? Có ràng buộc rõ ràng trong code không?"*
- **Trả lời vấn đáp**:
  Hệ thống có chính sách bồi thường khi nhân viên đến trễ (**Delay Compensation Tiers**). Quy tắc này được ràng buộc chặt chẽ cả ở Database Constraint lẫn tầng Service Validation:
  - **Giới hạn trần tối đa**: Mức giảm giá tối đa không được vượt quá **30%** (`tier.discountPercent() <= 30`).
  - **Quy tắc không chồng lấn**: Các khoảng thời gian phút trễ phải liên tục và không được ngắt quãng hay trùng lấn.
  - **Nếu trễ quá 30 phút**: Khách hàng có quyền hủy lịch hẹn hoàn toàn miễn phí.
- **File dẫn chứng**:
  - [`BookingDelayCompensationSettingsServiceImpl.java`](file:///app/housekeeping-management-system/src/main/java/com/housekeeping/management/service/impl/BookingDelayCompensationSettingsServiceImpl.java) (dòng 38-50).

---

### Câu 8: Minh chứng chụp ảnh (Nghiệm thu & Sự cố)
- **Câu hỏi của Thầy**: *"Có bằng chứng hình ảnh khi nghiệm thu công việc hoặc khi báo cáo sự cố không? Có ảnh mẫu để demo không?"*
- **Trả lời vấn đáp**:
  1. **Nghiệm thu hoàn tất công việc**: Khi nhân viên dọn dẹp xong, bắt buộc tải lên ảnh chụp hiện trường hoàn thành (`evidence-upload`) thì hệ thống mới chuyển lịch sang trạng thái `AWAITING_PAYMENT`.
  2. **Bằng chứng sự cố mất mát/hư hại**: Khi khách hàng gửi khiếu nại sự cố, giao diện bắt buộc khách nhập: tên món đồ, giá trị ước tính, số tháng sử dụng và tải kèm ảnh chụp hiện trường đồ bị hỏng (`FeedbackCaseEvidence`).
  3. **Dữ liệu mẫu để demo**: Dự án có sẵn thư mục ảnh mẫu nghiệm thu `src/main/resources/demo-evidence/` để phục vụ demo trực tiếp cho thầy xem mà không cần phải dùng điện thoại chụp thật.

---

### Câu 9: Bảng tra cứu "Chỗ nào xử lý ở code nào" (Cheat Sheet)

| Nghiệp vụ | Controller xử lý | Service phụ trách | Repository / Entity |
|---|---|---|---|
| Đăng ký / Đăng nhập | `AuthController` | `AuthService`, `RegistrationService` | `UserRepository` / `User` |
| Quản lý nhà của khách | `HouseController` | `HouseService` | `HouseRepository` / `House` |
| Đặt lịch dọn nhà | `BookingController` | `BookingReservationService`, `BookingAvailabilityService` | `BookingRepository`, `BookingDetailRepository` |
| Kiểm tra khoảng cách & lộ trình | `BookingController` | `VietMapRoutingClient`, `BookingReservationService` | Tọa độ GPS từ `House` |
| Xác nhận đến (2 chiều) | `BookingController`, `StaffController` | `BookingArrivalService` | `Booking.staffArrivedAt`, `customerConfirmedArrivalAt` |
| Tải ảnh nghiệm thu | `StaffController` | `BookingCompletionService`, `EvidenceStorageService` | `BookingImageRepository` / `BookingImage` |
| Thanh toán tiền mặt | `PaymentController`, `StaffController` | `PaymentService` | `PaymentRepository` / `Payment` |
| Thanh toán VNPay Sandbox | `PaymentController` | `OnlinePaymentService`, `VnPayClient` | Tra cứu chữ ký HMAC-SHA512 & API QueryDR |
| Đánh giá & Báo sự cố | `BookingController` | `FeedbackService`, `FeedbackCaseService` | `FeedbackRepository`, `FeedbackCaseRepository` |
| Nhân viên xin nghỉ phép | `StaffLeaveController` | `LeaveManagementService` | `StaffLeaveRepository` / `StaffLeave` |
| Admin tạo nhân viên | `AdminStaffController` | `StaffManagementService` | `StaffProfileRepository`, `UserRepository` |
| SuperAdmin khóa Admin khác | `AdminAccountController` | `AdminAccountService`, `AccountAuthorizationService` | `AdminAccountRepository` |

---

### Câu 10: Kiểm tra Session (Reload trang không mất dữ liệu)
- **Câu hỏi của Thầy**: *"Kiểm tra session khi người dùng reload trang (F5) có bị mất dữ liệu không?"*
- **Trả lời vấn đáp**:
  1. **Quy trình Đặt lịch (Booking Wizard)**: Toàn bộ quá trình chọn dịch vụ, chọn địa chỉ nhà, chọn ngày giờ và nhân viên được script client (`portal.js`) tự động đồng bộ vào `sessionStorage` của trình duyệt. Nếu khách vô tình F5 hoặc chuyển trang rồi quay lại, dữ liệu đã chọn sẽ được khôi phục tự động 100%.
  2. **Bảo mật phiên đăng nhập**: Sử dụng HTTP Session tiêu chuẩn của Spring Security với cookie an toàn `JSESSIONID` (cấu hình `HttpOnly=true` và `SameSite=Lax`). Đăng nhập được duy trì ổn định xuyên suốt phiên làm việc.
- **File dẫn chứng**:
  - Lưu draft: `portal.js` (hàm `saveBookingDraft` tại dòng 485 và `restoreDraft` tại dòng 1216).
  - Cấu hình cookie: `application.properties` (dòng 33-34).

---

### Câu 11: Ràng buộc ở từng khâu (State Machine & Business Constraints)
- **Câu hỏi của Thầy**: *"Có ràng buộc logic ở từng bước không? Có trường hợp nhảy cóc bước không?"*
- **Trả lời vấn đáp**:
  Hệ thống cài đặt máy trạng thái (State Machine) cực kỳ nghiêm ngặt:
  - **Không nhảy cóc**: Không thể chuyển sang `AWAITING_PAYMENT` nếu chưa có ảnh nghiệm thu (`BookingArrivalService.lockBookingForEvidence`). Không thể chuyển sang `COMPLETED` nếu chưa thanh toán tiền mặt hoặc VNPay.
  - **Ràng buộc thời gian đặt lịch**:
    - Phải đặt trước giờ bắt đầu tối thiểu 120 phút (đủ thời gian cho nhân viên chuẩn bị và di chuyển).
    - Chỉ cho phép đặt trong vòng 30 ngày tới.
    - Phút bắt đầu chỉ được chọn các mốc chuẩn: `00`, `15`, `30`, `45`.
  - **Ràng buộc di chuyển**: Tính toán khoảng cách giữa các nhà qua VietMap API, nếu khoảng cách và thời gian di chuyển giữa 2 lịch của cùng 1 nhân viên không đủ, hệ thống sẽ báo không khả thi và từ chối đặt lịch.

---

### Câu 12: Chức năng Tạo nhân viên
- **Câu hỏi của Thầy**: *"Chức năng tạo nhân viên thực hiện như thế nào?"*
- **Trả lời vấn đáp**:
  - Giao diện Admin: `/admin/staff`
  - Admin điền thông tin: Họ tên, Email, Số điện thoại, Mật khẩu ban đầu, Phân loại nhân viên (`FULL_TIME` hoặc `PART_TIME`).
  - Hệ thống tự động mã hóa mật khẩu bằng BCrypt, gán vai trò `STAFF`, kích hoạt trạng thái `ACTIVE` và tạo kèm bản ghi `StaffProfile`.
- **File dẫn chứng**:
  - Controller: `AdminStaffController.java`
  - Service: `StaffManagementServiceImpl.java` (method `createStaff(...)`).

---

### Câu 13: Admin không được khóa Admin khác — Vai trò SuperAdmin (MANAGER)
- **Câu hỏi của Thầy**: *"Admin thường có được tự ý khóa tài khoản của các Admin khác không? Phân quyền SuperAdmin như thế nào?"*
- **Trả lời vấn đáp**:
  Hệ thống thiết kế theo nguyên tắc bảo mật tối thiểu (Principle of Least Privilege):
  - **Admin thường**: Chỉ có quyền quản trị vận hành hàng ngày (nhân viên, khách hàng, duyệt lịch, dịch vụ). **Tuyệt đối không được phép khóa, sửa hay đổi trạng thái của các Admin khác**.
  - **Tài khoản SuperAdmin (MANAGER)**: Là tài khoản quản trị cấp cao nhất của hệ thống (mặc định: `manager@gonnha.vn` / `Password123!`). Chỉ khi đăng nhập bằng tài khoản Manager này thì mới có quyền tạo mới, kích hoạt hoặc khóa tài khoản của các Admin khác.
  - **Bằng chứng code**:
    Trong `AdminAccountServiceImpl.java`:
    ```java
    public void updateStatus(Long managerId, Long adminId, String status) {
        authorization.requireManager(managerId); // Bắt buộc phải là Manager!
        // ...
    }
    ```
    Hàm `requireManager(managerId)` trong `AccountAuthorizationServiceImpl.java` sẽ ném ngoại lệ `AccessDeniedException` ngay lập tức nếu người thực hiện không phải là `UserRole.MANAGER`.

---

### Câu 14: Các kỹ thuật sử dụng trong toàn bộ dự án
- **Câu hỏi của Thầy**: *"Đoạn code này/chức năng này sử dụng kỹ thuật gì?"*
- **Bảng tổng hợp kỹ thuật trả lời**:
  1. **Spring Security & RBAC (Role-Based Access Control)**: Phân quyền theo vai trò `CUSTOMER`, `STAFF`, `ADMIN`, `MANAGER` dựa trên SecurityFilterChain và Custom UserDetailsService.
  2. **Layered Architecture & Separation of Concerns**: Tách biệt Controller, Service, Repository, DTO.
  3. **DTO Pattern & Java Records**: Sử dụng `record` để làm các DTO bất biến, an toàn đa luồng.
  4. **Declarative Transaction Management (`@Transactional`)**: Đảm bảo toàn vẹn dữ liệu ACID khi ghi đồng thời nhiều bảng (Booking, BookingDetail, FinancialRecord).
  5. **Thymeleaf Fragments & Layout Dialect**: Tái sử dụng Header, Footer, Admin Sidebar, Modal bằng `th:replace` và `th:fragment`.
  6. **HMAC-SHA512 Signature & QueryDR (VNPay Sandbox)**: Ký và kiểm tra toàn vẹn gói tin thanh toán trực tuyến, gọi API QueryDR để đối soát giao dịch độc lập.
  7. **VietMap Routing API Integration**: Tích hợp REST API bên ngoài để tính toán thời gian di chuyển thực tế.
  8. **Audit Trail & Optimistic Concurrency**: Theo dõi dấu vết thay đổi dữ liệu, chống xung đột phiên làm việc.
  9. **Client-side Session Storage**: Lưu trạng thái form nháp chống mất dữ liệu khi reload trang.
  10. **Database Migration với Flyway**: Quản lý phiên bản cấu trúc database chuyên nghiệp.
