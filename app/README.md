> **Document:** Application Directory Guide\
> **File:** `app/README.md`\
> **Version:** v2.0.0\
> **Created:** 2026-06-14\
> **Last Updated:** 2026-10-24\
> **Status:** Draft\

# Ứng dụng Housekeeping

Mã nguồn ứng dụng nằm tại [`housekeeping-management-system/`](housekeeping-management-system/). Ứng dụng dùng Java 21, Spring Boot, Spring MVC, Thymeleaf, Bootstrap, JPA/Hibernate, SQL Server và Flyway trong một Maven project.

## Package Java

Theo [hướng dẫn package](../docs/architecture/java-package-structure.md), code mới được tổ chức theo `config/`, `controller/`, `dto/request/`, `dto/response/`, `entity/`, `repository/`, `service/`; giữ `domain/` cho enum/value type khi phù hợp. Không phát triển thêm nghiệp vụ trong các lớp `Portal*` cũ khi lát cắt tương ứng đã được chuyển.

## Tài nguyên giao diện

- `src/main/resources/templates/`: view Thymeleaf.
- `src/main/resources/static/`: CSS, JavaScript, ảnh và thư viện giao diện cục bộ.
- `src/main/resources/db/refactor/`: Flyway baseline của schema SQL Server mới.
- `src/main/resources/db/migration/`: migration lịch sử; không sửa V1/V2/V3 đã áp dụng.

Chạy demo theo [README ở repo root](../README.md). Chỉ cấu hình `.env` cho database development riêng; không commit hoặc chia sẻ thông tin kết nối.
