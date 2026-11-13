> **Document:** Database Guide\
> **File:** `database/README.md`\
> **Version:** v4.0.0\
> **Created:** 2026-06-14\
> **Last Updated:** 2026-11-13\
> **Status:** Draft\

# Hướng dẫn database

Microsoft SQL Server là database chính thức. Schema hiện tại có 29 bảng nghiệp vụ theo [Từ điển dữ liệu SQL Server](../docs/requirements/SQL_SERVER_DATA_DICTIONARY.md). V18 thêm thông tin món đồ Customer khai báo; V19 từng thêm phương án/phiếu việc; V25 giữ lịch sử migration nhưng loại bỏ bốn bảng phương án/phiếu việc và rút gọn luồng sự cố; V26 thêm mốc Staff hoàn tất để thực thi hạn phản ánh 24 giờ; V27 tách trạng thái đã quyết toán để cho phép xóa ciphertext tài khoản ngân hàng; V28 bổ sung quy trình Staff giải trình/kháng nghị và theo dõi thu hồi theo từng lần thực nhận; V29 đồng bộ nội dung và ảnh minh họa cho các phản ánh demo. V32 giữ ba bảng tài chính/hủy lịch sử; V36 thêm bảng nhật ký ETA. Hibernate dùng `ddl-auto=validate`; chỉ Flyway quản lý DDL.

## Opt-in automatic local reset

`DB_RESET_ON_STARTUP` defaults to `false`, preserving existing database contents. When explicitly set to `true`, before creating the datasource the application connects to `master`, disconnects existing users from the selected supported local database, drops it if present and creates it again. Flyway then rebuilds the schema. **All previous data and both old and new Flyway history tables in that database are deleted.** A failed migration does not restore the database deleted by the initializer.

Only `Housekeeping_Database` and `home_cleaning_demo` on `localhost`, `127.0.0.1` or `[::1]` can be reset. The SQL login needs ALTER/DROP/CREATE DATABASE permissions. Set `DB_RESET_ON_STARTUP=true` in the module `.env` only for an intentional disposable local reset; leave it false to preserve data or connect to a remote/shared database. The historical setup and manual recovery instructions below apply when automatic reset is disabled; no manual setup/reset script is required when it is enabled.

## Database demo tách biệt

1. Chỉ dùng môi trường SQL Server local dành cho demo/dev.
2. Chạy [`setup-demo.sql`](setup-demo.sql) bằng SSMS để tạo database mới `home_cleaning_demo` nếu chưa tồn tại. Script không tạo login hoặc chứa password.
3. Chạy ứng dụng với profile `demo`; Flyway áp dụng schema và dữ liệu mẫu từ `classpath:db/refactor` cùng migration dùng chung trong `classpath:db/shared`. Tài khoản, nhà và dịch vụ mẫu được ghi bởi V3; migration demo-only V7 tạo thêm một Booking để thử xác nhận đến. Migration V11 seed `manager@demo.gonnha.local` trong riêng `home_cleaning_demo`; migration V15 chuyển tài khoản đó thành vai trò MANAGER duy nhất. V20 tạo hai hồ sơ sự cố demo; V25 chuẩn hóa chúng thành một phản ánh đang xử lý và một đề xuất bồi thường đang chờ Customer, chưa giả lập Customer đồng ý hoặc Manager duyệt. V26 backfill mốc hoàn tất cho booking cũ, không sửa hồ sơ sự cố; V27 chuẩn hóa các khoản bồi thường đã chi để xóa ciphertext tài khoản. V28 không chuyển các quyết định Staff lịch sử thành kết luận theo quy trình mới và không tạo khoản hoàn trả mẫu. V29 gắn ảnh minh họa vào hai phản ánh, làm rõ đây là dữ liệu mẫu chưa xác nhận/duyệt/chi; component chỉ chạy trong profile `demo` chép file ảnh vào evidence storage. Nội dung phản ánh được tạo bởi migration, ảnh được cài lúc ứng dụng khởi động.

