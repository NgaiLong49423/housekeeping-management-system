# Thanh toán PayOS và xác nhận tiền mặt

## Cấu hình

Đặt `PAYOS_CLIENT_ID`, `PAYOS_API_KEY`, `PAYOS_CHECKSUM_KEY` cùng các URL callback vào `.env` ở máy chạy ứng dụng. Không lưu bí mật trong source, ảnh chụp, collection Postman hoặc Git. PayOS sử dụng dịch vụ thật; không tạo giao dịch thật trong kiểm thử tự động.

```dotenv
PAYOS_RETURN_URL=https://YOUR_PUBLIC_HOST/payments/payos/return
PAYOS_CANCEL_URL=https://YOUR_PUBLIC_HOST/payments/payos/cancel
PAYOS_WEBHOOK_URL=https://YOUR_PUBLIC_HOST/payments/payos/webhook
```

Khai báo webhook URL trong kênh PayOS để PayOS gọi được ứng dụng qua HTTPS công khai. URL return/cancel chỉ hiển thị thông báo; chỉ webhook có chữ ký hợp lệ mới được cập nhật kết quả thanh toán.

## Luồng nghiệp vụ

1. Nhân viên được phân công hoàn tất công việc. Booking chuyển sang `AWAITING_PAYMENT`.
2. Khách hàng có thể yêu cầu thanh toán trực tuyến. Backend khóa Booking, tạo một dòng `Payments` có `payment_method=ONLINE`, `status=PENDING` và mã `orderCode` duy nhất được lưu tại `transaction_code`.
3. Backend tạo hoặc truy xuất checkout PayOS cho mã đơn đó. Nếu yêu cầu được gửi lại khi lần trước còn pending, hệ thống dùng lại giao dịch đang chờ.
4. PayOS gọi `POST /payments/payos/webhook`. Ứng dụng kiểm tra chữ ký, `orderCode`, số tiền và đơn vị tiền. Khi PayOS xác nhận thành công, Payment thành `SUCCESS` và Booking thành `COMPLETED`. Webhook lặp lại được xử lý idempotent.
5. Nếu khách trả tiền mặt, chỉ nhân viên được phân công mới có thể xác nhận. Backend ghi Payment `CASH/SUCCESS` và hoàn tất Booking trong cùng transaction.
6. Mỗi Booking có thể có nhiều lần thử thanh toán trực tuyến trong bảng `Payments`, nhưng tối đa một Payment thành công. Khi xác nhận tiền mặt, các lần thử ONLINE đang pending được đánh dấu `FAILED`.

Các trạng thái Booking là `CONFIRMED`, `AWAITING_PAYMENT`, `COMPLETED`, `CANCELLED`. `COMPLETED` chỉ được đặt sau khi có Payment thành công. Ứng dụng không dùng bảng `Payment_Attempt` và không yêu cầu Admin xác nhận giao dịch.

## Kiểm thử và bảo mật

Kiểm thử tự động không gọi PayOS thật. Chỉ kiểm tra chữ ký/webhook bằng fixture giả lập; không đưa checksum key vào collection đã export. Thử nghiệm checkout thủ công có thể chuyển tiền thật.

Tham khảo: [PayOS API](https://payos.vn/docs/api/), [xác minh chữ ký webhook](https://payos.vn/docs/tich-hop-webhook/kiem-tra-du-lieu-voi-signature/), [môi trường kiểm thử PayOS](https://payos.vn/docs/moi-truong-test/).
