> **Document:** Local Runtime Setup\
> **File:** `docs/local-setup.md`\
> **Version:** v1.6.0\
> **Created:** 2026-10-16\
> **Last Updated:** 2026-10-17\
> **Status:** Draft

# Chạy Project Trên Máy Đã Setup

**Kết nối hiện tại:** SQL Server `SQLEXPRESS` tại `127.0.0.1:1433`, database **`hsf302`**. Database `hsf302_housekeeping_local` của lần setup trước không còn hiện diện khi kiểm tra lại; cấu hình local đã chuyển sang database `hsf302` được tạo bằng SQL thủ công. SQL login riêng vẫn giữ tên `hsf302_housekeeping_local`; tên login không cần giống tên database.

## Chạy full stack trong IntelliJ IDEA

Frontend Thymeleaf được Spring Boot phục vụ cùng backend tại port 8080. Một Run Configuration chạy cả hai; SQL Server cần hoạt động trước khi chạy.

**Máy hiện tại đã cấu hình xong:** workspace ngoài đã liên kết đúng `pom.xml` của ứng dụng. Chọn **Housekeeping Full Stack** rồi bấm **Run** hoặc **Shift+F10**; IntelliJ tự mở trang `http://localhost:8080/` trong trình duyệt mặc định bằng tác vụ **Launch Web Browser**. Đã thử dừng server rồi Run lại: Chrome mở trang chủ Gọn Nhà và trang chủ trả HTTP 200. SDK của project là `ms-21` (Microsoft OpenJDK 21), language level dùng SDK default; annotation processing cho Lombok đã bật. Các bước dưới đây dùng khi mở lại project từ đầu.

