> **Document:** Local Runtime Setup\
> **File:** `docs/local-setup.md`\
> **Version:** v1.8.0\
> **Created:** 2026-10-16\
> **Last Updated:** 2026-10-25\
> **Status:** Deprecated\

# Hướng dẫn local cũ (đã ngừng sử dụng)

Tài liệu và launcher trong phiên bản này thuộc mô hình database cũ. Chúng dùng các bảng `Customer`, `Admin`, `Work_Schedule`, `Payment_Attempt` và cấu hình `.runtime` riêng của máy trước khi baseline 15 bảng được chốt. Không dùng hướng dẫn này để khởi động ứng dụng hoặc thao tác với SQL Server.

Để chạy phiên bản hiện tại trên database chính, làm theo [README](../README.md) và [hướng dẫn database](../database/README.md). Cấu hình mặc định dùng SQL Server `Housekeeping_Database` với Flyway tại `classpath:db/primary`; profile demo tùy chọn dùng database và lịch sử Flyway riêng.

Các launcher/seed/verify cũ trong `scripts/` đã bị khóa để tránh ghi nhầm dữ liệu hoặc chạy luồng không còn khớp nghiệp vụ. Chúng không xóa, reset hay chuyển đổi database. Dùng Maven/IntelliJ theo hướng dẫn chạy database chính.
