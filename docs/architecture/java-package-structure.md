> **Document:** Java Package Structure Guide\
> **File:** `docs/architecture/java-package-structure.md`\
> **Version:** v3.4.0\
> **Created:** 2026-10-24\
> **Last Updated:** 2026-11-11\
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
    │   │   │   ├── entity/   # DTO ánh xạ hai chiều với JPA entity
    │   │   │   ├── request/
    │   │   │   └── response/
    │   │   ├── entity/
    │   │   │   ├── User.java
    │   │   │   ├── Staff.java
    │   │   │   ├── House.java
    │   │   │   ├── Booking.java
    │   │   │   └── ...
    │   │   ├── repository/
    │   │   ├── security/    # mã hóa/giải mã dữ liệu nhạy cảm
    │   │   ├── service/
    │   │   │   ├── impl/
    │   │   ├── integration/ # adapter/client theo hệ thống bên ngoài
    │   │   ├── startup/     # tác vụ vòng đời khi ứng dụng khởi động
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
- `entity/`: ánh xạ các bảng nghiệp vụ dùng JPA trong Data Dictionary. Bảng nghiệp vụ được truy cập bằng ứng dụng phải có entity phù hợp; không bỏ entity để viết JDBC riêng. Enum/domain types có thể giữ ở `domain/`.
- `repository/`: truy cập dữ liệu nghiệp vụ bằng Spring Data JPA; repository là interface (thường extends `JpaRepository`) và được service implementation inject trực tiếp. Ưu tiên derived query theo tên; dùng JPQL, projection, `@Lock` và `@Modifying` khi truy vấn phức tạp hoặc cần khóa/cập nhật có điều kiện. Không dùng `JdbcTemplate`, JDBC trực tiếp, native SQL hay câu SQL để đọc/ghi dữ liệu nghiệp vụ trong mã Java. Ngoại lệ hẹp: `config/SqlServerDatabaseInitializer` được dùng JDBC để tạo database khi khởi động; không dùng nó cho truy vấn ứng dụng. SQL quản lý schema vẫn nằm trong migration Flyway và công cụ database được phê duyệt. Không tạo `repository/impl/`.
- `service/`: interface của use case/nghiệp vụ, là contract cho controller và các service khác.
- `service/impl/`: implementation được Spring quản lý bằng `@Service`; chứa business rules, ownership checks và `@Transactional` boundaries. Dùng constructor injection. Service implementation chỉ gọi repository thuộc domain mình sở hữu; khi cần nghiệp vụ domain khác, phụ thuộc service interface của domain đó. Với dữ liệu chỉ đọc liên domain, dùng query service của domain sở hữu dữ liệu và giữ query service không phụ thuộc ngược các use case để tránh vòng bean. Một use case có thể dùng nhiều repository trong cùng domain.
- `integration/`: adapter/client kết nối hệ thống bên ngoài, nhóm theo provider như `vietmap/` và `vnpay/`. Package này không chứa service contract nghiệp vụ; DTO tích hợp của provider không được đưa ra ngoài adapter nếu service/controller chỉ cần kiểu dữ liệu trung lập.
- `startup/`: component chỉ chạy theo vòng đời khởi động như đối soát metadata và file lưu trữ; không đặt chúng trong `service/` hoặc `service/impl/` nếu chúng không thực hiện use case nghiệp vụ.
- `security/`: primitive bảo mật hạ tầng được inject vào service, không chứa use case hoặc quyền truy cập dữ liệu. Ví dụ mã hóa AES-GCM field-level; khóa bí mật luôn lấy từ cấu hình môi trường/vault, không commit vào repository.
- Mỗi JPA entity và embeddable có DTO tương ứng trong `dto/entity/` với `convertEntityToDTO` và `convertDTOToEntity`. Converter chỉ chuyển dữ liệu; gom các getter lặp vào biến cục bộ, không đọc lặp chuỗi getter. Entity quan hệ được biểu diễn bằng DTO. Service phân giải, xác thực và gán entity quan hệ; converter không tự truy vấn, không tự tạo entity quan hệ chỉ từ ID và không giữ quy tắc nghiệp vụ. Khi cập nhật, converter có thể nhận entity đang được quản lý làm đích để giữ persistence context. DTO User không chuyển password hash ra ngoài; converter nhận hash đã mã hóa riêng khi tạo entity. Mọi converter cần test hai chiều, nested DTO, null/default và trường hợp bảo mật phù hợp.

Luồng phụ thuộc chính:

```text
controller -> service -> repository -> SQL Server
                 └----> entity/domain types
```

