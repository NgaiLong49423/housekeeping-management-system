> **Document:** Java Package Structure Guide\
> **File:** `docs/architecture/java-package-structure.md`\
> **Version:** v2.1.0\
> **Created:** 2026-10-24\
> **Last Updated:** 2026-10-25\
> **Status:** Active\

# Hướng dẫn cấu trúc package Java

Tài liệu này quy định cách tổ chức code Java mục tiêu trong ứng dụng. Agent và thành viên nhóm phải ưu tiên cấu trúc dưới đây khi xây mới hoặc tái cấu trúc từng luồng nghiệp vụ. Đây là quy ước linh hoạt: tên class có thể khác ví dụ nếu vẫn diễn đạt đúng domain và trách nhiệm.

## Cây thư mục mục tiêu

```text
app/housekeeping-management-system/
├── pom.xml
└── src/
    ├── main/
    │   ├── java/com/housekeeping/management/
    │   │   ├── config/
    │   │   │   ├── SecurityConfig.java
    │   │   │   └── ...
    │   │   ├── controller/
    │   │   │   ├── AuthController.java
    │   │   │   ├── BookingController.java
    │   │   │   ├── StaffController.java
    │   │   │   ├── PaymentController.java
    │   │   │   └── ...
    │   │   ├── dto/
    │   │   │   ├── request/
    │   │   │   └── response/
    │   │   ├── entity/
    │   │   │   ├── User.java
    │   │   │   ├── Staff.java
    │   │   │   ├── House.java
    │   │   │   ├── Booking.java
    │   │   │   └── ...
    │   │   ├── repository/
    │   │   ├── service/
    │   │   ├── domain/       # enum/value types khi hữu ích
    │   │   └── HousekeepingManagementSystemApplication.java
    │   └── resources/
    │       ├── templates/    # Thymeleaf
    │       ├── static/       # Bootstrap/CSS/JS/images
    │       └── db/migration/ # SQL Server + Flyway
    └── test/java/...
```

## Trách nhiệm các layer

- `config/`: cấu hình Spring Security, datasource/Flyway, session và tích hợp hạ tầng. Không chứa quy tắc nghiệp vụ.
- `controller/`: tiếp nhận request MVC, binding/validation cấu trúc, gọi service rồi trả view/redirect/response. Controller mỏng, không truy cập repository trực tiếp.
- `dto/request/`: dữ liệu form/request và validation ở ranh giới ứng dụng.
- `dto/response/`: view model hoặc response chỉ khi cần. Không đưa password hash hay toàn bộ entity graph ra ngoài.
- `entity/`: ánh xạ JPA cho đúng 15 bảng nghiệp vụ trong Data Dictionary; enum/domain types có thể giữ ở `domain/`.
- `repository/`: truy vấn SQL Server, projections và khóa/lock dữ liệu; không điều phối luồng nghiệp vụ.
- `service/`: use case, business rules, ownership checks và `@Transactional` boundaries.

Luồng phụ thuộc chính:

```text
controller -> service -> repository -> SQL Server
                 └----> entity/domain types
```

Provider PayOS phải nằm sau lớp tích hợp rõ ràng. Chỉ tạo `PaymentGateway` interface nếu nó giúp kiểm thử hoặc cô lập vendor thực tế; DTO/SDK và exception PayOS không được rò vào business contract. Không thêm `IService` cho mọi service, generic repository, fake implementation, hoặc module Maven mới.

## Áp dụng với code hiện tại

Các route hiện được chia theo controller của luồng nghiệp vụ: `AuthController`, `BookingController`, `PaymentController`, `HouseController`, `StaffController`, `PortalAdminController`, cùng các controller chuyên trách Leave, Staff Profile và Company Settings. `PortalController` chỉ giữ trang chủ, danh mục dịch vụ công khai và điều hướng theo role. Business operation được đặt trong service theo domain như `BookingReservationService`, `BookingCancellationService`, `PaymentService`, `StaffAvailabilityService`, `StaffManagementService`, `ServiceManagementService` và `StaffFollowService`; không đưa nghiệp vụ mới vào controller tổng hợp.

`domain/entity/` và một số repository cũ cho mô hình Customer/Admin riêng, WorkSchedule, FavoriteStaff, PaymentAttempt và Evidence vẫn còn trong lịch sử source nhưng không thuộc mô hình chạy hiện tại. `HousekeepingManagementSystemApplication` chỉ scan `com.housekeeping.management.entity`; các repository legacy có `@NoRepositoryBean`, vì vậy không được coi là contract để phát triển tiếp. Không import chúng vào feature mới. Việc dọn các file legacy còn lại cần thực hiện trong đợt được cho phép rõ ràng; không sửa hoặc xóa migration lịch sử để làm sạch package.

Không đổi framework hoặc giao thức: đây vẫn là một ứng dụng Java 21/Spring Boot, Spring MVC + Thymeleaf + Bootstrap trong một Maven project, dùng Spring Data JPA/Hibernate, SQL Server, Flyway và Spring Security HTTP Session. Đây không phải REST API riêng hay React SPA; route MVC hiện có chỉ đổi khi use case yêu cầu.

## Quy tắc thiết kế và review

1. Trước feature đáng kể, tự rà soát Responsibility, Coupling, Naming, Extensibility, Simplicity, Consistency và Testability.
2. Ưu tiên class/method có tên theo domain/hành vi như `BookingService`, `createBooking`, `confirmCashReceived`, `isActive`; tránh `CommonService`, `Utils`, `processData`.
3. Mỗi class/method có một trách nhiệm mạch lạc; không tách nhỏ máy móc theo số dòng hoặc tạo abstraction nếu chưa có lợi ích cụ thể.
4. Service sở hữu transaction và invariants. Repository chỉ truy cập dữ liệu. Controller không chứa SQL/BR/vendor workflow.
5. Bảo toàn lỗi và ngữ cảnh hữu ích; không nuốt exception, không log hoặc trả secret.
6. Tái cấu trúc từng lát cắt đầy đủ, giữ templates và CSS hữu ích, không sửa code ngoài phạm vi.
7. Test hành vi: boundary, ownership, trạng thái, lỗi và concurrency tương ứng. Chỉ báo test đã đạt sau khi chạy thật.
8. Không sửa V1/V2/V3 migration đã áp dụng. Schema mới phát triển theo baseline/location Flyway riêng như tài liệu database; tuyệt đối không reset hoặc drop database người dùng.
