> **Tài liệu:** Hướng dẫn cấu trúc package Java\
> **Tệp:** `docs/architecture/java-package-structure.md`\
> **Phiên bản:** v1.0.1\
> **Ngày tạo:** 2026-10-24\
> **Cập nhật lần cuối:** 2026-10-24\
> **Trạng thái:** Đang áp dụng\

# Hướng dẫn cấu trúc package Java

Tài liệu này giúp thành viên trong nhóm biết nên đặt code Java ở đâu và mỗi phần chịu trách nhiệm gì. Đây là cấu trúc định hướng mạnh cho code mới hoặc phần được chỉnh sửa đáng kể; không yêu cầu tên file phải giống hệt ví dụ. Hãy giữ thay đổi trong phạm vi công việc và đừng chuyển toàn bộ code cũ chỉ để cây thư mục trông giống mẫu.

## Cấu trúc đề xuất

```text
app/housekeeping-management-system/
└── src/main/java/com/housekeeping/management/
    ├── config/
    │   ├── SecurityConfig.java
    │   └── ...
    ├── controller/
    │   ├── AuthController.java
    │   ├── BookingController.java
    │   ├── StaffController.java
    │   ├── PaymentController.java
    │   └── ...
    ├── dto/
    │   ├── request/
    │   └── response/
    ├── entity/
    │   ├── User.java
    │   ├── Staff.java
    │   ├── House.java
    │   ├── Booking.java
    │   └── ...
    ├── repository/
    │   ├── UserRepository.java
    │   ├── BookingRepository.java
    │   └── ...
    ├── service/
    │   ├── AuthService.java
    │   ├── BookingService.java
    │   ├── PaymentService.java
    │   └── ...
    └── HousekeepingManagementSystemApplication.java
```

Tên trong ví dụ thể hiện vai trò phổ biến. Khi tên miền hoặc quy ước Spring MVC hiện tại của dự án khác đi, hãy ưu tiên tính nhất quán với code đang có.

## Mỗi package dùng để làm gì?

- `config/`: cấu hình ứng dụng và bảo mật. Không đặt quy tắc nghiệp vụ vào đây.
- `controller/`: nhận request HTTP/MVC, đọc dữ liệu form, gọi service, rồi chọn response hoặc view cần trả về. Controller nên mỏng; không tự xử lý nghiệp vụ đặt lịch hay thanh toán.
- `dto/request/` và `dto/response/`: các đối tượng dữ liệu đi vào và đi ra ở ranh giới web/API. Mặc định không dùng entity làm dữ liệu trả trực tiếp cho API.
- `entity/`: các JPA entity và quan hệ lưu trữ. Không để luồng xử lý nhiều bước hoặc logic trình bày trong entity.
- `repository/`: truy vấn và thao tác dữ liệu. Quy trình nghiệp vụ có transaction nên nằm trong service.
- `service/`: xử lý nghiệp vụ, phối hợp nhiều repository, kiểm tra điều kiện liên quan nhiều entity và xác định ranh giới transaction.

Dự án hiện có package `domain/` cho enum và kiểu miền nghiệp vụ; có thể tiếp tục dùng package này. Không cần chuyển các kiểu đó sang `entity/`. Chỉ thêm package dùng chung khi có nhu cầu cụ thể và trách nhiệm rõ ràng.

## Áp dụng vào ứng dụng MVC hiện tại

Ứng dụng hiện dùng Spring MVC với Thymeleaf và đã có các package `web/`, `domain/`, `repository/`, `service/`. Hãy xem các package này là cách tổ chức hiện có cho những trách nhiệm tương ứng:

| Vai trò trong cấu trúc đề xuất | Package/quy ước đang dùng |
|---|---|
| Controller MVC và kết nối với view | `web/` |
| JPA entity | `domain/entity/` |
| Enum và kiểu miền nghiệp vụ | `domain/` |
| Dữ liệu form và view | Các kiểu form/view model trong `web/` |
| Truy vấn và lưu dữ liệu | `repository/` |
| Luồng nghiệp vụ | `service/`; một số application service trong `web/` theo cách tổ chức đã có |

Khi thêm tính năng, trước tiên hãy xem một tính năng gần giống để làm theo cách đặt tên, validation, transaction và xử lý lỗi đang dùng. Không đổi `web/` thành `controller/`, không chuyển `domain/entity/` thành `entity/`, và không chuyển class giữa các package như một phần dọn dẹp ngoài phạm vi.

Nếu một nhiệm vụ yêu cầu chuyển đổi package rõ ràng, hãy lập kế hoạch riêng cho việc di chuyển class, cập nhật import, cấu hình, kiểm tra và tài liệu; bảo đảm ứng dụng MVC/Thymeleaf vẫn hoạt động.

## Hướng gọi giữa các layer

```text
controller -> service -> repository -> database
                 |
                 +-> entity và kiểu domain

DTO dùng ở ranh giới web; entity là dữ liệu miền/lưu trữ.
```

Repository có thể dùng entity hoặc projection chuyên cho truy vấn. Controller không nên chứa quy tắc đặt lịch, thanh toán, kiểm tra quyền sở hữu hay xếp lịch; các quy tắc này thuộc service. Đừng thêm layer, package, framework hoặc abstraction mới nếu chưa có nhu cầu thực tế.

## Quy tắc khi thay đổi code

1. Xem phần code gần nhất trước khi tạo class mới; làm theo quy ước đang dùng.
2. Đặt code vào package phù hợp với trách nhiệm, kể cả khi tên class khác ví dụ.
3. Tách dữ liệu request/response khỏi entity khi dữ liệu đi qua ranh giới web.
4. Giữ Spring MVC/Thymeleaf và các quyết định backend/database hiện tại, trừ khi người dùng duyệt một thay đổi kiến trúc rộng hơn.
5. Không chuyển toàn bộ package trong repo chỉ để cây thư mục giống hoàn toàn mẫu đề xuất.
6. Nếu có lý do cần tổ chức khác mẫu, giữ ngoại lệ trong phạm vi nhỏ và nêu lý do trong phần tóm tắt thay đổi.