1. Chọn **File → Open**, mở `app/housekeeping-management-system/pom.xml` và chọn **Open as Project**. Đây là Maven project của ứng dụng; mở riêng thư mục ZIP bên ngoài có thể chỉ tạo Java module thông thường.
2. Trong **File → Project Structure → Project**, đặt **SDK = JDK 21**, **Language level = SDK default**. Nếu chưa có JDK 21, chọn **Add JDK from disk**, trỏ tới `.runtime/jdk-21.0.12.1+1` ở root repository (cùng cấp với `README.md`, cách thư mục ứng dụng hai cấp).
3. Chờ Maven đồng bộ xong. Nếu chưa thấy Maven, nhấp phải `pom.xml` → **Add as Maven Project**. Trong **Settings → Build Tools → Maven**, chọn JDK 21 cho Importer và Runner nếu chúng chưa dùng Project SDK.
4. Trên thanh Run, chọn **Housekeeping Full Stack** từ file cấu hình đã chuẩn bị trong `app/housekeeping-management-system/.run/`. Nếu chưa thấy, mở **Run → Edit Configurations**, thêm **Application** và điền các giá trị bên dưới.
5. Nếu trước đó dùng launcher chạy nền, chạy `stop-local.cmd` tại root repository để nhường port 8080. Sau đó bấm **Shift+F10** trong IntelliJ.
6. Chờ console có `Started HousekeepingManagementSystemApplication`, rồi mở [trang chủ](http://localhost:8080/). Dùng nút **Stop** trong IntelliJ để dừng phiên chạy này.

| Thuộc tính Application | Giá trị |
|---|---|
| Main class | `com.housekeeping.management.HousekeepingManagementSystemApplication` |
| Use classpath of module | `housekeeping-management-system` (module Maven sau khi import) |
| JRE | SDK `ms-21` đã đăng ký trong IntelliJ, trỏ tới JDK 21 của project |
| Working directory | `$MODULE_WORKING_DIR$/../..` — root repository có `.runtime/` và `run-local.cmd` |
| Program arguments | `--spring.profiles.active=local,demo --spring.config.additional-location=file:./.runtime/` |
| Before launch | Build, Launch Web Browser → `http://localhost:8080/` |

Run Configuration nạp kết nối SQL từ `.runtime/application-local.properties`; không điền mật khẩu vào cấu hình Run. SQL snapshot được cung cấp trùng với Flyway V1; database `hsf302` đã được Flyway baseline tại V1 và Hibernate validate thành công. Không chạy lại lệnh tạo bảng hoặc bật lại baseline trong các lần chạy bình thường.

Tham khảo cách tổ chức Run Configuration trong [Backend Guide](https://github.com/NgaiLong49423/vegetarian-support-system/blob/develop/app/mamxanh-backend/README.md) và [Frontend Guide](https://github.com/NgaiLong49423/vegetarian-support-system/blob/develop/app/mamxanh-frontend/README.md) của Vegetarian Support System. Project tham khảo dùng React/Vite riêng; Housekeeping tiếp tục dùng Spring MVC/Thymeleaf theo baseline hiện có.

**Trạng thái xác minh ngày 2026-10-17:** đã build và khởi động bằng nút Run trong IntelliJ. Tiến trình Java 21 có parent `idea64.exe`, có `idea_rt.jar` và classpath qua argument file. Console báo ứng dụng đã khởi động, Hibernate kết nối `hsf302/dbo`; đăng nhập thật Admin/Customer/Staff đều chuyển đúng trang với HTTP 200, trang dịch vụ render dữ liệu SQL Server.

Muốn đổi URL tự mở hoặc bỏ bước mở trình duyệt, vào **Run → Edit Configurations → Housekeeping Full Stack → Before launch → Launch Web Browser**. Tác vụ này được lưu trong các file `.run/Housekeeping Full Stack.run.xml` tương ứng với thư mục đang mở trong IntelliJ; không phải hành vi tự mở trình duyệt của backend khi chạy qua launcher.

### Sửa lỗi không tìm thấy main class

Nếu console báo `ClassNotFoundException: com.housekeeping.management.HousekeepingManagementSystemApplication`, kiểm tra **Run → Edit Configurations → Housekeeping Full Stack → Use classpath of module**. Giá trị phải là **`housekeeping-management-system`**, không phải module thư mục ngoài `housekeeping-management-system-main`. Nếu chưa có module này, mở đúng `pom.xml` trong editor rồi chọn **Add as Maven Project**, chờ import hoàn tất. Giữ bước Build trước khi chạy và dùng **Shorten command line → @argFiles (Java 9+)** trên Windows.

### Sửa lỗi không tìm thấy `.runtime`

Thông báo `Config data location 'file:./.runtime/' does not exist` nghĩa là thư mục làm việc của phiên chạy không chứa `.runtime/`. Các bản cấu hình Run hiện dùng chung **Working directory = `$MODULE_WORKING_DIR$/../..`**, tính từ module Maven của ứng dụng, để mở workspace ngoài, root repository hoặc module ứng dụng đều tìm đúng cấu hình local. Đồng thời đặt **JRE = `ms-21`**; không chọn SDK 26 cho cấu hình này. Đã kiểm tra lại thư mục làm việc thực tế của tiến trình JVM, Java 21, trang chủ, đăng nhập và dịch vụ HTTP 200 sau khi sửa. Giữ đường dẫn cấu hình bắt buộc để phát hiện cấu hình local bị thiếu.

## Mở ứng dụng

Truy cập [http://localhost:8080/](http://localhost:8080/). Trang đăng nhập dùng chung cho ba vai trò: [http://localhost:8080/login](http://localhost:8080/login).

Ba tài khoản demo đã được tạo trong database local riêng:

Bảng dưới đây là bộ tài khoản local ban đầu với mật khẩu riêng. Bộ tài khoản mẫu công khai cho GitHub dùng email `*@demo.gonnha.local`, mật khẩu `Demo@123456`, đã được bổ sung bằng profile `demo`; xem [hướng dẫn demo](demo-setup.md). Cấu hình **Housekeeping Full Stack** hiện bật `local,demo`; cả hai bộ tài khoản vẫn hoạt động.

| Vai trò | Email |
|---|---|
| Admin | `admin@gonnha.local` |
| Customer | `customer@gonnha.local` |
| Staff | `staff@gonnha.local` |

Mật khẩu ngẫu nhiên được lưu tại [`.runtime/LOCAL-ACCOUNTS.md`](../.runtime/LOCAL-ACCOUNTS.md), chỉ trên máy đã setup. Không có mật khẩu cố định trong source hoặc migration. `.runtime/` chứa cấu hình riêng và thông tin đăng nhập; không commit hoặc gửi kèm thư mục này khi chia sẻ source.

## Mở lại sau khi tắt máy

Đảm bảo dịch vụ **SQL Server (SQLEXPRESS)** đang chạy. Từ thư mục có README và file `run-local.cmd`:

```powershell
# Chạy trong cửa sổ hiện tại; Ctrl+C để dừng
.\run-local.cmd

# Hoặc chạy nền, không mở thêm cửa sổ Java
.\run-local.cmd -Background

# Tắt đúng tiến trình chạy nền của project
.\stop-local.cmd
```

Có thể nhấp đúp `run-local.cmd` để chạy. Script tự chọn JDK 21 của project, build Maven, nạp cấu hình local và mở cổng 8080 chỉ trên máy này. Khi dùng `-Background`, script chỉ thông báo sẵn sàng sau khi trang đăng nhập trả HTTP 200. Nếu ứng dụng đã chạy thì không tạo tiến trình thứ hai.

Log nằm tại `app/housekeeping-management-system/target/local-app.out.log` và `local-app.err.log`. File PID nằm trong `.runtime/app.pid`; script dừng kiểm tra command line trước khi dừng để tránh tác động chương trình khác.

## Môi trường đã cấu hình

| Thành phần | Thiết lập local |
|---|---|
| Java | Microsoft OpenJDK 21.0.12.1, nằm trong `.runtime/`; đã kiểm tra SHA256 |
| Maven | Dùng Maven có sẵn; launcher có fallback Maven Wrapper |
| SQL Server | Instance `SQLEXPRESS`, TCP `127.0.0.1:1433` |
| Database ứng dụng | `hsf302` |
| SQL login riêng | `hsf302_housekeeping_local`, được map vào `hsf302` với các role `db_datareader`, `db_datawriter`, `db_ddladmin` |
| Schema | 11 bảng tạo thủ công đúng V1; Flyway ghi baseline V1 thành công trong `flyway_schema_history` |
| Profile chạy | `local`, nạp cấu hình ngoài JAR từ `.runtime/application-local.properties` |
| Evidence | `app/housekeeping-management-system/var/local-evidence/` |
| URL | `http://localhost:8080/` |

Không thay `JAVA_HOME`/PATH toàn hệ thống, không đổi mật khẩu SQL Server hiện có, không đổi dữ liệu database khác. Thông tin kết nối riêng không nằm trong `src/main/resources` và không được đóng vào JAR. `PhysicalNamingStrategyStandardImpl` được cấu hình để Hibernate giữ đúng các tên đã khai báo trong Entity/migration; vẫn dùng `ddl-auto=validate`.

JDK tải từ [Microsoft OpenJDK](https://learn.microsoft.com/en-us/java/openjdk/download). Cấu hình giữ tên bảng theo [hướng dẫn Spring Boot](https://docs.spring.io/spring-boot/4.0/how-to/data-access.html).

## Dữ liệu demo và kiểm tra đã chạy

Bộ demo đã được khôi phục vào database `hsf302` đang rỗng: ba tài khoản dùng lại thông tin đăng nhập local trước đó, ba dịch vụ và một khoảng sẵn sàng 14 ngày. Mật khẩu BCrypt và thông tin đăng nhập trong `.runtime/LOCAL-ACCOUNTS.md` không được in ra log. Không tạo lịch đặt hoặc thanh toán trong lượt kết nối lại này. Khi khoảng sẵn sàng hết hạn, Admin thêm khoảng mới trong giao diện.

Đã xác minh kết nối lại ngày 2026-10-16:

- SQL login của backend kết nối được database `hsf302` qua JDBC.
- Flyway baseline V1 thành công; bỏ các cờ baseline tạm thời sau lần tiếp nhận schema.
- Khởi động lại ứng dụng bằng cấu hình bình thường thành công; Hibernate validate mapping và Hikari mở kết nối.
- Đăng nhập thật cả ba vai trò, chuyển đúng `/admin/bookings`, `/customer/bookings`, `/staff/bookings`, trả HTTP 200.
- Customer truy cập route Admin trả HTTP 403.
- Trang `/services` render dữ liệu dịch vụ từ SQL Server.
- SQL xác nhận một Admin, một Customer, một Staff, ba Services và một Work_Schedule.

Lần setup trước trên database demo cũ đã có các bằng chứng sau; đây không phải dữ liệu hoặc lượt kiểm tra mới trên `hsf302`:

- 18 kiểm thử web/identity/ownership bằng đúng JDK 21.
- Flyway migrate database rỗng, Hibernate validate và khởi động server.
- Đăng nhập ba vai trò qua HTTP thật, đọc dữ liệu bằng JDBC/JPA từ SQL Server.
- Customer tạo lịch `CONFIRMED`; Staff upload ảnh PNG hợp lệ, hoàn thành tạo Payment `UNPAID`.
- Customer chọn `CASH`, Admin xác nhận nhận tiền thành `PAID`, Customer gửi một Feedback.
- Thêm yêu thích, tạo và hủy một lịch tương lai thành `CANCELLED`.
- Route Admin bị từ chối với Customer; POST thiếu CSRF bị từ chối.
- Dừng và khởi động lại bằng launcher thành công.

Seeder `scripts/seed-local.ps1` và kịch bản ghi dữ liệu `scripts/verify-local.ps1` vẫn giới hạn ở database demo cũ `hsf302_housekeeping_local`; không dùng chúng để ghi vào database `hsf302` hiện tại. Nghiệm thu luồng đặt lịch trên database mới có thể thực hiện qua giao diện theo [hướng dẫn tích hợp](frontend-integration.md). Các test cạnh tranh dùng Docker/Testcontainers chưa được chạy trong lượt kết nối này.

Nếu chuyển project sang máy khác, `.runtime/` không được đưa vào source; cần chuẩn bị JDK 21, database riêng và cấu hình kết nối theo [hướng dẫn tích hợp](frontend-integration.md).
