package com.housekeeping.management.web;

import java.util.Map;

public final class PortalMessages {
    private PortalMessages() { }
    private static final Map<String, String> MESSAGES = Map.ofEntries(
        Map.entry("Chưa cấu hình PAYOUT_ENCRYPTION_KEY; không thể nhận thông tin ngân hàng.",
                "Hệ thống chưa cấu hình khóa bảo mật để nhận thông tin chuyển khoản. Quản trị viên cần cấu hình PAYOUT_ENCRYPTION_KEY rồi khởi động lại ứng dụng."),
        Map.entry("PAYOUT_ENCRYPTION_KEY phải là Base64 của khóa ngẫu nhiên 32 byte.",
                "Khóa bảo mật nhận tiền PAYOUT_ENCRYPTION_KEY chưa đúng định dạng. Quản trị viên cần cấu hình khóa Base64 hợp lệ dài 32 byte rồi khởi động lại ứng dụng."),
        Map.entry("The selected Staff is already booked in this interval", "Nhân viên vừa có lịch trùng giờ. Vui lòng chọn khoảng khác."),
        Map.entry("A detail falls outside its referenced work schedule", "Tổng thời lượng dịch vụ vượt khoảng sẵn sàng. Hãy đổi giờ hoặc chọn khoảng dài hơn."),
        Map.entry("Every detail must use the selected Staff's schedule", "Khoảng sẵn sàng không thuộc nhân viên đã chọn."),
        Map.entry("Every detail must have between one and five valid evidences", "Mỗi dịch vụ cần từ 1 đến 5 ảnh bằng chứng trước khi hoàn thành."),
        Map.entry("Only a confirmed booking can be completed", "Chỉ có thể hoàn thành lịch đang được xác nhận."),
        Map.entry("Cần tải ít nhất một ảnh minh chứng trước khi xác nhận hoàn thành.", "Cần tải ít nhất một ảnh minh chứng trước khi xác nhận hoàn thành."),
        Map.entry("Nội dung sự cố phải có từ 1 đến 500 ký tự.", "Nội dung sự cố phải có từ 1 đến 500 ký tự."),
        Map.entry("Evidence must be a non-empty image no larger than 5 MB", "Chọn ảnh không rỗng, tối đa 5 MB."),
        Map.entry("Only JPEG and PNG evidence files are accepted", "Chỉ nhận ảnh JPEG hoặc PNG."),
        Map.entry("File extension does not match the image content", "Nội dung ảnh không khớp phần mở rộng của file."),
        Map.entry("Evidence is not a decodable image", "File không phải ảnh JPEG/PNG hợp lệ."),
        Map.entry("Evidence image could not be decoded", "Không thể đọc ảnh. Vui lòng chọn ảnh JPEG/PNG khác."),
        Map.entry("Only JPEG and PNG evidence content is accepted", "Nội dung file phải là ảnh JPEG/PNG."),
        Map.entry("A detail cannot have more than five evidences", "Dịch vụ này đã đủ 5 ảnh bằng chứng."),
        Map.entry("Evidence can only be uploaded for a confirmed booking", "Chỉ tải ảnh cho lịch đang được xác nhận."),
        Map.entry("Only an unpaid payment with a selected method can be confirmed", "Khách phải chọn phương thức và thanh toán phải còn chưa trả."),
        Map.entry("Receipt must be verified before confirming payment", "Hãy xác minh thực tế đã nhận tiền trước khi đánh dấu đã trả."),
        Map.entry("A method can only be selected for an unpaid completed booking", "Chỉ chọn phương thức cho lịch hoàn thành và chưa thanh toán.")
    );
    public static String business(RuntimeException failure) {
        String message = failure.getMessage();
        if (message != null && (message.startsWith("Chỉ ") || message.startsWith("Lịch ") || message.startsWith("Bạn ")
                || message.startsWith("Khoảng ") || message.startsWith("Hãy ") || message.startsWith("Giờ ")
                || message.startsWith("Nhân viên ") || message.startsWith("Dịch vụ ") || message.startsWith("Cần ")
                || message.startsWith("Địa chỉ ") || message.startsWith("Khách đã báo ")
                || message.startsWith("Nhân viên đã lỡ ") || message.startsWith("Không thể xác nhận ")
                || message.startsWith("Hồ sơ này đã có khoản bồi thường"))) { return message; }
        return MESSAGES.getOrDefault(message == null ? "" : message, "Không thể thực hiện thao tác. Vui lòng kiểm tra dữ liệu hoặc tải lại trang.");
    }
}
