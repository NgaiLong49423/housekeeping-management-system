> **Document:** Database Starter Guide\
> **File:** `database/README.md`\
> **Version:** v1.0.0\
> **Created:** 2026-06-14\
> **Last Updated:** 2026-10-13\
> **Status:** Draft\

# Hướng Dẫn Thư Mục Database

## Lựa Chọn Đã Chốt Cho HSF302

- Hệ quản trị cơ sở dữ liệu: Microsoft SQL Server.
- `schema.sql` hiện là file rỗng (0 byte), vì vậy chưa mô tả schema và không xác minh được thuộc tính, PK/FK, nullability, cardinality hoặc constraint.
- Chưa có quyết định về công cụ migration trong baseline này. Không tự tạo hoặc sửa schema/migration trong phạm vi đồng bộ tài liệu.

Thư mục này có thể chứa các script cơ sở dữ liệu của dự án. Các file SQL khung không mặc định sản phẩm cơ sở dữ liệu hoặc cách quản lý migration; lựa chọn DBMS đã chốt riêng cho HSF302 như trên.

## Các File Gợi Ý

- `schema.sql`: khung schema tùy chọn, cần điều chỉnh theo cú pháp của cơ sở dữ liệu đã chọn.
- `sample-data.sql`: dữ liệu mẫu tổng hợp nếu cần; không dùng dữ liệu cá nhân hoặc dữ liệu production.
- `queries.sql`: các truy vấn tùy chọn phục vụ phát triển hoặc xác minh.

Xóa hoặc thay thế file không phù hợp với quy trình cơ sở dữ liệu đã chọn. Nếu dự án sử dụng công cụ migration, hãy ghi rõ nguồn sự thật và vòng đời của nó tại đây; không duy trì nhiều lịch sử schema cạnh tranh nếu chưa có kế hoạch đồng bộ cụ thể.

Không đưa thông tin xác thực hoặc dữ liệu riêng tư có thật vào script. Cập nhật tài liệu dự án liên quan khi schema hoặc quy trình cơ sở dữ liệu thay đổi.