Thanh toán trực tuyến được expose qua contract chung `OnlinePaymentService`; implementation hiện tại `VnpayPaymentServiceImpl` dùng `integration/vnpay/VnpayGatewayClient` và `VnpaySignature`. Tích hợp địa chỉ dùng `integration/vietmap/VietmapClient`; service contract trả `LocationSuggestion` thay vì kiểu lồng provider. Controller phụ thuộc contract chung; tên nhà cung cấp chỉ nằm trong service implementation, integration adapter và cấu hình giao thức. Chỉ tạo thêm interface gateway nếu nó giúp kiểm thử hoặc cô lập vendor thực tế; tham số, chữ ký và exception VNPay không được rò vào business contract chung. Mọi loại có hậu tố `Service`, bao gồm service lưu trữ tệp, phải có interface trong `service/` và implementation tương ứng trong `service/impl/`. Gateway/client và reconciler không mang hậu tố `Service` nếu không phải service contract; reconciler vòng đời ứng dụng thuộc `startup/`. Không thêm generic repository, fake implementation hoặc module Maven mới.

## Áp dụng với code hiện tại

Các route hiện được chia theo controller của luồng nghiệp vụ: `AuthController`, `BookingController`, `PaymentController`, `HouseController`, `StaffController`, `PortalAdminController`, cùng các controller chuyên trách Leave, Staff Profile và Company Settings. `PortalController` chỉ giữ trang chủ, danh mục dịch vụ công khai và điều hướng theo role. Business operation được khai báo bằng service interface theo domain như `BookingReservationService`, `BookingCancellationService`, `PaymentService`, `StaffAvailabilityService`, `StaffManagementService`, `ServiceManagementService` và `StaffFollowService`; Spring inject implementation tương ứng trong `service/impl/`. `AccountAuthorizationService` dùng chung để xác minh Admin còn hoạt động ở ranh giới service. Khi phối hợp domain, gọi interface của domain đó thay vì dùng trực tiếp repository của domain khác. Không đưa nghiệp vụ mới vào controller tổng hợp.

Các use case tự phục vụ của Staff nhận `userId` từ principal đăng nhập rồi tự tìm bản ghi Staff liên kết bên trong service; không truyền `staffId` do request cung cấp làm danh tính người thao tác. `PortalUser.accountId` vẫn được dùng cho những màn hình cần mã Customer/Staff domain, còn `PortalUser.userId` là khóa của bảng `Users`.

`domain/entity/` và một số repository cũ cho mô hình Customer/Admin riêng, WorkSchedule, FavoriteStaff, PaymentAttempt và Evidence vẫn còn trong lịch sử source nhưng không thuộc mô hình chạy hiện tại. `HousekeepingManagementSystemApplication` chỉ scan `com.housekeeping.management.entity`; các repository legacy có `@NoRepositoryBean`, vì vậy không được coi là contract để phát triển tiếp. Không import chúng vào feature mới. Việc dọn các file legacy còn lại cần thực hiện trong đợt được cho phép rõ ràng; không sửa hoặc xóa migration lịch sử để làm sạch package.

Không đổi framework hoặc giao thức: đây vẫn là một ứng dụng Java 21/Spring Boot, Spring MVC + Thymeleaf + Bootstrap trong một Maven project, dùng Spring Data JPA/Hibernate, SQL Server, Flyway và Spring Security HTTP Session. Đây không phải REST API riêng hay React SPA; route MVC hiện có chỉ đổi khi use case yêu cầu.

## Quy tắc thiết kế và review

1. Trước feature đáng kể, tự rà soát Responsibility, Coupling, Naming, Extensibility, Simplicity, Consistency và Testability.
2. Ưu tiên class/method có tên theo domain/hành vi như `BookingService`, `createBooking`, `confirmCashReceived`, `isActive`; tránh `CommonService`, `Utils`, `processData`.
3. Mỗi class/method có một trách nhiệm mạch lạc; không tách nhỏ máy móc theo số dòng hoặc tạo abstraction nếu chưa có lợi ích cụ thể.
4. Service sở hữu transaction và invariants. Repository JPA chỉ truy cập dữ liệu bằng derived query hoặc JPQL. Controller không chứa câu truy vấn dữ liệu, BR hay vendor workflow.
5. Bảo toàn lỗi và ngữ cảnh hữu ích; không nuốt exception, không log hoặc trả secret.
6. Tái cấu trúc từng lát cắt đầy đủ, giữ templates và CSS hữu ích, không sửa code ngoài phạm vi.
7. Test hành vi: boundary, ownership, trạng thái, lỗi và concurrency tương ứng. Chỉ báo test đã đạt sau khi chạy thật.
8. Không sửa V1/V2/V3 migration đã áp dụng. Schema mới phát triển theo baseline/location Flyway riêng như tài liệu database; tuyệt đối không reset hoặc drop database người dùng.
