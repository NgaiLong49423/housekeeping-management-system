> **Document:** Local Runtime Setup\
> **File:** `docs/local-setup.md`\
> **Version:** v1.8.0\
> **Created:** 2026-10-16\
> **Last Updated:** 2026-10-25\
> **Status:** Deprecated\

# Hướng dẫn local cũ (đã ngừng sử dụng)

Tài liệu và launcher trong phiên bản này thuộc mô hình database cũ. Chúng dùng các bảng `Customer`, `Admin`, `Work_Schedule`, `Payment_Attempt` và cấu hình `.runtime` riêng của máy trước khi baseline 15 bảng được chốt. Không dùng hướng dẫn này để khởi động ứng dụng hoặc thao tác với SQL Server.

Để chạy phiên bản hiện tại, làm theo [hướng dẫn demo](demo-setup.md) và [hướng dẫn database](../database/README.md). Ứng dụng hiện dùng SQL Server, Flyway tại `classpath:db/refactor`, và profile `demo` chỉ dành cho database demo riêng `home_cleaning_demo`.

Các launcher/seed/verify cũ trong `scripts/` đã bị khóa để tránh ghi nhầm dữ liệu hoặc chạy luồng không còn khớp nghiệp vụ. Chúng không xóa, reset hay chuyển đổi database. Dùng Maven/IntelliJ theo hướng dẫn demo hiện hành.
