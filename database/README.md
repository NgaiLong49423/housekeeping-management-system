> **Document:** Database Starter Guide\
> **File:** `database/README.md`\
> **Version:** v1.0.0\
> **Created:** 2026-06-14\
> **Last Updated:** 2026-10-13\
> **Status:** Draft\

# Hướng Dẫn Thư Mục Database

## Lựa Chọn Đã Chốt Cho HSF302

- Hệ quản trị cơ sở dữ liệu: Microsoft SQL Server.
- Công cụ quản lý thay đổi schema: Flyway, dùng để quản lý các database migration có phiên bản.
- `schema.sql` là schema snapshot/reference artifact, không được dùng để runtime bootstrap hoặc làm nguồn migration thứ hai.
- Flyway migrations là authoritative source duy nhất để tạo và thay đổi schema. Runtime migrations nằm tại `app/housekeeping-management-system/src/main/resources/db/migration/`; migration đầu tiên dùng tên `V1__create_initial_schema.sql`.

Thư mục này có thể chứa các script cơ sở dữ liệu của dự án. Các file SQL khung không mặc định sản phẩm cơ sở dữ liệu; SQL Server và Flyway đã được chọn riêng cho HSF302 như trên.

## Các File Gợi Ý

- `schema.sql`: snapshot/reference của schema sau migration; không dùng làm cơ chế runtime bootstrap.
- `sample-data.sql`: dữ liệu mẫu tổng hợp nếu cần; không dùng dữ liệu cá nhân hoặc dữ liệu production.
- `queries.sql`: các truy vấn tùy chọn phục vụ phát triển hoặc xác minh.

Chỉ Flyway quản lý vòng đời thay đổi schema. Giữ `schema.sql` đồng bộ với trạng thái schema đã migrate để tham khảo; không duy trì một luồng khởi tạo hoặc lịch sử thay đổi schema độc lập.

Không đưa thông tin xác thực hoặc dữ liệu riêng tư có thật vào script. Cập nhật tài liệu dự án liên quan khi schema hoặc quy trình cơ sở dữ liệu thay đổi.