Tạo SQL login riêng và map vào database demo; cấp quyền DDL cần thiết để Flyway tạo schema. Lưu credentials trong `.env` cục bộ, không commit. Script không xóa database, không tạo hoặc reset login, không tạo schema history thủ công và không sửa database cũ. Nếu `home_cleaning_demo` đã có schema hoặc Flyway history không phù hợp, dừng lại và dùng database dev mới; không chạy `clean`, không drop và không sửa thủ công history.

## Flyway và lịch sử schema

- `app/housekeeping-management-system/src/main/resources/db/primary/` là baseline của database chính `Housekeeping_Database`; Flyway ghi nhận lịch sử mới trong `flyway_primary_schema_history` để không sửa hay repair lịch sử cũ. Flyway tạo baseline version `0` rồi áp dụng V1; migration V1 chỉ cho phép database chính/acceptance rỗng (ngoại trừ hai bảng lịch sử Flyway).
- `app/housekeeping-management-system/src/main/resources/db/refactor/` giữ baseline cho profile demo và bộ kiểm thử của profile đó; V7 chỉ seed Booking thử trong `home_cleaning_demo`.
- `app/housekeeping-management-system/src/main/resources/db/shared/` chứa migration version dùng chung sau baseline; cả profile mặc định và `demo` nạp các migration dùng chung mới nhất đến V41 (các số version có khoảng trống lịch sử). V5 thêm ngày hiệu lực cho phiên bản giờ hoạt động.
- Migration dùng chung V6 thêm thời điểm Staff xác nhận đến và báo sự cố trên `Bookings`; schema vẫn có 15 bảng nghiệp vụ. Hành vi ETA được mở rộng tại V36 và xử lý báo giờ đến trễ theo luồng V41.
- Migration V8 thêm ETA, phản hồi Customer, giờ bắt đầu việc và các trường giảm trễ; không thêm bảng. Đây là schema nền, được V36 mở rộng thành lịch sử ETA riêng và đồng thuận cho từng mốc cụ thể. V12 cho phép bắt đầu sau khi có xác nhận đã đến, kể cả trước giờ hẹn; hành vi xác minh được cập nhật tại V31 và V41. Mức giảm theo tier chỉ tính từ giờ đến thực tế đã xác minh/kê khai khi dịch vụ được thực hiện.
- Migration dùng chung V9 thêm rating và chủ đề cho `Feedbacks`, cho phép lời nhắn rỗng ở rating 4–5, giữ rating lịch sử là NULL và thêm trạng thái đã phản hồi trước khi resolve; không thêm bảng nghiệp vụ.
- Migration dùng chung V10 thêm hồ sơ khiếu nại, ảnh bằng chứng, lịch sử quyết định xử lý Staff và sổ khoản tiền; thêm cờ quản lý tạm thời và đánh dấu Payments ONLINE sandbox là mô phỏng. Tổng số bảng nghiệp vụ tăng từ 15 lên 19. Khoản tiền do Admin đề xuất, Manager duyệt và cần hóa đơn/biên nhận trước khi quyết toán.
- Migration dùng chung V11 chỉ seed Admin quản lý demo khi database hiện tại là `home_cleaning_demo`; không thay đổi dữ liệu hay schema của database chính.
- Migration dùng chung V12 thay `CK_Bookings_Work_Start`: work_started_at có thể trước start_time; Staff phải tự xác nhận đến, còn V31 cho phép Customer/Admin xác minh thời điểm đến để xử lý tranh chấp. V12 không thêm bảng; V36 thêm lịch sử ETA.
- Migration dùng chung V13 thêm thời điểm Customer báo Staff chưa đến; theo luồng V41, chỉ Customer sở hữu Booking và Admin xem được. Customer báo từ giờ hẹn khi việc chưa bắt đầu, kể cả khi Staff đã tự báo đến; báo cáo tạm khóa bắt đầu. Admin xác minh chưa tới hoặc giờ thực tế, rồi Customer chọn đợi hoặc hủy miễn phí. Báo cáo một mình không mở ngoại lệ hủy.
- Migration dùng chung V14 thêm bảng `Booking_Delay_Compensation_Tiers` với các mức mặc định 1–15 phút/5%, 16–30/10%, trên 30/20%; Admin chỉnh ngưỡng và phần trăm tại `/admin/settings`. Tier phải liên tiếp từ phút 1 và mức cuối không giới hạn. Booking đã ghi nhận mức giảm giữ nguyên giá trị đã lưu. Bỏ giới hạn phần trăm cũ trong `Bookings`; sau V14 có 20 bảng nghiệp vụ; V30 ép tier lưu tối đa 30% và service cũng áp trần này khi đọc cấu hình cũ.
- Migration dùng chung V15 chuyển Admin quản lý hiện tại thành role `MANAGER`, thay cờ V10 bằng vai trò hệ thống và filtered unique index để chỉ có một Manager. Manager kế thừa toàn quyền Admin, quản lý tài khoản Admin tại `/admin/accounts`, và là người duy nhất duyệt khoản tiền cùng quyết định nội bộ Staff. V15 giữ nguyên các bảng nghiệp vụ.
- Migration dùng chung V18 thêm tên/mô tả món đồ, giá trị Customer ước tính và thời gian sử dụng vào hồ sơ sự cố; các trường này chỉ là thông tin khai báo, không phải giá trị công ty đã xác nhận hay mức bồi thường đã chốt. V18 không thêm bảng nghiệp vụ.
- Migration dùng chung V19 từng thêm năm bảng xác minh/phương án/phiếu việc và đưa schema lịch sử lên 27 bảng. Migration V23/V24 lần lượt thêm giờ và Staff Customer chọn cho work order; các migration này được giữ nguyên trong Flyway history.
- Migration V20 không thay đổi schema. Chỉ database tên `home_cleaning_demo` nhận hai hồ sơ sự cố và Booking lịch sử.
- Migration V21 chỉ chuẩn hóa mã chủ đề của hai phản hồi demo theo các giá trị `FeedbackTopic` mà ứng dụng hỗ trợ; chỉ chạy cập nhật trong `home_cleaning_demo`.
- Migration V22 chuẩn hóa danh sách bằng chứng đã xem trống trong lịch sử xác minh demo theo định dạng ID phân tách bằng dấu phẩy mà ứng dụng sử dụng; chỉ chạy cập nhật trong `home_cleaning_demo`.
- Migration V23 thêm thời gian Customer đề xuất cho phiếu công việc; lịch sử migration vẫn được giữ.
- Migration V24 thêm Staff Customer đã chọn cho phiếu công việc; cấu trúc work order này được V25 xóa khỏi schema.
- Migration V25 đưa schema hiện tại còn 23 bảng: xóa dữ liệu/bốn bảng phương án và work order V19, bỏ hai cột liên kết workflow cũ, thêm mong muốn xử lý của Customer, trạng thái hồ sơ và thời điểm đồng ý/nhận bồi thường. Chỉ sửa ví dụ demo trong database tên `home_cleaning_demo`; không chuyển dữ liệu demo thành thỏa thuận đã chốt.
- Migration V26 thêm `Bookings.service_completed_at`; booking đã thanh toán/đang chờ thanh toán được backfill từ thời điểm thanh toán thành công hoặc giờ kết thúc lịch nếu thiếu. Hạn mở sự cố mới là 24 giờ kể từ mốc này. Không sửa các migration đã áp dụng và không chạy migration trên database demo đang dùng.
- Migration V27 thêm trạng thái phản hồi `SETTLED`; khi khoản bồi thường đã chi, ciphertext tài khoản được xóa mà không vi phạm constraint. Các khoản bồi thường lịch sử đã thanh toán được backfill sang trạng thái này; Customer vẫn cần xác nhận đã nhận tiền để đóng hồ sơ.
- Migration V28 tăng độ dài/trạng thái theo dõi xử lý Staff, lưu giải trình/kháng nghị cùng thời hạn 48 giờ, thời điểm quá hạn và quyết định/căn cứ/số tiền riêng; tạo bảng bằng chứng Staff nội bộ và bảng từng lần Admin ghi nhận Staff thực trả. Khoản thu hồi không tự khấu trừ lương; báo cáo cộng phần thực nhận, không cộng toàn nghĩa vụ. Dữ liệu cũ được giữ nguyên, không tự giả lập giải trình hay kết luận mới. Schema sau V28 có 25 bảng nghiệp vụ.
- Migration demo-only V29 đồng bộ câu chữ của hai hồ sơ phản ánh, gắn một ảnh PNG minh họa vào tin Customer đầu tiên và ghi nhãn rõ trong tên ảnh/nội dung. Ảnh tài sản chỉ mô phỏng vết nứt; số tiền 120.000 ₫ chỉ là đề xuất demo, chưa được khách đồng ý, Manager duyệt hoặc chi. Migration không đổi schema và không sửa feedback ngoài hai hồ sơ demo. `DemoFeedbackEvidenceInstaller` chỉ chạy với profile `demo`, chép ảnh từ classpath vào `${app.evidence.storage-root}` trước reconciler và không ghi đè file đang có.
- Migration demo-only V35 đổi họ tên tạm "… Demo" của năm tài khoản demo ban đầu (Customer, hai Staff, Admin, Manager) sang họ tên tiếng Việt, kèm tên người nhận trên địa chỉ của Customer demo; chỉ cập nhật khi tên tạm còn nguyên. Migration demo-only V33 thêm `staff3`–`staff7` (FULL_TIME/PART_TIME, có `Staff_Profiles.bio`) và `customer2`–`customer6@demo.gonnha.local`, mỗi Customer một địa chỉ nhà mặc định có tọa độ trong bán kính phục vụ tính từ `Company_Settings`. Mật khẩu dùng lại hash `Demo@12345` của V3; chèn theo `IF NOT EXISTS` trên email nên chạy lại không trùng. Cả hai không đổi schema và không chạy trên `Housekeeping_Database`.
- Migration dùng chung V37 thêm `Users.status_reason`, `status_changed_at`, `status_changed_by` để lưu lý do khóa tài khoản và người/thời điểm đổi trạng thái gần nhất; CHECK chỉ cho phép lý do khi tài khoản ở trạng thái `INACTIVE`. Không thêm bảng và không đổi dữ liệu hiện có.
- Migration dùng chung V40 thêm `Users.must_change_password` mặc định false; luồng Admin tạo Staff và Manager tạo Admin bật cờ để buộc đổi mật khẩu tạm ở lần đăng nhập đầu. Tài khoản hiện hữu không bị đánh dấu hồi tố.
- Migration dùng chung V41 thêm bốn cột kiểm toán vào `Bookings` để lưu Admin xác minh Staff chưa tới và Customer chọn đợi. Không thêm bảng; mọi lịch sử/dữ liệu tài chính cũ được giữ nguyên. Quy tắc ứng dụng mới cho phép Customer tự hủy Booking CONFIRMED chưa bắt đầu khi còn ít nhất 120 phút, hoặc hủy miễn phí trước khi bắt đầu sau đề xuất ETA trễ / xác minh trễ của Admin. Mọi lần hủy mới ghi `CancellationLogs`, giữ tổng giá trị Booking làm tham chiếu và để phí hủy, hoàn tiền, bồi thường Staff, phí nền tảng bằng 0; không phát sinh nợ COD, hoàn ví hoặc tự động khóa/hạn chế tài khoản. Thanh toán sau khi dịch vụ hoàn tất vẫn giữ nguyên.
- Migration dùng chung V42 chỉ sửa dữ liệu: trả lại mã băm mật khẩu mẫu cho 6 tài khoản Staff bị cắt mất đoạn đầu mã băm trong `database/Housekeeping_Database.bak`; database không có dòng hỏng thì không đổi gì.
- `db/shared/beforeValidate.sql` là Flyway callback chạy mỗi lần khởi động: database đã chạy migration theo số cũ (bản `.bak`, máy đã chạy PR #8) được ghi lại số mới cho đúng (V32 đổi tên demo → V35, V34 phí hủy → V32, V35 lý do khóa → V37, V36 sửa mật khẩu → V42). Nội dung các file không đổi nên không chạy lại migration nào; `spring.flyway.out-of-order=true` cho các phiên bản nhỏ hơn còn thiếu (V36, V40, V41) chạy bù.
- Migration demo-only V39 thay địa chỉ tâm placeholder ban đầu và các địa chỉ placeholder/trùng của sáu Customer demo bằng các địa chỉ công khai có số nhà/tên đường, phường hiện hành và tọa độ trong bán kính phục vụ. Migration chỉ cập nhật `home_cleaning_demo`; địa chỉ tâm chỉ đổi nếu vẫn là placeholder nguyên bản. House cũ không còn Booking tham chiếu được xóa cùng thông tin người nhận/ghi chú; House có Booking tham chiếu được soft-delete để giữ FK và lịch sử. Sau đó tạo một địa chỉ mặc định đồng bộ cho mỗi Customer; không sửa snapshot địa chỉ trong Booking.
- Migration demo-only V38 sửa địa chỉ placeholder chưa có tọa độ của `customer@demo.gonnha.local` thành địa chỉ mô phỏng đủ cụ thể, nằm trong bán kính phục vụ hiện tại. Chỉ sửa đúng địa chỉ mặc định còn nguyên dữ liệu placeholder trong `home_cleaning_demo`; không thay địa chỉ đã chỉnh hoặc dữ liệu database chính.
- `db/migration/V1__create_initial_schema.sql`, `V2__add_momo_payment_attempts.sql`, `V3__replace_momo_with_payos.sql` là lịch sử của mô hình cũ. Giữ nguyên để bảo toàn lịch sử; không chỉnh nội dung migration đã áp dụng.
- Database cũ dùng schema/model trước đây không tự động chuyển đổi. Nếu chủ sở hữu xác nhận bỏ dữ liệu cũ trên database chính, chạy [`recreate-primary-schema.sql`](recreate-primary-schema.sql) một lần trong đúng `Housekeeping_Database`; script giữ nguyên lịch sử `flyway_schema_history` cũ, xóa toàn bộ bảng ứng dụng và dữ liệu (kể cả lịch sử Flyway mới nếu có), đồng thời kiểm tra đúng tên database. Sau đó chạy ứng dụng mặc định để baseline tạo 15 bảng và migration áp dụng tiếp để đạt schema hiện tại (29 bảng nghiệp vụ). Không chạy script ở môi trường có dữ liệu cần giữ.
- `database/schema.sql` chỉ còn guard báo lỗi cho đường dẫn schema cũ; không dùng làm DDL. Baseline ở `db/primary` cùng migration tại `db/shared` là nguồn DDL cho ứng dụng mặc định.
- Flyway `flyway_schema_history` là bảng tooling, không nằm trong 29 bảng nghiệp vụ.

### Khởi tạo Manager hệ thống
Database demo seed tài khoản Manager riêng ở V11 và chuyển vai trò từ cờ cũ trong V15. Với database chính, nếu V15 không tìm thấy Admin đang có cờ quản lý để chuyển đổi, DBA cần gán vai trò cho đúng một tài khoản Admin đã xác minh (thay placeholder trước khi chạy):
```sql
UPDATE dbo.Users SET role='MANAGER'
WHERE email=N'<manager-admin-email>' AND role='ADMIN' AND status='ACTIVE';
```
Filtered unique index từ V15 ngăn tạo Manager thứ hai. Manager có thể tạo/khoá Admin tại `/admin/accounts`; Admin không được tự cấp quyền hoặc mở trang này. Nếu cần khôi phục Manager sau này, DBA phải chuyển tài khoản Admin đã xác minh bằng thao tác có kiểm soát.

## Cấu hình riêng

Trong `.env` ở thư mục Maven module, đặt `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` cho `Housekeeping_Database`. Không dùng credentials production và không commit `.env`. Profile demo tùy chọn trỏ cố định tới `home_cleaning_demo`, không lấy URL từ `.env`. Đặt `PAYOUT_ENCRYPTION_KEY` bằng chuỗi Base64 giải mã thành đúng 32 byte (AES-256) trong secret store hoặc `.env` cục bộ; không đưa khóa thật vào source, migration hay tài liệu. Tính năng nhận thông tin tài khoản chuyển khoản từ Customer yêu cầu khóa này; khi chưa cấu hình hợp lệ, ứng dụng từ chối lưu thông tin đó. Giữ khóa ổn định cho tới khi mọi đề nghị chuyển khoản đang chờ đã được quyết toán, vì đổi khóa trước đó làm mất khả năng giải mã thông tin đang lưu. Không dùng chung khóa giữa các môi trường.

## Kiểm chứng

GitHub Actions chạy `bash ./mvnw -B verify` trên JDK 21 và Docker; job này bao gồm nhóm Testcontainers SQL Server. Chỉ coi migration, Hibernate mapping và concurrency đã được xác minh khi job kiểm thử ứng dụng hoàn tất thành công.

```powershell
# Unit/MVC, không cần SQL Server
mvn "-DexcludedGroups=sqlserver" verify

# Flyway, Hibernate mapping và concurrency trên SQL Server Testcontainers; cần Docker
mvn verify
```

Không kết luận migration hoặc concurrency đã được SQL Server xác minh nếu Testcontainers/SQL Server tests không chạy. Xem [README](../README.md) để chạy ứng dụng.

- Migration dùng chung V16 thêm bảng `Feedback_Case_Messages` và liên kết bằng chứng hiện có qua `message_id`. V17 thêm `Financial_Payout_Responses`; V19 từng thêm lịch sử xác minh, phương án và work order, đưa schema lịch sử lên 27 bảng. V25 đã gỡ các bảng phương án/work order; V26 thêm mốc hoàn tất Booking; V27 ghi trạng thái `SETTLED` sau khi xóa ciphertext; V28 thêm bằng chứng giải trình Staff riêng tư và các lần Staff thực trả, đưa schema lên 25 bảng. V32 tăng lên 28 bảng; V36 thêm lịch sử ETA, đưa schema lên 29 bảng. V29 gắn ảnh minh họa cho hai hồ sơ demo mà không đổi schema. Mô tả sự cố và reply cũ được nhập vào timeline; thời điểm reply cũ được đánh dấu không chính xác. Bằng chứng hiện có được liên kết với tin Customer đầu tiên. Customer sở hữu booking và Admin/Manager xem/gửi tin, Staff và Customer khác không có quyền. Tin nhắn/bằng chứng Customer/Admin append-only, hồ sơ đã giải quyết chỉ đọc.

- Migration dùng chung V32 thêm ba bảng tài chính/hủy lịch và các cột lịch sử; giữ nguyên bảng/cột cùng dữ liệu cũ, không sửa migration đã áp dụng.
- Migration dùng chung V36 thêm `Booking_Delay_Proposals` để giữ append-only mỗi ETA và phản hồi; thêm trạng thái ETA Booking EXPIRED để đồng thuận hết hiệu lực sau ETA. Schema hiện tại có 29 bảng nghiệp vụ.
- Migration dùng chung V31 ghi actual arrival verification; V36 thêm lịch sử ETA. Theo chính sách V41, Customer báo giờ đến sai từ giờ hẹn khi việc chưa bắt đầu, kể cả khi Staff tự khai đã tới; báo cáo khóa bắt đầu đến khi Admin xác minh. Customer sau xác minh chọn đợi hoặc hủy miễn phí.
