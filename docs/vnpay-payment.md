> **Document:** VNPay Sandbox Payment Guide\
> **File:** `docs/vnpay-payment.md`\
> **Version:** v1.0.0\
> **Created:** 2026-11-08\
> **Last Updated:** 2026-11-08\
> **Status:** Draft\

# Thanh toán VNPay sandbox và xác nhận tiền mặt

Thanh toán trực tuyến dùng **môi trường thử nghiệm (sandbox) của VNPay**: chỉ nhận thẻ test, không chuyển tiền thật. Thanh toán tiền mặt do Staff được phân công xác nhận như trước.

## Cấu hình

1. Một người trong nhóm đăng ký tài khoản sandbox tại `https://pay.vnpaytest.vn/dev-registration` và kích hoạt bằng link trong email. VNPay gửi **TMN Code** (mã website, 8 ký tự) và **Hash Secret** (chuỗi bí mật để ký giao dịch).
2. Mỗi máy chép hai giá trị đó vào `.env` trong thư mục Maven module (`app/housekeeping-management-system/.env`). Gửi riêng cho từng thành viên; không đưa lên Git, nhóm chat chung hay ảnh chụp.

```dotenv
VNPAY_TMN_CODE=YOUR_VNPAY_TMN_CODE
VNPAY_HASH_SECRET=YOUR_VNPAY_HASH_SECRET
```

3. Khởi động lại ứng dụng. Nếu thiếu hai giá trị này, bấm thanh toán sẽ hiện hướng dẫn cấu hình thay vì chuyển sang VNPay.

Không cần link công khai, ngrok hay khai báo IPN để chạy trên máy cá nhân. Chỉ đặt `VNPAY_RETURN_URL` khi ứng dụng không mở tại `http://localhost:8080`.

## Thẻ test

Chọn **Thẻ nội địa và tài khoản ngân hàng → NCB** trên trang VNPay, rồi nhập:

| Trường | Giá trị |
|---|---|
| Số thẻ | `9704198526191432198` |
| Tên chủ thẻ | `NGUYEN VAN A` |
| Ngày phát hành | `07/15` |
| OTP | `123456` |

Danh sách thẻ test khác (thẻ hết tiền, thẻ bị khóa…) ở [trang demo VNPay](https://sandbox.vnpayment.vn/apis/vnpay-demo/).

## Luồng nghiệp vụ

1. Staff được phân công bấm hoàn thành công việc. Booking chuyển sang `AWAITING_PAYMENT`.
2. Customer bấm **Thanh toán trực tuyến qua VNPay**. Backend khóa Booking, tạo một dòng `Payments` `ONLINE/PENDING`, lưu mã đơn (`vnp_TxnRef`) tại `transaction_code`, rồi chuyển trình duyệt sang VNPay bằng URL có chữ ký HMAC-SHA512. Link thanh toán hết hạn sau 15 phút.
3. Trả xong, VNPay đưa khách về `GET /payments/vnpay/return`. Backend kiểm tra chữ ký, rồi **tự gọi API QueryDR của VNPay** (server gọi server) để hỏi kết quả. Chỉ khi VNPay xác nhận thành công và đúng số tiền, Payment mới thành `SUCCESS` và Booking thành `COMPLETED`. Dữ liệu trình duyệt mang về không bao giờ tự đánh dấu đã trả.
4. Khách thấy thông báo trên trang chi tiết booking: thành công, thất bại/đã hủy (thanh toán lại được), hoặc VNPay chưa xác nhận (tải lại sau ít phút).
5. Nếu khách trả tiền mặt, chỉ Staff được phân công xác nhận. Backend ghi Payment `CASH/SUCCESS` và hoàn tất Booking trong cùng transaction; các lần thử ONLINE đang chờ được đánh dấu `FAILED`.

Mỗi Booking có thể có nhiều lần thử ONLINE nhưng tối đa một Payment thành công. Các trạng thái Booking vẫn là `CONFIRMED`, `AWAITING_PAYMENT`, `COMPLETED`, `CANCELLED`; database không đổi.

### Bấm thanh toán lại khi lần trước còn chờ

Backend hỏi VNPay về lần trước trước khi làm gì khác, để khách không bị thu tiền hai lần:

| VNPay báo lần trước | Xử lý |
|---|---|
| Đã thanh toán | Ghi nhận thành công, không tạo giao dịch mới |
| Không tồn tại (`91`) hoặc thất bại (`02`) | Đánh dấu lần trước `FAILED`, tạo giao dịch mới |
| Chưa hoàn tất (`01`), link còn hạn | Mở lại đúng link cũ |
| Chưa hoàn tất (`01`), link đã hết hạn | Đánh dấu lần trước `FAILED`, tạo giao dịch mới |
| Lỗi khác, không trả lời được | Không tạo giao dịch mới; báo khách thử lại sau |

### IPN (khi có link công khai)

`GET /payments/vnpay/ipn` nhận thông báo server-to-server của VNPay và trả JSON `{"RspCode","Message"}` theo quy ước VNPay (`00` thành công, `01` không tìm thấy đơn, `02` đã xác nhận, `04` sai số tiền, `97` sai chữ ký, `99` lỗi khác). Chỉ dùng khi ứng dụng chạy ở địa chỉ HTTPS công khai và đã khai báo IPN URL với VNPay; mỗi tài khoản chỉ có một IPN URL nên không dùng cho nhiều máy cá nhân. IPN và trang return dùng chung xử lý idempotent trong `PaymentService`.

## Xử lý sự cố

| Hiện tượng | Nguyên nhân |
|---|---|
| Trang VNPay báo lỗi `code=70` | Sai chữ ký: Hash Secret trong `.env` không đúng |
| Trang VNPay báo lỗi `code=71` | Sai TMN Code |
| Trang VNPay báo lỗi `code=88` "The terminal is not configured" | Tài khoản sandbox chưa được VNPay kích hoạt/cấu hình xong; kiểm tra email kích hoạt hoặc liên hệ hotrovnpay@vnpay.vn |
| Bấm thanh toán lại thấy "VNPay chưa trả lời được trạng thái… (mã 99)" | QueryDR của VNPay đang lỗi, thường cùng nguyên nhân với `code=88` |

Log ứng dụng ghi mã trả lời của QueryDR (`VNPay QueryDR for order … answered …`) để chẩn đoán.

## Kiểm thử và bảo mật

Kiểm thử tự động không gọi VNPay thật: chữ ký được so với giá trị tính độc lập bằng `openssl`, còn QueryDR dùng server giả lập. Thử nghiệm thủ công trên sandbox chỉ dùng thẻ test. Không đưa Hash Secret vào source, test hay tài liệu.

Tham khảo: [VNPay – thanh toán PAY](https://sandbox.vnpayment.vn/apis/docs/thanh-toan-pay/pay.html), [VNPay – truy vấn giao dịch QueryDR](https://sandbox.vnpayment.vn/apis/docs/truy-van-hoan-tien/querydr&refund.html).
