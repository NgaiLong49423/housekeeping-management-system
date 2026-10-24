> **Document:** SQL Server Data Dictionary\
> **File:** `docs/requirements/SQL_SERVER_DATA_DICTIONARY.md`\
> **Version:** v1.0.0\
> **Created:** 2026-10-24\
> **Last Updated:** 2026-10-24\
> **Status:** Active\
# Từ điển dữ liệu SQL Server — 15 bảng

**Logical schema:** 15 bảng nghiệp vụ đã chốt. **Physical DBMS:** Microsoft SQL Server. Đây là tài liệu mô tả thiết kế SQL Server; **không** dùng bản `.sql` MySQL từ hồ sơ cũ để chạy trực tiếp.

## Mapping kiểu dữ liệu đề xuất
| Logical | SQL Server |
|---|---|
| BIGINT auto-increment PK | `BIGINT IDENTITY(1,1)` + `PRIMARY KEY` |
| VARCHAR string tiếng Việt | `NVARCHAR(n)` / `NVARCHAR(MAX)` khi phù hợp |
| TEXT | `NVARCHAR(MAX)` |
| BOOLEAN | `BIT NOT NULL DEFAULT (0)/(1)` |
| DATETIME | `DATETIME2(0)` hoặc độ chính xác đã chọn đồng bộ với Java |
| TIME | `TIME(0)` |
| DATE | `DATE` |
| Money | `DECIMAL(12,2)` (quyết định logical; không đổi sang integer-scale mà không giải thích) |
| Enum | `VARCHAR(20)` với `CHECK` |

Recommended Java: `Long`, `BigDecimal`, `LocalDate`, `LocalTime`, `LocalDateTime` cho local business time; xác định múi giờ ở application boundary trước khi code theo thời gian. Lat/lng `BigDecimal` (`DECIMAL(10,7)`). Với collations/email uniqueness kiểm tra case-insensitivity thực tế trên SQL Server; tránh phụ thuộc vào mặc định môi trường. Tên physical bảng và cột giữ nguyên theo logical contract trong DDL và `@Table/@Column`.

## 1. Users
`user_id PK BIGINT IDENTITY`, `full_name NVARCHAR(150) NOT NULL`, `email NVARCHAR(255) NOT NULL UNIQUE`, `password_hash NVARCHAR(255) NOT NULL`, `phone NVARCHAR(20) NOT NULL`, `role VARCHAR(20) NOT NULL CHECK CUSTOMER/STAFF/ADMIN`, `status VARCHAR(20) NOT NULL DEFAULT ACTIVE CHECK ACTIVE/INACTIVE`.
**Không** có created_at/updated_at. Backend role authorization.

## 2. Staff
`staff_id PK`, `user_id BIGINT NOT NULL UNIQUE FK -> Users`, `staff_type VARCHAR(20) NOT NULL CHECK FULL_TIME/PART_TIME`. Chỉ Users.role=STAFF mới có bản ghi Staff (service authorization; ordinary FK không enforce role).

## 3. Staff_Profiles
`profile_id PK`, `staff_id BIGINT NOT NULL UNIQUE FK -> Staff`, `avatar_url NVARCHAR(500) NULL`, `bio NVARCHAR(MAX) NULL`. Không timestamp.

## 4. Services
`service_id PK`, `service_name NVARCHAR(150) NOT NULL`, `description NVARCHAR(MAX) NULL`, `image_url NVARCHAR(500) NULL`, `price DECIMAL(12,2) NOT NULL CHECK price>=0`, `duration_minutes INT NOT NULL CHECK>0`, `status VARCHAR(20) NOT NULL DEFAULT ACTIVE CHECK ACTIVE/INACTIVE`. Một ảnh URL duy nhất.

## 5. Company_Settings
`company_id PK`, `company_address NVARCHAR(500) NOT NULL`, `latitude DECIMAL(10,7) NOT NULL`, `longitude DECIMAL(10,7) NOT NULL`, `service_radius_km DECIMAL(6,2) NOT NULL CHECK >0`. Chỉ 1 row theo nghiệp vụ; enforce qua application/bootstrap hoặc constraint hợp lý, không phát minh company marketplace. CHECK lat between -90/+90, lng -180/+180.

