# GLOSSARY — Housekeeping Management System

Bảng thuật ngữ chuẩn hóa cho hệ thống quản lý và đặt dịch vụ dọn dẹp nhà ở. Định nghĩa thống nhất giữa đội ngũ phát triển, kiểm thử và giáo viên hướng dẫn môn HSF302.

---

## 1. Nghiệp vụ Lịch hẹn & Vận hành (Booking & Operations)

### Lịch hẹn (Booking)
Đơn đặt dịch vụ dọn nhà do Khách hàng (Customer) khởi tạo, gắn liền với một địa chỉ nhà cụ thể, một nhân viên (Staff) phụ trách, một khung thời gian bắt đầu xác định và một danh sách các dịch vụ cần thực hiện.
- *Tránh dùng*: Order, hóa đơn đặt phòng, ticket.

### Vòng đời Lịch hẹn (Booking Lifecycle)
Chuỗi trạng thái tuần tự và chặt chẽ của một lịch hẹn:
1. **CONFIRMED**: Lịch hẹn được tạo thành công, đã gán nhân viên và chờ đến giờ làm việc.
2. **ARRIVED**: Nhân viên và khách hàng đã xác nhận có mặt tại hiện trường (Xác nhận đến hai chiều).
3. **IN_PROGRESS**: Nhân viên bắt đầu bấm giờ thực hiện các công việc dọn dẹp.
4. **AWAITING_PAYMENT**: Công việc đã hoàn tất, nhân viên đã đăng tải hình ảnh nghiệm thu và hệ thống mở cổng thanh toán.
5. **COMPLETED**: Lịch hẹn đã được thanh toán đầy đủ (tiền mặt hoặc VNPay) và kết thúc hoàn toàn.
6. **CANCELLED**: Lịch hẹn bị hủy (hủy miễn phí trước 24h hoặc do nhân viên đến trễ quá 30 phút).

### Xác nhận đến hai chiều (Mutual Arrival Confirmation)
Cơ chế xác thực việc nhân viên đã có mặt tại nhà khách đúng giờ:
- Khách hàng bấm xác nhận nhân viên đã tới.
- Nhân viên bấm xác nhận mình đã có mặt.
- Nếu thời gian đến trễ so với giờ hẹn dự kiến, hệ thống sẽ tự động ghi nhận số phút trễ để tính toán chiết khấu bồi thường.

### Nghiệm thu công việc & Ảnh bằng chứng (Work Evidence & Acceptance)
Ảnh chụp thực tế hiện trường sau khi dọn dẹp xong (hoặc trước và sau khi làm). Nhân viên bắt buộc phải tải lên ảnh nghiệm thu để khách hàng kiểm tra trước khi chuyển sang bước thanh toán.
- *Tránh dùng*: Ảnh chụp tự do, attachment không định danh.

---

## 2. Tài chính & Thanh toán (Finance & Payment)

### Thanh toán sau hoàn thành (Post-service Payment)
Chính sách thanh toán của hệ thống: Khách hàng **không cần phải đặt cọc hay thanh toán trước**. Chỉ khi nhân viên dọn xong và tải ảnh nghiệm thu, khách hàng mới tiến hành thanh toán.
- *Tránh dùng*: Pre-paid, cọc 50%, cọc giữ chỗ.

### Hình thức thanh toán (Payment Method)
Hệ thống hỗ trợ 2 hình thức thanh toán chính thống:
- **Tiền mặt (CASH)**: Khách hàng thanh toán tiền mặt trực tiếp cho nhân viên dọn dẹp. Nhân viên sau khi nhận đủ tiền sẽ bấm xác nhận đã thu trên giao diện Staff Portal.
- **Thanh toán trực tuyến (ONLINE - VNPay Sandbox)**: Khách hàng quét mã VNPAY-QR hoặc dùng thẻ ngân hàng nội địa (test NCB) qua cổng VNPay Sandbox. Giao dịch được xác thực tự động bằng chữ ký số HMAC-SHA512 và API tra cứu trạng thái giao dịch (QueryDR).

### Bồi thường trễ giờ (Delay Compensation)
Chính sách bảo vệ quyền lợi khách hàng khi nhân viên đến muộn:
- Trễ dưới 10 phút: Không phạt (ân hạn di chuyển).
- Trễ từ 10 - 20 phút: Giảm giá hóa đơn (ví dụ 5%).
- Trễ từ 20 - 30 phút: Giảm giá hóa đơn (ví dụ 10%).
- **Giới hạn trần (Discount Cap)**: Tỷ lệ giảm giá được cấu hình tối đa không vượt quá 30% tổng giá trị dịch vụ.
- **Hủy miễn phí**: Nếu nhân viên đến trễ hơn 30 phút, khách hàng có quyền hủy lịch ngay lập tức mà không chịu bất kỳ chi phí nào.

---

## 3. Phản ánh & Bồi thường Sự cố (Feedback & Incidents)

### Đánh giá dịch vụ (Feedback)
Điểm số đánh giá từ 1 đến 5 sao kèm nhận xét của khách hàng sau khi lịch hẹn hoàn thành (`COMPLETED`).

### Hồ sơ sự cố (Feedback Case / Incident)
Biến cố phát sinh trong quá trình dọn dẹp do khách hàng phản ánh (ví dụ: làm vỡ đồ, hỏng thiết bị gia dụng, mất mát tài sản). Hồ sơ sự cố bắt buộc phải có:
- Mô tả chi tiết món đồ.
- Giá trị ước tính ban đầu và thời gian đã sử dụng.
- Hình ảnh/video bằng chứng hiện trường.
- Quy trình hòa giải và phương án bồi thường được Admin xem xét và thỏa thuận minh bạch với khách hàng.

---

## 4. Phân quyền Người dùng (User Roles & Access Control)

### Khách hàng (CUSTOMER)
Người dùng đặt lịch, quản lý danh sách nhà/căn hộ, theo dõi tiến độ dọn dẹp, nghiệm thu, thanh toán và gửi đánh giá/sự cố.

### Nhân viên (STAFF)
Người thực hiện công việc dọn dẹp theo phân công, theo dõi lịch làm việc hàng ngày, xác nhận đến, chụp ảnh nghiệm thu, xác nhận thu tiền mặt và xin nghỉ phép. Phân loại gồm nhân viên Toàn thời gian (`FULL_TIME`) và Bán thời gian (`PART_TIME`).

### Quản trị viên (ADMIN)
Người vận hành hệ thống hàng ngày: quản lý danh mục dịch vụ, duyệt đơn nghỉ phép của nhân viên, điều phối lịch hẹn, giải quyết hồ sơ khiếu nại sự cố.
- **Quy tắc bảo mật**: Admin **không có quyền khóa hoặc sửa tài khoản của các Admin khác**.

### Người quản lý cấp cao / SuperAdmin (MANAGER)
Tài khoản quản trị cao nhất của toàn hệ thống (mặc định: `manager@demo.gonnha.local`). Chỉ có Manager mới có quyền tạo, kích hoạt, khóa hoặc thay đổi trạng thái của các tài khoản Admin khác.
