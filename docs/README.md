> **Document:** Documentation Guide\
> **File:** `docs/README.md`\
> **Version:** v2.0.0\
> **Created:** 2026-06-14\
> **Last Updated:** 2026-10-24\
> **Status:** Active\

# Tài Liệu Dự Án

Lưu tài liệu dự án được duy trì tại đây và liên kết tới nguồn chuẩn cho từng chủ đề. Yêu cầu sản phẩm ở PRD; điều kiện kiểm chứng ở SRS; cấu trúc bảng SQL Server ở Data Dictionary. Không để các bản sao mô tả những quyết định khác nhau.

## Tài liệu chuẩn

- [PRD](requirements/PRD.md): phạm vi sản phẩm, vai trò và 12 module.
- [SRS](requirements/SRS.md): quy tắc và acceptance criteria có thể kiểm thử.
- [Từ điển dữ liệu SQL Server](requirements/SQL_SERVER_DATA_DICTIONARY.md): 15 bảng nghiệp vụ và quy tắc mapping.
- [Cấu trúc package Java](architecture/java-package-structure.md): MVC/N-Layer và trách nhiệm package.
- [Hướng dẫn kết nối giao diện và backend](frontend-integration.md): các route/form MVC sau khi đồng bộ với SRS.

- `requirements/`: yêu cầu sản phẩm, SRS và data dictionary.
- `architecture/`: cấu trúc package và quyết định kiến trúc.
- `reports/`: các báo cáo dự án dự định duy trì trong repository.

Chỉ thêm khu vực tài liệu khác khi dự án có nhu cầu. Với repository này, hãy theo stack SQL Server và cấu trúc ứng dụng đã chốt trong README, Data Dictionary và hướng dẫn package; không áp dụng lựa chọn công nghệ chung của template cũ.

## Quy Tắc Tài Liệu

- Dùng liên kết tương đối trong repository.
- Giữ tài liệu thống nhất với các quyết định đã duyệt và bằng chứng triển khai.
- Dùng metadata phù hợp với từng loại artifact. Tài liệu dự án có thể dùng khối metadata tài liệu; skill và workflow của Agent giữ định dạng riêng.
- Không commit báo cáo hoặc log local của Agent trong `.agents/outputs/`.
- Tuân thủ quy tắc bảo vệ đường dẫn trong `AGENTS.md` và `.agents/POLICY.md`; không kiểm tra nội dung bị bảo vệ hoặc liên kết trỏ vào đó.