## 6. Company_Operating_Hours
`operating_hour_id PK`, `company_id BIGINT NOT NULL FK`, `day_of_week INT NOT NULL CHECK BETWEEN 1 AND 7` (Mon=1), `opening_time TIME(0) NULL`, `closing_time TIME(0) NULL`, `is_closed BIT NOT NULL DEFAULT 0`; `UNIQUE(company_id,day_of_week)`. Closed=>hours null; open=>non-null open<close. 7 rows do service/bootstrap enforce.

## 7. Houses
`house_id PK`, `customer_id BIGINT NOT NULL FK -> Users`, `house_name NVARCHAR(100) NOT NULL`, `address NVARCHAR(500) NOT NULL`, `latitude DECIMAL(10,7) NOT NULL`, `longitude DECIMAL(10,7) NOT NULL`, `address_note NVARCHAR(500) NULL`, `is_deleted BIT NOT NULL DEFAULT 0`. Không timestamps. `Bookings.house_id` giữ FK sau soft-delete.

## 8. Staff_Availability
`availability_id PK`, `staff_id BIGINT NOT NULL FK -> Staff`, `work_date DATE NOT NULL`, `start_time TIME(0) NOT NULL`, `end_time TIME(0) NOT NULL`, `created_at DATETIME2(0) NOT NULL DEFAULT SYSDATETIME()`. CHECK start<end; no updated_at. **Không phải lịch tuần**; overlap kiểm tra bằng transaction/backend.

## 9. Staff_Leaves
`leave_id PK`, `staff_id BIGINT NOT NULL FK -> Staff`, `start_date DATE NOT NULL`, `end_date DATE NOT NULL`, `reason NVARCHAR(500) NOT NULL`, `status VARCHAR(20) NOT NULL DEFAULT PENDING CHECK PENDING/APPROVED/REJECTED`, `created_at DATETIME2(0) NOT NULL DEFAULT SYSDATETIME()`, `reviewed_by BIGINT NULL FK -> Users`, `reviewed_at DATETIME2(0) NULL`. CHECK start<=end; PENDING reviewed fields NULL; APPROVED/REJECTED reviewed fields non-NULL.

## 10. Bookings
`booking_id PK`, `customer_id BIGINT NOT NULL FK -> Users`, `staff_id BIGINT NOT NULL FK -> Staff` (**current**), `original_staff_id BIGINT NOT NULL FK -> Staff` (**immutable**), `house_id BIGINT NOT NULL FK -> Houses`, `address_snapshot NVARCHAR(500) NOT NULL`, `latitude_snapshot DECIMAL(10,7) NOT NULL`, `longitude_snapshot DECIMAL(10,7) NOT NULL`, `address_note_snapshot NVARCHAR(500) NULL`, `start_time DATETIME2(0) NOT NULL`, `end_time DATETIME2(0) NOT NULL`, `total_duration_minutes INT NOT NULL`, `total_amount DECIMAL(12,2) NOT NULL`, `area_note NVARCHAR(MAX) NULL`, `status VARCHAR(20) NOT NULL DEFAULT CONFIRMED CHECK CONFIRMED/AWAITING_PAYMENT/COMPLETED/CANCELLED`, `cancelled_at DATETIME2(0) NULL`, `cancelled_by BIGINT NULL FK -> Users`, `cancellation_reason NVARCHAR(500) NULL`, `created_at DATETIME2(0) NOT NULL DEFAULT SYSDATETIME()`.
CHECK end_time>start_time, nonnegative totals, valid coordinate range; status CANCELLED means all cancel fields present, others cancel fields NULL. No updated_at/work_completed_at/history.

## 11. Booking_Details
`booking_detail_id PK`, `booking_id BIGINT NOT NULL FK -> Bookings`, `service_id BIGINT NOT NULL FK -> Services`, `service_name_snapshot NVARCHAR(150) NOT NULL`, `unit_price DECIMAL(12,2) NOT NULL CHECK>=0`, `duration_minutes INT NOT NULL CHECK>0`. No sequence_no, execution_order, service-specific time. Backend guarantees >=1 details per booking and snapshots/totals in one transaction.

## 12. Booking_Images
`image_id PK`, `booking_id BIGINT NOT NULL FK -> Bookings`, `image_url NVARCHAR(500) NOT NULL`, `uploaded_at DATETIME2(0) NOT NULL DEFAULT SYSDATETIME()`. No uploaded_by, no booking_detail_id.

## 13. Payments
`payment_id PK`, `booking_id BIGINT NOT NULL FK -> Bookings`, `amount DECIMAL(12,2) NOT NULL CHECK>=0`, `payment_method VARCHAR(20) NOT NULL CHECK CASH/ONLINE`, `status VARCHAR(20) NOT NULL DEFAULT PENDING CHECK PENDING/SUCCESS/FAILED`, `transaction_code NVARCHAR(255) NULL`, `created_at DATETIME2(0) NOT NULL DEFAULT SYSDATETIME()`, `paid_at DATETIME2(0) NULL`. SUCCESS iff paid_at not null; not SUCCESS => paid_at null. One Booking has many attempts. **At most one SUCCESS per Booking**, enforce by SQL Server filtered unique index `UNIQUE(booking_id) WHERE status='SUCCESS'` (implementation recommendation, compatible with schema) and service transaction/locking. No payment_gateway/confirmed_by/updated_at or 1:1 booking uniqueness.

## 14. Feedbacks
`feedback_id PK`, `booking_id BIGINT NOT NULL UNIQUE FK -> Bookings`, `customer_id BIGINT NOT NULL FK -> Users`, `content NVARCHAR(MAX) NOT NULL`, `admin_reply NVARCHAR(MAX) NULL`, `status VARCHAR(20) NOT NULL DEFAULT OPEN CHECK OPEN/RESOLVED`. No rating, created_at, replied_at.

## 15. Staff_Follows
`customer_id BIGINT NOT NULL FK -> Users`, `staff_id BIGINT NOT NULL FK -> Staff`, `created_at DATETIME2(0) NOT NULL DEFAULT SYSDATETIME()`, `PRIMARY KEY(customer_id,staff_id)`; unfollow=DELETE pair.

## Referential integrity & SQL Server notes
- For FK child tables `Booking_Details`, `Booking_Images`, `Payments`, `Feedbacks` -> Bookings: `ON DELETE NO ACTION` (SQL Server analog of RESTRICT). Also preserve history on Houses/Staff/Services via appropriate FK protection, no cascade that deletes booking history.
- SQL Server **multiple cascade paths** risk: prefer NO ACTION/restrict where appropriate, avoid delete cascades by default.
- Idempotent service-layer validation: check DB locks/transactions for staff slot collision and availability mutation; use explicit transaction boundaries.
- MySQL `AUTO_INCREMENT`, backticks, `BOOLEAN`, `CURRENT_TIMESTAMP`, `LIMIT`, `ON DELETE RESTRICT` should not be copied verbatim to SQL Server.
- Hibernate `ddl-auto=validate`; Flyway applies schema. Fresh schema migration path must be separated from existing production schema. Do **not** modify existing applied versioned Flyway migrations in-place on a shared DB.
- Flyway's `flyway_schema_history` is a tooling table, not a 16th business Entity.
- The entity mapping and migration must match column types and nullability. Do not silently add auditing, gateway IDs, admin ID or rating fields just to make a vendor SDK easier.

## Concurrency focus
Serialize booking creation on same Staff via reliable row lock/transaction/isolation or equivalently proven mechanism. Serialize Payment success callbacks/confirmation on same Booking and enforce filtered unique success. Explicitly test SQL Server behavior; 'query then insert' without a lock is unsafe.
